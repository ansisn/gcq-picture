package com.gcq.picture.model.dto.space;


import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceEditRequest  implements Serializable {


    private static final long serialVersionUID = 5405643654368456257L;
    /**
     * id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;



}
