package com.gcq.picture.service.impl;


import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.exception.BusinessException;
import com.gcq.picture.exception.ThrowUtils;
import com.gcq.picture.model.dto.space.analyze.*;
import com.gcq.picture.model.entity.Picture;
import com.gcq.picture.model.vo.analyze.*;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.service.PictureService;
import com.gcq.picture.service.SpaceAnalyzeService;
import com.gcq.picture.service.SpaceService;
import com.gcq.picture.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {


    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    //根据参数增加查询语句
    public void addQueryCondition(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper){
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        if (queryAll){
            return;
        }
        if (queryPublic){
            queryWrapper.isNull("spaceId");
            return;
        }
        if (spaceId != null){
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数错误");
    }



    /**
     * 分析私有空间
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    public void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser){
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        if (queryPublic || queryAll){
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "非管理员，不能查询公共空间");
        }else if (spaceId != null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NO_AUTH_ERROR, "空间不存在");
            log.info("spaceId:{}", space.getUserId());
            log.info("userId{}",loginUser.getId());
            ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "非空间拥有者，不能查询该空间");
        }
    }


    @Override
    public SpaceUsageAnalyzeResponse

    analyzeSpaceUsage(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,User loginUser) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.NOT_FOUND_ERROR,"请求参数不能为空");
        //公共空间
        if (spaceUsageAnalyzeRequest.isQueryPublic() || spaceUsageAnalyzeRequest.isQueryAll()){
            //仅管理员可以访问
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            //统计公共图库的使用情况
            QueryWrapper<Picture> queryWrapper = new QueryWrapper();
            queryWrapper.select("picSize");
            // 管理员可以查询所有空间情况（包括个人空间）
            addQueryCondition(spaceUsageAnalyzeRequest,queryWrapper);
            List<Object> pictureList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            long usePicSize = pictureList.stream().mapToLong(result -> result instanceof Long ? (Long) result : 0).sum();
            long useCount = pictureList.size();
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usePicSize);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setUsedCount(useCount);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        }else {
            //个人空间
            Space space = spaceService.getById(spaceUsageAnalyzeRequest.getSpaceId());
            ThrowUtils.throwIf(space == null, ErrorCode.NO_AUTH_ERROR, "空间不存在");
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeResponse.setSizeUsageRatio(space.getTotalSize() * 1.0 / space.getMaxSize());
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            spaceUsageAnalyzeResponse.setCountUsageRatio(space.getTotalCount() * 1.0 / space.getMaxCount());
            return spaceUsageAnalyzeResponse;
        }
    }

    @Override
    public List<SpaceCategoryAnalyzeResponse> analyzeSpaceCategoryList(SpaceCategoryAnalyzeRequest categoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(categoryAnalyzeRequest == null,ErrorCode.NOT_FOUND_ERROR,"请求参数不能为空");
        checkSpaceAnalyzeAuth(categoryAnalyzeRequest, loginUser);
            QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
            //判断是否查询全部或者公共
            addQueryCondition(categoryAnalyzeRequest, pictureQueryWrapper);
            //select category,COUNT(picture.category),SUM(picScale) as totalSize from gcq_picture.picture where category IS NOT NULL GROUP BY category;
            pictureQueryWrapper.select("category",
                            "COUNT(picture.category) as count",
                            "SUM(picScale) as totalSize"
                    )
                .isNotNull("category")
                .groupBy("category");


        return pictureService.getBaseMapper().selectMaps(pictureQueryWrapper)
                .stream().map(result -> {
                   String category = result.get("category") == null ? "" : result.get("category").toString();
                   Long count =   ((Number) result.get("count")).longValue();
                   Long totalSize = ((Number) result.get("totalSize")).longValue();
                   return new SpaceCategoryAnalyzeResponse(category,count,totalSize);
                }).collect(Collectors.toList());

    }

    @Override
    public List<SpaceTagAnalyzeResponse> analyzeSpaceTagsList(SpaceTagsAnalyzeRequest tagsAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(tagsAnalyzeRequest == null,ErrorCode.NOT_FOUND_ERROR,"请求参数不能为空");
        checkSpaceAnalyzeAuth(tagsAnalyzeRequest, loginUser);
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        //判断是否查询全部或者公共
        addQueryCondition(tagsAnalyzeRequest, pictureQueryWrapper);
        pictureQueryWrapper.select("tags")
                .isNotNull("tags");
        List<String> pictureList = pictureService.getBaseMapper().selectMaps(pictureQueryWrapper)
                .stream()
                .map(result -> result.get("tags") == null ? "" : result.get("tags").toString())
                // 这样会让
                // .filter(ObjUtil::isNotNull)
                //.map(Objects::toString)
                .collect(Collectors.toList());
           //两个 JSON 字符串 ["a","b"] 和 ["c","d"] 解析成列表，然后合并为一个包含 "a", "b", "c", "d" 的流，最终收集为一个列表
        log.info("tags:{}",pictureList);



        Map<String, Long> map = pictureList.stream()
                .flatMap(s -> JSONUtil.toList(s, String.class).stream())
                // 下面这句是错误的，是把两个字符串 "a,b" 和 "c,d" 按逗号分割，生成 ["a", "b"] 和 ["c", "d"]
                // 两个数组，然后合并为一个包含 "a", "b", "c", "d" 的流，最终收集为一个列表。
                //  .flatMap(s -> Arrays.stream(s.split(","))).distinct()
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

       //降序排序
        return map.entrySet().stream()
                .sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue())
                ).collect(Collectors.toList());
    }

    @Override
    public List<SpaceSizeAnalyzeResponse> analyzeSpaceSizeList(SpaceSizeAnalyzeRequest sizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(sizeAnalyzeRequest == null,ErrorCode.NOT_FOUND_ERROR,"请求参数不能为空");
        checkSpaceAnalyzeAuth(sizeAnalyzeRequest, loginUser);
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();

        //判断是否查询全部或者公共
        addQueryCondition(sizeAnalyzeRequest, pictureQueryWrapper);
        pictureQueryWrapper.select("picSize");
        List<Long> picSize = pictureService.getBaseMapper()
                .selectObjs(pictureQueryWrapper)
                .stream().map(sice -> ((Number) sice).longValue())
                .collect(Collectors.toList());

         Map<String,Long> sizeRange = new LinkedHashMap<>();
         sizeRange.put("0-100kB",picSize.stream().filter(size -> size <= 100 * 1024).count());
         sizeRange.put("100kB-1MB",picSize.stream().filter(size -> size > 100 * 1024 && size <= 1024 * 1024).count());
         sizeRange.put("1MB-10MB",picSize.stream().filter(size -> size > 1024 * 1024 && size <= 10 * 1024 * 1024).count());
        sizeRange.put(">10MB",picSize.stream().filter(size -> size > 10 * 1024 * 1024).count());

        return sizeRange.entrySet().stream().collect(Collectors.toList())
                .stream().map(entry ->
                 new SpaceSizeAnalyzeResponse(entry.getKey(),entry.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SpaceUserAnalyzeResponse> analyzeSpaceUserList(SpaceUserAnalyzeRequest userAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(userAnalyzeRequest == null,ErrorCode.NOT_FOUND_ERROR,"请求参数不能为空");
        checkSpaceAnalyzeAuth(userAnalyzeRequest, loginUser);
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        //判断是否查询全部或者公共
        addQueryCondition(userAnalyzeRequest, pictureQueryWrapper);
        Long userId = userAnalyzeRequest.getUserId();
        pictureQueryWrapper.eq(ObjectUtil.isNotNull(userId),"userId",userId);
        String timeDimension = userAnalyzeRequest.getTimeDimension();
        switch (timeDimension){
            case "day":
                pictureQueryWrapper.select("DATE_FORMAT(createTime,'%Y-%m-%d') as period", "count(*) as count");
                pictureQueryWrapper.groupBy("period");
                break;
            case "month":
                pictureQueryWrapper.select("DATE_FORMAT(createTime,'%Y-%m') as period", "count(*) as count");
                pictureQueryWrapper.groupBy("period");
                break;
            case "year":
                pictureQueryWrapper.select("DATE_FORMAT(createTime,'%Y') as period", "count(*) as count");
                pictureQueryWrapper.groupBy("period");
                break;
            default:
                pictureQueryWrapper.select("count(*) as count");
                break;
        }
        List<Map<String, Object>> maps = pictureService.getBaseMapper().selectMaps(pictureQueryWrapper);

        return maps.stream().map(map -> new SpaceUserAnalyzeResponse
                        ((String) map.get("period"),
                                (Long) map.get("count")))
                .collect(Collectors.toList());
    }

    @Override
    public List<Space> getSpaceRankAnalyzeList(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

          // 仅管理员可查看空间排行
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");

        // 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT " + spaceRankAnalyzeRequest.getTopN()); // 取前 N 名

         // 查询结果
        return spaceService.list(queryWrapper);
    }


}
