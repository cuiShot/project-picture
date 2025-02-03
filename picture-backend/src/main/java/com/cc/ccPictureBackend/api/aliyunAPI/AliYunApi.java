package com.cc.ccPictureBackend.api.aliyunAPI;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.cc.ccPictureBackend.api.aliyunAPI.model.CreateOutPaintingTaskRequest;
import com.cc.ccPictureBackend.api.aliyunAPI.model.CreateOutPaintingTaskResponse;
import com.cc.ccPictureBackend.api.aliyunAPI.model.GetOutPaintingTaskResponse;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @Author cuicui
 * @Description
 */

@Component
@Slf4j
public class AliYunApi {
    // 读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 创建任务地址
    private static final String CREAT_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";
    // 查询任务状态 %s 是任务 id 具体任务具体填充
    private static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";


    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"扩图参数为空");
        }
        HttpRequest httpRequest = HttpRequest.post(CREAT_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION,"Bearer " + apiKey)
                .header("X-DashScope-Async","enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .body(JSONUtil.toJsonStr(request));
        try(HttpResponse httpResponse = httpRequest.execute();){
            if(!httpResponse.isOk()){
                log.error("请求异常：{}",httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI扩图失败");
            }
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            String errorCode = response.getCode();
            if(StrUtil.isNotBlank(errorCode)){
                String errorMessage = response.getMessage();
                log.error("AI 扩图失败,errorMessage{}",errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI接口响应失败");
            }

            return response;
        }
    }



    /**
     * 查询创建的任务结果
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 ID 不能为空");
        }
        // 处理响应
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }




}
