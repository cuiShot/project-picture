package com.cc.ccPictureBackend.controller;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc.ccPictureBackend.annotation.AuthCheck;
import com.cc.ccPictureBackend.common.BaseResponse;
import com.cc.ccPictureBackend.common.DeleteRequest;
import com.cc.ccPictureBackend.common.PageRequest;
import com.cc.ccPictureBackend.common.ResultUtils;
import com.cc.ccPictureBackend.constant.UserConstant;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.model.dto.picture.PictureEditRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureQueryRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureUpdateRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureUploadRequest;
import com.cc.ccPictureBackend.model.entity.Picture;
import com.cc.ccPictureBackend.model.entity.PictureTagCategory;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.vo.PictureVO;
import com.cc.ccPictureBackend.service.PictureService;
import com.cc.ccPictureBackend.service.UserService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @Author cuicui
 * @Description
 */
@Log4j2
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;


    /**
     * 上传图片
     *
     * @param multipartFile        图片文件
     * @param pictureUploadRequest 上传文件的时候，pictureUploadRequest = null ，更新文件的时候不为空
     * @param request              从 HttpServletRequest 获取登录信息
     * @return PictureVO
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     * 仅本人 和管理员可以删除图片
     *
     * @param deleteRequest
     * @return boolean
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断图片是否存在
        Picture picture = pictureService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 判断是不是管理员或者本人
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        Long userId = picture.getUserId();// 上传图片的 userid
        if (!isAdmin || !userId.equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 删除
        boolean result = pictureService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 更新图片
     *
     * @param pictureUpdateRequest 封装的更新图片的类
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断数据库对象是否存在
        Long oldPicture = pictureUpdateRequest.getId();
        Picture pictureById = pictureService.getById(oldPicture);
        ThrowUtils.throwIf(pictureById == null, ErrorCode.NOT_FOUND_ERROR);
        // 新对象
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        pictureService.validPicture(picture);

        // 更新数据库
        boolean b = pictureService.updateById(picture);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取Picture (原始类)
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id < 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取Picture (封装类)
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(Long id, HttpServletRequest request) {
        BaseResponse<Picture> pictureByIdBaseResponse = getPictureById(id, request);
        Picture pictureById = pictureByIdBaseResponse.getData();
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(pictureById, pictureVO);
        return ResultUtils.success(pictureVO);

    }

    /**
     * 分页获取 图片 列表（封装类）
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {

        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        //
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }


    /**
     * 分页获取 图片 列表,原始类，管理员权限
     *
     * @param pictureQueryRequest
     * @return
     */

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {

        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        //
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 编辑图片，给用户使用
     *
     * @param pictureEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null || pictureEditRequest.getId() < 0, ErrorCode.PARAMS_ERROR);
        //DTO信息复制到 entity
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // tags 格式转换
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 图片格式校验
        pictureService.validPicture(picture);
        // 设置编辑时间
        picture.setEditTime(new Date());

        User loginUser = userService.getLoginUser(request);
        // 判断 要编辑的图片在数据库中是否存在
        Picture oldPicture = pictureService.getById(picture.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 管理员 和 本人 可以进行图片编辑
        if(!userService.isAdmin(loginUser) || !oldPicture.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean b = pictureService.updateById(picture);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

}
