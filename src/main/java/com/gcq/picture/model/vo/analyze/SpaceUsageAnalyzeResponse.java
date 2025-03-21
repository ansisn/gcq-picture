package com.gcq.picture.model.vo.analyze;

import com.gcq.picture.model.dto.space.analyze.SpaceAnalyzeRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Data
public class SpaceUsageAnalyzeResponse  implements Serializable {

    /**
     * 已使用大小
     */
    private Long usedSize;

    /**
     * 总大小
     */
    private Long maxSize;

    /**
     * 空间使用比例
     */
    private Double sizeUsageRatio;

    /**
     * 当前图片数量
     */
    private Long usedCount;

    /**
     * 最大图片数量
     */
    private Long maxCount;

    /**
     * 图片数量占比
     */
    private Double countUsageRatio;

    private static final long serialVersionUID = 1L;
}
