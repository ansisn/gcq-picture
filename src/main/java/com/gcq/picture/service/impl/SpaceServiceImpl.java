package com.gcq.picture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.exception.BusinessException;
import com.gcq.picture.exception.ThrowUtils;
import com.gcq.picture.model.dto.space.SpaceAddRequest;
import com.gcq.picture.model.dto.space.SpaceQueryRequest;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.SpaceUser;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.enums.SpaceLevelEnum;
import com.gcq.picture.model.enums.SpaceRoleEnum;
import com.gcq.picture.model.enums.SpaceTypeEnum;
import com.gcq.picture.model.vo.SpaceVO;
import com.gcq.picture.model.vo.UserVO;
import com.gcq.picture.service.SpaceService;
import com.gcq.picture.mapper.SpaceMapper;
import com.gcq.picture.service.SpaceUserService;
import com.gcq.picture.service.UserService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* @author guochuqu
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-03-01 08:39:14
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private SpaceUserService spaceUserService;

    @Override
    public Boolean addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        //1. 填充参数默认值
        Space space  = new Space();
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            spaceAddRequest.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            spaceAddRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (spaceAddRequest.getSpaceType() == null) {
            spaceAddRequest.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        BeanUtil.copyProperties(spaceAddRequest,space);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        validSpace(space,true);
        //2. 校验参数
        this.fillReviewParams(space,loginUser);
        //3. 校验权限，非管理员只能创建普通级别的空间
        boolean admin = userService.isAdmin(loginUser);
        ThrowUtils.throwIf(!admin && space.getSpaceLevel() != SpaceLevelEnum.COMMON.getValue()
                ,ErrorCode.NO_AUTH_ERROR,"普通用户无法创建内容扩展");
        //4. 控制同一个用户只能创建一个私有空间（本地锁 + 事务，后续改造）
        String lockName = String.valueOf(userId).intern();
        RLock lock = redissonClient.getLock(lockName);

        try {
            lock.lock();
            if (lock.tryLock(0,-1, TimeUnit.SECONDS)){
                Boolean execute = transactionTemplate.execute(status -> {
                    boolean exists = this.lambdaQuery().
                            eq(Space::getUserId, userId)
                            .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                            .exists();
                    ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "用户只能创建一个私有空间");
                    boolean save = this.save(space);
                    ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "创建失败");
                    //如果是团队空间，将创建人加入该空间
                    if (spaceAddRequest.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                        SpaceUser spaceUser = new SpaceUser();
                        spaceUser.setSpaceId(space.getId());
                        spaceUser.setUserId(userId);
                        spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                        boolean result = spaceUserService.save(spaceUser);
                        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队空间失败");
                    }

                      //判断事务是否回滚
                     //throw new RuntimeException();
                    return true;
                });
                return execute;
            }
        } catch (TransactionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
     return true;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest != null) {
            return spaceQueryWrapper;
        }
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        spaceQueryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        spaceQueryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("asc"), sortField);
        return spaceQueryWrapper;
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        Long userId = space.getUserId();
        if (spaceVO != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return  spaceVO;
    }

    /**
     * 转换查询空间页面类
     * @param spacePage
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)){
            return spaceVOPage;
        }
        //将对象类转换成封装对象类
        List<SpaceVO> spaceVOList = spaceList.stream().map(
                space -> getSpaceVO(space, request))
                .collect(Collectors.toList());
        // 1, 关联查询信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        //2, 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if(userMap.containsKey(userId)){
                user = userMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);

        return spaceVOPage;
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Integer spaceType = space.getSpaceType();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (spaceType != null && (spaceType != 0 || spaceType != 1)) {
                ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "空间类型错误");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }

    }

    /**
     * 创新或更新空间时，需要根据空间级别自动填充限额数据，可以在服务编写方法便于复用
     * @param space
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Space space, User loginUser) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        ThrowUtils.throwIf(spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "空间级别不存在");
        if (space.getMaxSize() == null) {
            space.setMaxSize(spaceLevelEnum.getMaxSize());
        }
        if (space.getMaxCount() == null) {
            space.setMaxCount(spaceLevelEnum.getMaxCount());
        }
        if (space.getSpaceType() == null) {
            space.setSpaceType(spaceTypeEnum.getValue());
        }
    }
}




