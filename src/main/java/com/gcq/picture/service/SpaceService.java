package com.gcq.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gcq.picture.model.dto.picture.PictureQueryRequest;
import com.gcq.picture.model.dto.space.SpaceAddRequest;
import com.gcq.picture.model.dto.space.SpaceQueryRequest;
import com.gcq.picture.model.entity.Picture;
import com.gcq.picture.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.vo.PictureVO;
import com.gcq.picture.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author guochuqu
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-03-01 08:39:14
*/
public interface SpaceService extends IService<Space> {



    Boolean addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     *
     * @param spaceQueryRequest
     * @return
     */
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);


    /**
     * 获取图片信息
     * @param space
     * @param request
     * @return
     */
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     *
     * @param spacePage
     * @param request
     * @return
     */
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 校验图片
     * @param space
     */
    public void validSpace(Space space, boolean add);

    void fillReviewParams(Space space, User loginUser);
}
