package com.gcq.picture.controller;


import com.gcq.picture.common.BaseResponse;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.common.ResultUtils;
import com.gcq.picture.exception.ThrowUtils;
import com.gcq.picture.model.dto.space.analyze.*;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.vo.analyze.*;
import com.gcq.picture.service.SpaceAnalyzeService;
import com.gcq.picture.service.UserService;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;


    /**
     * 空间使用情况
     * @param spaceUsageAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse>  analyzeSpaceUsage(@RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest
            , HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse =
                spaceAnalyzeService.analyzeSpaceUsage(spaceUsageAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyzeResponse);
    }

    /**
     *空间标签分析
     * @param spaceTagsAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/tags")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> analyzeSpaceTags(@RequestBody SpaceTagsAnalyzeRequest spaceTagsAnalyzeRequest
            , HttpServletRequest request) {
        ThrowUtils.throwIf(spaceTagsAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceTagAnalyzeResponse> spaceTagAnalyzeResponses =
                spaceAnalyzeService.analyzeSpaceTagsList(spaceTagsAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceTagAnalyzeResponses);
    }


    /**
     * 空间分类分析
     * @param spaceCategoryAnalyzeRequest
     * @param request
     * @return
     */
   @PostMapping("/category")
   public BaseResponse<List<SpaceCategoryAnalyzeResponse>> analyzeSpaceCategory(@RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest,
                                                                                HttpServletRequest request) {
       ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
       User loginUser = userService.getLoginUser(request);
      List<SpaceCategoryAnalyzeResponse> spaceCategoryAnalyzeResponses =
                      spaceAnalyzeService.analyzeSpaceCategoryList(spaceCategoryAnalyzeRequest, loginUser);
      return ResultUtils.success(spaceCategoryAnalyzeResponses);
   }

    /**
     * 空间大小分析
     * @param spaceSizeAnalyzeRequest
     * @param request
     * @return
     */
   @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> analyzeSpaceSize(@RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest,
                                        HttpServletRequest request){
      ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
       User loginUser = userService.getLoginUser(request);
       List<SpaceSizeAnalyzeResponse> spaceSizeAnalyzeResponses =
               spaceAnalyzeService.analyzeSpaceSizeList(spaceSizeAnalyzeRequest, loginUser);
       return ResultUtils.success(spaceSizeAnalyzeResponses);
   }


    /**
     * 空间用户分析
     * @param spaceUserAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> analyzeSpaceUser(@RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest,
                                        HttpServletRequest request){
       ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
       User loginUser = userService.getLoginUser(request);
       List<SpaceUserAnalyzeResponse> spaceUserAnalyzeResponses =
               spaceAnalyzeService.analyzeSpaceUserList(spaceUserAnalyzeRequest, loginUser);
       return ResultUtils.success(spaceUserAnalyzeResponses);
    }

    /**
     *  空间排名分析(前 10)
     * @param spaceRankAnalyzeRequest
     * @param request
     * @return
     */
    @PostMapping("/rank")
    public BaseResponse<List<Space>> analyzeSpaceRank(@RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest,
                                                      HttpServletRequest request){
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<Space> spaceRankList =
                spaceAnalyzeService.getSpaceRankAnalyzeList(spaceRankAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceRankList);
    }

}
