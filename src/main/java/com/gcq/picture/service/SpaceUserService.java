package com.gcq.picture.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import com.gcq.picture.model.dto.spaceuser.SpaceUserAddRequest;
import com.gcq.picture.model.dto.spaceuser.SpaceUserQueryRequest;
import com.gcq.picture.model.entity.SpaceUser;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author guochuqu
* @description 针对表【spaceUser_user(空间用户关联)】的数据库操作Service
* @createDate 2025-03-18 22:07:14
*/
public interface SpaceUserService extends IService<SpaceUser> {

    Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest, User loginUser);

    /**
     *
     * @param spaceUserQueryRequest
     * @return
     */
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);


    /**
     * 获取图片信息
     * @param spaceUser
     * @param request
     * @return
     */
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     *
     * @param spaceUserPage
     * @param request
     * @return
     */
    public Page<SpaceUserVO> getSpaceUserVOPage(Page<SpaceUser> spaceUserPage, HttpServletRequest request);

    /**
     * 校验图片
     * @param spaceUser
     */
    public void validSpaceUser(SpaceUser spaceUser, boolean add);


    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
