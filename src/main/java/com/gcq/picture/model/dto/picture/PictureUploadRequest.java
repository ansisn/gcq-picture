package com.gcq.picture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {


    /**
     * 图片id
     */
    private Long id;

    private String fileUrl;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 名称前缀
     */
    private String namePrefix;

    /**
     * 图片名称
     */
    private String picName;

    private static final long serialVersionUID = 2926224194597436005L;
}
