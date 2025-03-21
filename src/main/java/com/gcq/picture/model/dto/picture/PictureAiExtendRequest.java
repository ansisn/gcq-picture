package com.gcq.picture.model.dto.picture;

import com.gcq.picture.api.aliyunAi.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class PictureAiExtendRequest implements Serializable {
    /**
     * 图片id
     */
    private Long id;

    /**
     * 绘画任务请求
     */
    private CreateOutPaintingTaskRequest createOutPaintingTaskRequest;


    private static final long serialVersionUID = 4344814803313768017L;
}
