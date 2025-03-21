package com.gcq.picture.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.gcq.picture.manager.auth.model.SpaceUserPermission;
import com.gcq.picture.model.entity.SpaceUser;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class SpaceUserVO {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    /**
     * 当前空间下图片的总大小
     */
    private Long totalSize;

    /**
     * 当前空间下的图片数量
     */
    private Long totalCount;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     *  用户信息
     *
     */
    private UserVO user;

    /**
     *  空间信息
     */
    private SpaceVO space;

    /**
     * 权限列表
     */
    private List<String> spaceUserPermissions = new ArrayList<>();

    /**
     * 封装类转对象
     */
    public static SpaceUser voToObj(SpaceUserVO spaceUserVO) {
        if (spaceUserVO == null) {
            return null;
        }
        SpaceUser space = new SpaceUser();
        BeanUtils.copyProperties(spaceUserVO, space);
        // 类型不同，需要转换

        return space;
    }

    /**
     * 对象转封装类
     */
    public static SpaceUserVO objToVo(SpaceUser spaceUser) {
        if (spaceUser == null) {
            return null;
        }
        SpaceUserVO spaceVO = new SpaceUserVO();
        BeanUtils.copyProperties(spaceUser, spaceVO);
        // 类型不同，需要转换

        return spaceVO;
    }
}
