package com.gcq.picture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gcq.picture.api.aliyunAi.client.DashScopeAPIClient;
import com.gcq.picture.api.aliyunAi.model.CreateOutPaintingTaskRequest;
import com.gcq.picture.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.exception.BusinessException;
import com.gcq.picture.exception.ThrowUtils;
import com.gcq.picture.manager.CosManager;
import com.gcq.picture.manager.picture.FilePictureUpload;
import com.gcq.picture.manager.picture.PictureUploadTemplate;
import com.gcq.picture.manager.picture.UrlPictureUpload;
import com.gcq.picture.model.dto.picture.*;
import com.gcq.picture.model.entity.Picture;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.enums.PictureReviewStatusEnum;
import com.gcq.picture.model.vo.PictureVO;
import com.gcq.picture.model.vo.UserVO;
import com.gcq.picture.service.PictureService;
import com.gcq.picture.mapper.PictureMapper;
import com.gcq.picture.service.SpaceService;
import com.gcq.picture.service.UserService;
import com.gcq.picture.utils.ColorSimilarUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* @author guochuqu
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-02-21 10:28:35
*/
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

//    @Resource
//    private FileManager fileManager;

    @Resource
    private CosManager cosManager;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private SpaceService spaceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private DashScopeAPIClient dashScopeAPIClient;


    @Resource
    private UserService userService;

    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();


    @Override
    public PictureVO uploadPicture(Object resource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(Objects.isNull(loginUser), ErrorCode.NO_AUTH_ERROR);

        // 用于判断是新增还是更新图片
        Long pictureId = null;
        if (Objects.nonNull(pictureUploadRequest) && Objects.nonNull(pictureUploadRequest.getId()) && pictureUploadRequest.getId() > 0) {
            pictureId = pictureUploadRequest.getId();
        }

        // 如果是更新图片，需要校验图片是否存在
        if (Objects.nonNull(pictureId)) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(Objects.isNull(oldPicture), ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(((User) loginUser).getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }

        // 上传图片，得到信息
        // 按照用户 id 划分目录
        // 按照用户 id 划分目录 => 按照空间划分目录
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null && spaceId > 0){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null,ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!space.getUserId().equals(loginUser.getId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            if (space.getTotalCount() >= space.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间已满");
            }
        }
        String uploadPathPrefix;
        if (spaceId != null && spaceId > 0) {
            //spaceId存在，则放在space
            uploadPathPrefix = String.format("space/%s", spaceId);
        } else {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (resource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult  uploadPictureResult = pictureUploadTemplate.uploadPicture(resource, uploadPathPrefix);
       // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }

        picture.setName(picName);
        picture.setUserId(loginUser.getId());
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setSpaceId(uploadPictureResult.getSpaceId());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setPicColor(uploadPictureResult.getPicColor());
        if (pictureId != null && pictureId > 0) {
            picture.setId(pictureId);
        }



        if(spaceId != null && spaceId > 0){
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, spaceId)
                    .setSql("totalCount = totalCount + 1")
                    .setSql("totalSize = totalSize +" + picture.getPicSize())
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "图片更新失败");
        }

            transactionTemplate.execute(status -> {
                boolean result = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
                return true;
            });

        //唯一索引查找Id
        if (pictureId == null || pictureId <= 0) {
            Wrapper<Picture> url = new QueryWrapper<Picture>().eq("url", picture.getUrl());
            Picture one = this.getOne(url);
            picture.setId(one.getId());
        }

        return PictureVO.objToVo(picture);
    }


    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);


        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }


    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }


    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    @Override
    public Picture fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
        return picture;
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        String picName = pictureUploadByBatchRequest.getPicName();
        if (namePrefix == null) {
            namePrefix = picName;
        }
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }

        return uploadCount;
    }


    @Override
    public Page<PictureVO> listPictureVOByPageCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        //构建缓存key
        String cacheKey = JSONUtil.toJsonStr(pictureQueryRequest);
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        //md5压缩
        String hashKey = DigestUtil.md5Hex(cacheKey);
        String redisKey = "gcq:listPictureVOByPageCache" + hashKey;
        String caffeineKey = "listPictureVOByPageCache" + hashKey;
        //多级缓存
        String caffeineValue = LOCAL_CACHE.getIfPresent(caffeineKey);
        if (StrUtil.isNotBlank(caffeineValue)){
            Page<PictureVO> pictureVOPageByCache = JSONUtil.toBean(caffeineValue, Page.class);
            return pictureVOPageByCache;
        }
        //从redis缓存中查询
        ValueOperations<String, String>  valueOperations = stringRedisTemplate.opsForValue();
        String cacheValue = valueOperations.get(redisKey);
        //如果查询出来，直接返回
        if (StrUtil.isNotBlank(cacheValue)){
            Page<PictureVO> pictureVOPageByCache = JSONUtil.toBean(cacheValue, Page.class);
            return pictureVOPageByCache;
        }

        //没有查询出来进行数据库查询
        Page<Picture> picturePage = this.page(new Page<>(current, size),
                this.getQueryWrapper(pictureQueryRequest));
        //获取封装类
        Page<PictureVO> pictureVOPage = this.getPictureVOPage(picturePage, request);
        //本地缓存
        LOCAL_CACHE.put(caffeineKey, JSONUtil.toJsonStr(pictureVOPage));
        //对redis进行缓存
        String jsonStr = JSONUtil.toJsonStr(pictureVOPage);
        //进行过期日期随机化，防止雪崩
        int cacheExpireTime = RandomUtil.randomInt(0, 200) + 300;
        valueOperations.set(redisKey, jsonStr, cacheExpireTime, TimeUnit.SECONDS);
        return pictureVOPage;
    }


    @Override
    public Boolean verifyPersonal(Picture picture, User loginUser) {
        Long spaceId = picture.getSpaceId();
        //如果是删除功能不需要判断老图片是否是个人图片
        Long userId = picture.getUserId();
        //如果是跟新操作，则需要判断老图片和新图片的spaceId是否一致
        if (spaceId != null && userId.equals(loginUser.getId())) {
            return true;
        }
        //公共图库
        if (spaceId == null) {
            return true;
        }

        return false;
    }

    @Override
    @Transactional
    public boolean deletePicture(long id, User loginUser) {
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //判断是否为个人图库
        if (!this.verifyPersonal(oldPicture,loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        Boolean execute = transactionTemplate.execute(status -> {// 操作数据库
            boolean result = this.removeById(id);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片删除失败");
            Long spaceID = oldPicture.getSpaceId();
            if (spaceID != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceID)
                        .setSql("totalCount = totalCount - 1")
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "图库删除失败");
            }
            return true;
        });

        cosManager.deleteObject(oldPicture.getUrl());
        cosManager.deleteObject(oldPicture.getThumbnailUrl());
        return  execute;
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        //老图
        Picture oldPicture = this.getById(id);

        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        this.fillReviewParams(picture, loginUser);
        //判断是否为个人图库
        if (!this.verifyPersonal(oldPicture,loginUser)){
           throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        //更新操作，如果新的id为空，使用老图片的Id todo 这里可能有问题
        if (picture.getSpaceId() == null){
            picture.setSpaceId(oldPicture.getSpaceId());
        }
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }



    //每晚定时处理多余的图片
//    @Override
//    @Scheduled(cron = "0 0 0 * * ?")
//    public void clearPictureFile(Picture oldPicture) {
//            // 判断该图片是否被多条记录使用
//            String pictureUrl = oldPicture.getUrl();
//            long count = this.lambdaQuery()
//                    .eq(Picture::getUrl, pictureUrl)
//                    .count();
//            // 有不止一条记录用到了该图片，不清理
//            if (count > 1) {
//                return;
//            }
//            // FIXME 注意，这里的 url 包含了域名，实际上只要传 key 值（存储路径）就够了
//            cosManager.deleteObject(oldPicture.getUrl());
//            // 清理缩略图
//            String thumbnailUrl = oldPicture.getThumbnailUrl();
//            if (StrUtil.isNotBlank(thumbnailUrl)) {
//                cosManager.deleteObject(thumbnailUrl);
//            }
//
//    }

    @Override
    public List<PictureVO> searchByColor(Long spaceId,String color, User loginUser) {
        //1，校验参数
        ThrowUtils.throwIf(color == null, ErrorCode.PARAMS_ERROR,"颜色不能为空");
        //2.查询该空间下所有图片（必修主色调）

        List<Picture> picturePage = new ArrayList<>();
        if (spaceId == null) {
            // 公开图库
            // 普通用户默认只能查看已过审的公开数据
            picturePage =  this.lambdaQuery()
                    .eq(Picture::getReviewStatus, PictureReviewStatusEnum.PASS.getValue())
                    .isNull(Picture::getSpaceId)
                    .isNotNull(Picture::getPicColor)
                    .list();
        } else {
            // 私有空间
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
           picturePage =  this.lambdaQuery()
                    .eq(Picture::getSpaceId, spaceId)
                    .isNotNull(Picture::getPicColor)
                    .list();
        }
        //3.如果没有图片，直接返回空列表
        if (picturePage.isEmpty()) {
            return new ArrayList<>();
        }
        //将目标颜色转换Color对象
        Color targetColor = Color.decode(color);
        //4.计算相似度并排序
        List<Picture> collect = picturePage.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String picColor = picture.getPicColor();
                    if (StrUtil.isBlank(picColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color picColorObj = Color.decode(picColor);
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, picColorObj);
                })).limit(12).collect(Collectors.toList());

        return collect.stream().map(PictureVO::objToVo).collect(Collectors.toList());
    }

    @Override
    public void editPictureByBath(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1.获取校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(pictureIdList == null || pictureIdList.isEmpty(), ErrorCode.PARAMS_ERROR, "图片id列表不能为空");
        //2.校验空间权限
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "个人空间没有权限");
        }
        //3.查询指定图片
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()){
            return;
        }
        //4.更新图片分类和标签
        pictureList.stream().forEach(picture -> {
            if (StrUtil.isNotBlank(category)){
                picture.setCategory(category);
            }
           if (CollUtil.isNotEmpty(tags)){
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        //5.操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "批量更新失败");
    }

    @Override
    public CreateOutPaintingTaskResponse updatePictureOutPainting(PictureAiExtendRequest pictureAiExtendRequest, User loginUser) {
        ThrowUtils.throwIf(pictureAiExtendRequest == null, ErrorCode.PARAMS_ERROR, "扩图参数错误");
        Long id = pictureAiExtendRequest.getId();
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = pictureAiExtendRequest.getCreateOutPaintingTaskRequest();
        ThrowUtils.throwIf(createOutPaintingTaskRequest == null, ErrorCode.PARAMS_ERROR, "扩图参数错误");
        //获取图片信息
        Picture picture = Optional.ofNullable(this.getById(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));

        judgeAiPicture(picture);

        //权限校验
        verifyPersonal(picture, loginUser);
        //构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        taskRequest.setInput(createOutPaintingTaskRequest.getInput());
        BeanUtils.copyProperties(createOutPaintingTaskRequest, taskRequest);
        //创建任务
        return dashScopeAPIClient.createTask(taskRequest);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    public void judgeAiPicture(Picture picture) {
        //校验图片格式和大小, 图像大小：不超过10MB。
        Integer picHeight = picture.getPicHeight();
        Integer picWidth = picture.getPicWidth();
        String picFormat = picture.getPicFormat();
        Double picScale = picture.getPicScale();
        Long picSize = picture.getPicSize();

        if (picHeight == null || picWidth == null || picScale == null || picFormat == null || picSize == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片信息不完整");
        }
       if (picSize == null || picSize > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过10M");
       }
        //  图像分辨率：不低于512×512像素且不超过4096×4096像素。
        //          图像单边长度范围：[512, 4096]，单位像素。
        if (picHeight < 512 || picWidth < 512 || picHeight > 4096 || picWidth > 4096) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片分辨率不在512×512像素且不超过4096×4096像素范围内");
        }
        //  图像格式：JPG、JPEG、PNG、HEIF、WEBP。
        if (!"jpg".equals(picFormat) && !"jpeg".equals(picFormat) && !"png".equals(picFormat) && !"heif".equals(picFormat) && !"webp".equals(picFormat)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片格式不正确");
        }

    }
}




