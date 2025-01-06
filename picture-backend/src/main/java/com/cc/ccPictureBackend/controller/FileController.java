package com.cc.ccPictureBackend.controller;

import com.cc.ccPictureBackend.annotation.AuthCheck;
import com.cc.ccPictureBackend.common.BaseResponse;
import com.cc.ccPictureBackend.common.ResultUtils;
import com.cc.ccPictureBackend.constant.UserConstant;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.manager.CosManager;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;

/**
 * @Author cuicui
 * @Description
 */

@Log4j2
@RestController
@RequestMapping("/file")
public class FileController {


    @Resource
    private CosManager cosManager;

    /**
     * 测试文件上传
     *
     * @param multipartFile
     * @return 文件路径
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filepath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            // 返回可访问地址
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }

    /**
     * 测试文件下载
     *
     * @param filepath 文件路径
     * @param response 响应对象
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(@RequestParam String filepath, HttpServletResponse response) throws IOException {

        // 传入 /test/欧阳耀莹02.jpg
        // filepath = "https://project-picture-1311982167.cos.ap-shanghai.myqcloud.com/test/欧阳耀莹02.jpg"
        System.out.println(filepath);


        COSObjectInputStream cosObjectInput = null;
        try {
            // 根据文件路径获取COS对象
            COSObject cosObject = cosManager.getObject(filepath); // TODO 出错
//            System.out.println("cosObject=" + cosObject);
            // COS对象转换成输入流
            cosObjectInput = cosObject.getObjectContent();
            if (cosObjectInput == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件不存在");
            }
            // 处理下载到的流  字节流
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应  将字节流写入到输出流中
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }

}
