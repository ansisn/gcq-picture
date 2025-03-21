package com.gcq.picture.service;

import com.gcq.picture.model.dto.space.analyze.*;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.vo.analyze.*;

import java.util.List;

public interface SpaceAnalyzeService {


    /**
     * 空间使用情况分析
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageAnalyzeResponse analyzeSpaceUsage(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);


    /**
     * 空间分类分析
     * @param categoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceCategoryAnalyzeResponse> analyzeSpaceCategoryList(SpaceCategoryAnalyzeRequest categoryAnalyzeRequest, User loginUser);


    /**
     * 空间标签分析
     * @param tagsAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceTagAnalyzeResponse> analyzeSpaceTagsList(SpaceTagsAnalyzeRequest tagsAnalyzeRequest, User loginUser);


    /**
     * 空间容量分析
     * @param sizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceSizeAnalyzeResponse> analyzeSpaceSizeList(SpaceSizeAnalyzeRequest sizeAnalyzeRequest, User loginUser);


    /**
     * 空间用户时间分析
     * @param userAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceUserAnalyzeResponse> analyzeSpaceUserList(SpaceUserAnalyzeRequest userAnalyzeRequest, User loginUser);



    List<Space> getSpaceRankAnalyzeList(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);


}
