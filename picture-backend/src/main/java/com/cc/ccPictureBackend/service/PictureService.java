package com.cc.ccPictureBackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cc.ccPictureBackend.model.dto.picture.PictureQueryRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureUploadByBatchRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureUploadRequest;
import com.cc.ccPictureBackend.model.dto.reviewPicture.PictureReviewRequest;
import com.cc.ccPictureBackend.model.entity.Picture;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.PictureReviewStatusEnmu;
import com.cc.ccPictureBackend.model.vo.PictureVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * @Author cuicui
 * @Description
 */


public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);


    /**
     * 图片封装的方法，关联用户信息
     *
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);


    /**
     * 分页获取图片VO封装
     *
     * @param picturePage Picture 封装
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 图片数据校验方法，用于更新和修改图片的时候进行判断
     * 校验 id、url、introduction 是否符合要求
     *
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 通用的补充审核参数的方法，根据用户的角色给对象填充审核字段的值
     *
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);


    /**
     * 上传文件 方法
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );


}
