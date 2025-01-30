package com.cc.ccPictureBackend.api.imageSearch.sub;

import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author cuicui
 * @Description 通过向百度发送post请求，获取给定图片URL的相似图片页面地址
 *  得到的是 百度搜图的结果 的地址
 */
@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取相似图片 的页面地址
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        // 1.准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn","pc");
        formData.put("form","pc");
        formData.put("image_source","PC_UPLOAD_URL");
        // 获取当前时间戳
        long upTime = System.currentTimeMillis();
        // 请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + upTime;

        try{
            // 2.发送 post 请求到百度接口
            HttpResponse response = HttpRequest.post(url).form(formData).execute();
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"调用接口失败");
            }
            // 解析相应
            String body = response.body();
            Map<String,Object> result = JSONUtil.toBean(body, Map.class);

            // 3.处理响应结果}
            if(result == null || !Integer.valueOf(0).equals(result.get("status"))){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"返回结果无效");
            }
            Map<String,Object> data = (Map<String,Object>)result.get("data");
            String ralUrl = (String)data.get("url");
            // 从 url 中获得searchResultUrl
            String searchResultUrl = URLUtil.decode(ralUrl, StandardCharsets.UTF_8);
            if(searchResultUrl == null){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"未返回有效结果");
            }
            return searchResultUrl;
        } catch (Exception e) {
            log.error("搜索失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"搜索失败");
        }

    }


    // 测试上面的方法
    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        String searchResultUrl = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + searchResultUrl);
    }
}
