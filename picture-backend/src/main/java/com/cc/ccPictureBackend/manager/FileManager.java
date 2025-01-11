package com.cc.ccPictureBackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
import com.cc.ccPictureBackend.config.CosClientConfig;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @Author cuicui
 * @Description 文件操作的通用方法 ，跟业务有点关系
 * @Deprecated 已经废弃，改为使用 upload 包下面的模板方法优化
 */
@Deprecated
@Service
@Slf4j
public class FileManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 业务场景下的上传图片
     * @param multipartFile 文件
     * @param uploadPathPrefix 文件上传路径前缀
     * @return 上传文件结果
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile ,String uploadPathPrefix) {
        // 1.校验图片
        this.validPicture(multipartFile);
        // 2.图片上传地址,做处理 UUID
        String uuid = RandomUtil.randomString(16);
        String OriginalFileName = multipartFile.getOriginalFilename();
            //新生成的文件名是：日期 + uuid + .后缀
        String uploadFileName = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()), uuid,FileUtil.getSuffix(OriginalFileName));
            // 生成上传路径
        String uploadFilePath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        // 3.解析结果并返回
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadFilePath, null);
            multipartFile.transferTo(file);
            // 上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadFilePath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            int high = imageInfo.getHeight(); // 高
            int width = imageInfo.getWidth(); // 宽
            double picScale = NumberUtil.round(width * 1.0 / high,2).doubleValue();// 宽高比
            // 设置 uploadPictureResult 属性
            uploadPictureResult.setPicHeight(high);
            uploadPictureResult.setPicWidth(width);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            uploadPictureResult.setPicName(FileUtil.mainName(uploadFileName));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setUrl(cosClientConfig.getHost()+ "/" + uploadFilePath);

            // 返回 UploadPictureResult 对象
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("上传图片失败 " , e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempFile(file);
        }
        // 4.清理临时文件

    }

    /**
     * 删除临时文件
     * @param file 临时文件
     */
    private void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        boolean delete = file.delete();
        if (!delete) {
            log.error("file delete fail,filePath = {}" ,file.getAbsolutePath());
        }
    }

    /**
     * 检验图片的方法，文件不合格就抛出异常
     * @param multipartFile
     */
    private void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR,"文件不能为空");
        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        final Long ONE_MAX = 2*2048*1024L; // 单个文件大小限制 4MB
        ThrowUtils.throwIf(fileSize > ONE_MAX,ErrorCode.PARAMS_ERROR,"图片大小不能超过4MB");
        // 2.检查文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FILE_SUFFEX = Arrays.asList("png","jpg","jpeg","gif");// 文件允许后缀
        ThrowUtils.throwIf(!ALLOW_FILE_SUFFEX.contains(fileSuffix),ErrorCode.PARAMS_ERROR,"文件类型不支持");
    }

    /**
     * 根据图片的 Url 上传图片，
     * TODO 两个方法有重复代码，可以抽出来一个新方法
     * @param fileUrl
     * @param uploadPathPrefix
     * @return
     */
    public UploadPictureResult uploadPictureByUrl(String fileUrl,String uploadPathPrefix) {
        // 1.图片上传地址,做处理 UUID
        String uuid = RandomUtil.randomString(16);
        String OriginalFileName = FileUtil.mainName(fileUrl);
        //新生成的文件名是：日期 + uuid + .后缀
        String uploadFileName = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()), uuid,FileUtil.getSuffix(OriginalFileName));
        // 生成上传路径
        String uploadFilePath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        // 3.解析结果并返回
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadFilePath, null);
            // 下载 fileUrl 路径的文件 ,下载到临时文件中
            HttpUtil.downloadFile(fileUrl, file);
            // 上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadFilePath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            int high = imageInfo.getHeight(); // 高
            int width = imageInfo.getWidth(); // 宽
            double picScale = NumberUtil.round(width * 1.0 / high, 2).doubleValue();// 宽高比
            // 设置 uploadPictureResult 属性
            uploadPictureResult.setPicHeight(high);
            uploadPictureResult.setPicWidth(width);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            uploadPictureResult.setPicName(FileUtil.mainName(uploadFileName));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadFilePath);

            // 返回 UploadPictureResult 对象
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("上传图片到对象存储失败 " , e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempFile(file);
        }
    }

    /**
     *  验证 url 格式以及根据 url 得到的图片信息
     * @param url
     */
    private void validPictureByUrl(String url){
        ThrowUtils.throwIf(ObjUtil.isEmpty(url),ErrorCode.PARAMS_ERROR,"文件地址不正确");
        // 1.验证url格式
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件地址格式不正确");
        }

        // 2.校验 协议
        ThrowUtils.throwIf(!(url.startsWith("http://")) && !(url.startsWith("https://"))
                ,ErrorCode.PARAMS_ERROR,"仅支持使用 HTTP 或 HTTPS 协议的文件地址");

        // 3.发送 HEAD 请求验证文件是否存在
        HttpResponse httpResponse = null; // try 一下，在 finally 中关闭
        try{
            httpResponse = HttpUtil.createRequest(Method.HEAD,url).execute();
            // 3.1 HTTP请求未正常返回，return
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK){
                return;
            }

            // 3.2 校验文件类型
            String contentType = httpResponse.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String>  ALLOW_FILE_TYPE = Arrays.asList("image/jpeg","image/png","image/gif");
                ThrowUtils.throwIf(!ALLOW_FILE_TYPE.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR,"不允许的文件类型");

            }

            // 3.3 校验文件大小
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try{
                    Long contentLength = Long.parseLong(contentLengthStr);
                    final long FOUR_MB = 4 * 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > FOUR_MB,
                            ErrorCode.PARAMS_ERROR,"文件大小不能超过4MB");
                }catch (NumberFormatException e){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件大小格式错误");
                }
            }
        }finally {
            if (httpResponse != null){
                httpResponse.close();
            }
        }
    }

}
