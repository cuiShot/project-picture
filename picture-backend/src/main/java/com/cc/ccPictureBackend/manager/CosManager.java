package com.cc.ccPictureBackend.manager;

import com.cc.ccPictureBackend.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import org.springframework.stereotype.Component;
import java.io.File;

import javax.annotation.Resource;

/**
 * @Author cuicui
 * @Description 一些操作 COS 的方法
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    // ... 一些操作 COS 的方法

    /**
     * 上传对象
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,file);
        return cosClient.putObject(putObjectRequest);
    }

}

