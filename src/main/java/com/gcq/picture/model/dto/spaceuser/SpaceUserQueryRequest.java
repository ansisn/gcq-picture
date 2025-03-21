package com.gcq.picture.model.dto.spaceuser;

import com.gcq.picture.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUserQueryRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 4788614725182827733L;

    private Long id;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;


}
