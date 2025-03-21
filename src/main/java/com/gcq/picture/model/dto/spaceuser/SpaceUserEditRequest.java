package com.gcq.picture.model.dto.spaceuser;


import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceUserEditRequest implements Serializable {

    private static final long serialVersionUID = -6856262718359662495L;
    /**
     * 空间用户id
     */
    private Long id;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;



}
