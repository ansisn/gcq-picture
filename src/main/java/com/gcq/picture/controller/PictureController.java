package com.gcq.picture.controller;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gcq.picture.annotation.AuthCheck;
import com.gcq.picture.annotation.SaSpaceCheckPermission;
import com.gcq.picture.api.aliyunAi.client.DashScopeAPIClient;
import com.gcq.picture.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.gcq.picture.api.aliyunAi.model.GetOutPaintingTaskResponse;
import com.gcq.picture.api.imagesearch.facadepattern.ImageSearchApiFacade;
import com.gcq.picture.api.imagesearch.model.ImageSearchResult;
import com.gcq.picture.common.BaseResponse;
import com.gcq.picture.common.DeleteRequest;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.common.ResultUtils;
import com.gcq.picture.constant.UserConstant;
import com.gcq.picture.exception.BusinessException;
import com.gcq.picture.exception.ThrowUtils;
import com.gcq.picture.manager.auth.SpaceUserManager;
import com.gcq.picture.manager.auth.constant.SpaceUserPermissionConstant;
import com.gcq.picture.model.dto.picture.*;
import com.gcq.picture.model.dto.picture.search.SearchPictureByColorRequest;
import com.gcq.picture.model.dto.picture.search.SearchPictureByPictureRequest;
import com.gcq.picture.model.entity.Picture;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.enums.PictureReviewStatusEnum;
import com.gcq.picture.model.vo.PictureVO;
import com.gcq.picture.service.PictureService;
import com.gcq.picture.service.SpaceService;
import com.gcq.picture.service.UserService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 图片接口
 */
@RestController
@RequestMapping("/picture")
@Slf4j
@Api("图片模块")
public class PictureController {
    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private DashScopeAPIClient dashScopeAPIClient;


    @Resource
    private SpaceUserManager spaceUserManager;



    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {

        User loginUser = userService.getLoginUser(request);
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUploadRequest, picture);
        pictureService.fillReviewParams(picture,loginUser);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        boolean result = pictureService.deletePicture(id, loginUser);

        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
       ThrowUtils.throwIf(pictureEditRequest == null || pictureEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR,"编辑参数出错");
        pictureService.editPicture(pictureEditRequest,request);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    @SaSpaceCheckPermission(SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture handlePicture = new Picture();
        handlePicture.setId(id);
        Picture pictureVerify = pictureService.getById(id);
        Space space = spaceService.getById(pictureVerify.getSpaceId());
        ThrowUtils.throwIf(pictureVerify == null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = new ArrayList<>();
        //判断是否为个人图库
        if (!pictureService.verifyPersonal(handlePicture, loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (pictureVerify.getSpaceId() != null && ObjectUtil.isNotEmpty(space)){
            //判断是否为个人图库
            Picture picture = pictureService.getOne(new QueryWrapper<Picture>()
                    .eq("id", handlePicture.getId()));
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR,"图片没审核，或不存在");
            // 获取封装类
           permissionList = spaceUserManager.getPermissionList(space, userService.getLoginUser(request));
            return ResultUtils.success(pictureService.getPictureVO(picture, request));
        }
        handlePicture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        Picture picture = pictureService.getOne(new QueryWrapper<Picture>().eq("reviewStatus", handlePicture.getReviewStatus())
                .eq("id", handlePicture.getId()));
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR,"图片没审核，或不存在");

        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setSpaceUserPermissions(permissionList);

        // 获取封装类
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        //判断是否为个人图库
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
       // 公开图库
        if (spaceId == null) {
            // 普通用户默认只能查看已过审的公开数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            // 私有空间
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }

        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取图片列表（封装类，带缓存） todo 当执行更新，删除操作需要删除缓存
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                  HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        Page<PictureVO> pagePictureVO = pictureService.listPictureVOByPageCache(pictureQueryRequest, request);

        return ResultUtils.success(pagePictureVO);
    }


    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("二次元", "风景", "人物", "动物", "植物", "建筑", "美食", "科技", "汽车", "其他");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 审核图片（给管理员使用）
     *
     * @param pictureReviewRequest
     * @param request
     * @return
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doReviewPicture(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null || pictureReviewRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "不存在该图片");
        User loginUser = userService.getLoginUser(request);
        boolean admin = userService.isAdmin(loginUser);
        if (!admin){
            ThrowUtils.throwIf(true, ErrorCode.NO_AUTH_ERROR, "没有权限");
        }
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量上传图片（给管理员使用）
     * @param pictureUploadByBatchRequest
     * @param request
     * @return
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    //以图搜图

    /**
     *
     * @param searchPictureByPictureRequest
     * @param request
     * @return
     */
    @PostMapping("/search/by/picture")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<ImageSearchResult>> searchByPicture(
            @RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(pictureId);
        List<ImageSearchResult> imageSearchResults = ImageSearchApiFacade.searchImage(picture.getThumbnailUrl());
        return ResultUtils.success(imageSearchResults);

    }


    /**
     * 根据颜色搜索图片
     * @param searchPictureByColorRequest
     * @param request
     * @return
     */
    @PostMapping("/search/by/color")
    public BaseResponse<List<PictureVO>> searchByColor(
            @RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
            HttpServletRequest request) {
         ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR,"参数错误");;
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchByColor(
                searchPictureByColorRequest.getSpaceId(),
                searchPictureByColorRequest.getPicColor(), loginUser);
        return ResultUtils.success(pictureVOList);
    }



    /**
     * 批量更新图片
     * @param pictureEditByBatchRequest
     * @param request
     * @return
     */
    @PostMapping("/update/by/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> updatePictureByBatch(
            @RequestBody PictureEditByBatchRequest pictureEditByBatchRequest,
            HttpServletRequest request){
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR,"参数错误");
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBath(pictureEditByBatchRequest,loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 根据AI扩展图片(异步)
     * @param pictureAiExtendRequest
     * @param request
     * @return
     */
    @PostMapping("/expand/by/ai")
    public BaseResponse<CreateOutPaintingTaskResponse> expandPictureByAi( @RequestBody PictureAiExtendRequest pictureAiExtendRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(pictureAiExtendRequest == null, ErrorCode.PARAMS_ERROR,"参数错误");
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse createOutPaintingTaskResponse = pictureService.
                updatePictureOutPainting(pictureAiExtendRequest, loginUser);

        return ResultUtils.success(createOutPaintingTaskResponse);

    }

    //获取ai扩图结果
    @GetMapping("/get/ai/extend/result")
    public BaseResponse<GetOutPaintingTaskResponse> getAiExtendResult(String taskId, HttpServletRequest request) {
        ThrowUtils.throwIf(StringUtils.isBlank(taskId), ErrorCode.PARAMS_ERROR,"参数错误");
        GetOutPaintingTaskResponse taskResult = dashScopeAPIClient.getTaskResult(taskId);
        return ResultUtils.success(taskResult);
    }

}
