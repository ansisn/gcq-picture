package com.gcq.picture.api.aliyunAi.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gcq.picture.api.aliyunAi.model.CreateOutPaintingTaskRequest;
import com.gcq.picture.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.gcq.picture.api.aliyunAi.model.GetOutPaintingTaskResponse;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DashScopeAPIClient {
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/";

    @Value("${aliYunAi.apiKey}")
    private String API_KEY;

    /**
     * 创建任务
     *
     * @param createOutPaintingTaskRequest 扩图任务请求对象
     * @return 创建任务的响应对象
     */
    public CreateOutPaintingTaskResponse createTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        // 校验请求参数是否为空
        ThrowUtils.throwIf(createOutPaintingTaskRequest == null, ErrorCode.PARAMS_ERROR, "扩图参数不能为空");
        String requestUrl = API_URL + "services/aigc/image2image/out-painting";
        log.info("开始创建任务，请求 URL: {}", requestUrl);
        try {
            HttpRequest request = HttpRequest.post(requestUrl);
            if (JSONUtil.toJsonStr(createOutPaintingTaskRequest) != null) {
                request.body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
            }
            HttpResponse response = request
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("X-DashScope-Async", "enable")
                    .header("Content-Type", "application/json")
                    .execute();
            // 解析响应
            CreateOutPaintingTaskResponse bean = JSONUtil.toBean(response.body(), CreateOutPaintingTaskResponse.class);
            log.info("任务创建成功，任务 ID: {}", bean.getRequestId());
            return bean;
        } catch (Exception e) {
            log.error("创建任务失败，请求 URL: {}", requestUrl, e);
            throw new RuntimeException("创建任务失败", e);
        }
    }

    /**
     * 根据任务 ID 查询结果
     *
     * @param taskId 任务 ID
     * @return 查询任务结果的响应对象
     */
    public GetOutPaintingTaskResponse getTaskResult(String taskId) {
        // 校验任务 ID 是否为空
        ThrowUtils.throwIf(taskId == null || taskId.isEmpty(), ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        String requestUrl = API_URL + "tasks/" + taskId;
        log.info("开始查询任务结果，请求 URL: {}", requestUrl);
        try {
            HttpRequest request = HttpRequest.get(requestUrl);
            HttpResponse response = request
                    .header("Authorization", "Bearer " + API_KEY)
                    .execute();
            // 解析响应
            GetOutPaintingTaskResponse bean = JSONUtil.toBean(response.body(), GetOutPaintingTaskResponse.class);
            log.info("任务结果查询成功，任务 ID: {}", taskId);
            return bean;
        } catch (Exception e) {
            log.error("查询任务结果失败，请求 URL: {}", requestUrl, e);
            throw new RuntimeException("查询任务结果失败", e);
        }
    }
}