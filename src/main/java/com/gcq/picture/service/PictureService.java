package com.gcq.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gcq.picture.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.gcq.picture.model.dto.picture.*;
import com.gcq.picture.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author guochuqu
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-02-21 10:28:35
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param resource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object resource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     *
     * @param pictureQueryRequest
     * @return
     */
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);


    /**
     * 获取图片信息
     * @param picture
     * @param request
     * @return
     */
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     *
     * @param picturePage
     * @param request
     * @return
     */
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 校验图片
     * @param picture
     */
    public void validPicture(Picture picture);


    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);


    /**
     * 图片审核
     * @param picture
     * @param loginUser
     */
    public Picture fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

    /**
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    Page<PictureVO> listPictureVOByPageCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request);


    /**
     * 判断是否为公共图库，如果是个人图库判断是否有权限
     * todo sa-token 可以替代该功能
     * @param picture
     * @param loginUser
     * @return
     */
    Boolean verifyPersonal(Picture picture,User loginUser);

    boolean deletePicture(long id, User loginUser);

    void editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request);


    /**
     * 根据颜色查询图片
     * @param spaceId
     * @param color
     * @param loginUser
     * @return
     */
     List<PictureVO> searchByColor(Long spaceId, String color, User loginUser);


    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
     public void editPictureByBath(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);


    /**
     * ai 扩图
     * @param pictureAiExtendRequest
     * @param loginUser
     * @return
     */
     public CreateOutPaintingTaskResponse updatePictureOutPainting(PictureAiExtendRequest pictureAiExtendRequest, User loginUser);
}
