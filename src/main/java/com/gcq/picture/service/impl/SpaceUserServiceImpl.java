package com.gcq.picture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.exception.BusinessException;
import com.gcq.picture.exception.ThrowUtils;
import com.gcq.picture.model.dto.spaceuser.SpaceUserAddRequest;
import com.gcq.picture.model.dto.spaceuser.SpaceUserQueryRequest;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.SpaceUser;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.enums.SpaceRoleEnum;
import com.gcq.picture.model.vo.SpaceUserVO;
import com.gcq.picture.model.vo.SpaceVO;
import com.gcq.picture.model.vo.UserVO;
import com.gcq.picture.service.SpaceService;
import com.gcq.picture.service.UserService;
import com.gcq.picture.service.SpaceUserService;
import com.gcq.picture.mapper.SpaceUserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author guochuqu
* @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
* @createDate 2025-03-18 22:07:14
*/
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
    implements SpaceUserService{

    @Resource
    @Lazy
    private SpaceService spaceService;

    @Resource
    private UserService userService;


    @Override
    public Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        // 数据库操作
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }



    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        SpaceUserVO spaceUserVO  = SpaceUserVO.objToVo(spaceUser);
        User loginUser = userService.getLoginUser(request);
        if (loginUser.getId() != null) {
            spaceUserVO.setUserId(loginUser.getId());
            UserVO userVO = userService.getUserVO(loginUser);
            spaceUserVO.setUser(userVO);
        }
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
            spaceUserVO.setSpace(spaceVO);
        }

        return spaceUserVO;
    }

    @Override
    public Page<SpaceUserVO> getSpaceUserVOPage(Page<SpaceUser> spaceUserPage, HttpServletRequest request) {
        List<SpaceUser> spaceUserList = spaceUserPage.getRecords();
        Page<SpaceUserVO> spaceUserVOPage = new Page<>(spaceUserPage.getCurrent(), spaceUserPage.getSize(), spaceUserPage.getTotal());
        //判断输入列表是否为空
         if (CollUtil.isEmpty(spaceUserList)){
             return  spaceUserVOPage;
         }
          //对象 => 封装对象vo
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream().map(spaceUser -> getSpaceUserVO(spaceUser, request))
                .collect(Collectors.toList());
        //1. 收集查询的用户的id和空间id
        Set<Long> userIdSet = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdset = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());

        //2.批量查询用户和空间
        Map<Long, List<User>>  userIdUserListMap = userService.listByIds(userIdSet)
                .stream().collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceIdUserListMap = spaceService.listByIds(spaceIdset).stream().collect(Collectors.groupingBy(Space::getId));
        //3.填充spaceUserVo的用户信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            User user = null;
            if(userIdUserListMap.containsKey(userId)){
               user = userIdUserListMap.get(userId).get(0);
            }

            Space space = null;
            if (spaceIdUserListMap.containsKey(spaceId)){
               space = spaceIdUserListMap.get(spaceId).get(0);
            }
            SpaceVO spaceVO = SpaceVO.objToVo(space);
            spaceUserVO.setSpace(spaceVO);
            spaceUserVO.setUser(userService.getUserVO(user));
        });
        spaceUserVOPage.setRecords(spaceUserVOList);
        return spaceUserVOPage;
    }

    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        // 创建时，空间 id 和用户 id 必填
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if (spaceRole != null && spaceRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 判断输入列表是否为空
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        // 对象列表 => 封装对象列表
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream().map(SpaceUserVO::objToVo).collect(Collectors.toList());
        // 1. 收集需要关联查询的用户 ID 和空间 ID
        Set<Long> userIdSet = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        // 2. 批量查询用户和空间
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));
        // 3. 填充 SpaceUserVO 的用户和空间信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            // 填充用户信息
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceUserVO.setUser(userService.getUserVO(user));
            // 填充空间信息
            Space space = null;
            if (spaceIdSpaceListMap.containsKey(spaceId)) {
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });
        return spaceUserVOList;
    }


}




