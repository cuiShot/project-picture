package com.cc.ccPictureBackend.manager;

import cn.hutool.core.io.FileUtil;
import com.cc.ccPictureBackend.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

/**
 * @Author cuicui
 * @Description 一些操作 COS 的方法 纯通用，与业务毫无关系
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
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 文件 key,不包含域名等信息的 key
     */
    public void deleteObject(String key) throws CosClientException {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }


    /**
     * 下载对象
     * @param key 唯一键 位置
     * @return
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传对象（附带图片信息）
     * 使用 数据万象
     * @param key  唯一键
     * @param file 文件
     */
//    public PutObjectResult putPictureObject(String key, File file) {
//        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
//                file);
//        // 对图片进行处理（获取基本信息也被视作为一种处理）
//        PicOperations picOperations = new PicOperations();
//        // 1 表示返回原图信息
//        picOperations.setIsPicInfo(1);
//        // 构造处理参数
//        putObjectRequest.setPicOperations(picOperations);
//        return cosClient.putObject(putObjectRequest);
//    }


    /**
     * 上传对象，带有图片格式转换功能，格式转换为 webp
     * 上传对象（附带图片信息）
     * 使用 数据万象
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();

        // 图片压缩（转成 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setFileId(webpKey);
        rules.add(compressRule);

        // 缩略图处理 新加规则，只对图片体积大小 大于 20 KB的图片进行缩略处理
        if(file.length() > 2*1024){
            PicOperations.Rule thumbnailUrlRule = new PicOperations.Rule();
            thumbnailUrlRule.setBucket(cosClientConfig.getBucket());
            String thumbnailUrl = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailUrlRule.setFileId(thumbnailUrl);
            // 缩放规则, 宽高都是最小缩放到 128 px
            thumbnailUrlRule.setRule(String.format(("imageMogr2/thumbnail/%sx%s>"),256,256));
            rules.add(thumbnailUrlRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

}

