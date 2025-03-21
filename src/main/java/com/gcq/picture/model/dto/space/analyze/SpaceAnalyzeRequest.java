package com.gcq.picture.model.dto.space.analyze;


import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceAnalyzeRequest implements Serializable {

    private static final long serialVersionUID = -4546464799819842541L;
    private Long spaceId;

    /**
     * 是否查询公开空间
     */
    private boolean queryPublic;


    /**
     * 全空间分析
     */
    private boolean queryAll;


}
