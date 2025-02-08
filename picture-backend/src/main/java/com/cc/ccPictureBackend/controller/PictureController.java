package com.cc.ccPictureBackend.controller;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc.ccPictureBackend.annotation.AuthCheck;
import com.cc.ccPictureBackend.api.aliyunAPI.AliYunApi;
import com.cc.ccPictureBackend.api.aliyunAPI.model.CreateOutPaintingTaskResponse;
import com.cc.ccPictureBackend.api.aliyunAPI.model.GetOutPaintingTaskResponse;
import com.cc.ccPictureBackend.api.imageSearch.ImageSearchApiFacade;
import com.cc.ccPictureBackend.api.imageSearch.model.ImageSearchResult;
import com.cc.ccPictureBackend.common.BaseResponse;
import com.cc.ccPictureBackend.common.DeleteRequest;
import com.cc.ccPictureBackend.common.PageRequest;
import com.cc.ccPictureBackend.common.ResultUtils;
import com.cc.ccPictureBackend.constant.SpaceUserPermissionConstant;
import com.cc.ccPictureBackend.constant.UserConstant;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.manager.auth.SpaceUserAuthManager;
import com.cc.ccPictureBackend.manager.auth.StpKit;
import com.cc.ccPictureBackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.cc.ccPictureBackend.model.dto.picture.*;
import com.cc.ccPictureBackend.model.dto.reviewPicture.PictureReviewRequest;
import com.cc.ccPictureBackend.model.entity.Picture;
import com.cc.ccPictureBackend.model.entity.PictureTagCategory;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.PictureReviewStatusEnmu;
import com.cc.ccPictureBackend.model.enums.UserRoleEnum;
import com.cc.ccPictureBackend.model.vo.PictureVO;
import com.cc.ccPictureBackend.service.PictureService;
import com.cc.ccPictureBackend.service.SpaceService;
import com.cc.ccPictureBackend.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.servlet.ServletRequest;
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
    @Autowired
    private ServletRequest httpServletRequest;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SpaceService spaceService;
    @Autowired
    private AliYunApi aliYunApi;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 上传图片
     *
     * @param multipartFile        图片文件
     * @param pictureUploadRequest 上传文件的时候，pictureUploadRequest = null ，更新文件的时候不为空
     * @param request              从 HttpServletRequest 获取登录信息
     * @return PictureVO
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile,
                                                 PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     * @param deleteRequest
     * @return boolean
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long pictureId = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(pictureId, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 更新图片
     *
     * @param pictureUpdateRequest 封装的更新图片的类
     * @param request
     * @return TODO 设置了审核参数，但是没有更新数据库啊
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    @PostMapping("/update")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断数据库对象是否存在
        Long oldPictureId = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(oldPictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(oldPicture, loginUser);
        // 新对象
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        pictureService.validPicture(picture);
        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        // 更新数据库
        boolean b = pictureService.updateById(oldPicture);
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
//        // 空间权限校验
//        Long spaceId = picture.getSpaceId();
//        if (spaceId != null) {
//            User loginUser = userService.getLoginUser(request);
//            pictureService.checkPictureAuth(loginUser, picture);
//        }
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
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            // 已经改为使用注解鉴权
            // User loginUser = userService.getLoginUser(request);
            // pictureService.checkPictureAuth(loginUser, picture);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 获取权限列表
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        // 获取封装类
//        return ResultUtils.success(pictureService.getPictureVO(picture, request));
        return ResultUtils.success(pictureVO);
    }


    /**
     * 分页获取 图片 列表（视图类）
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

        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        // 公开图库
        if (spaceId == null) {
            // 普通用户默认只能查看已过审的公开数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            // 已经改为使用注解鉴权
//            // 私有空间
//            User loginUser = userService.getLoginUser(request);
//            Space space = spaceService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
        }

        //查询数据库
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
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null || pictureEditRequest.getId() < 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_FOUND_ERROR);
        pictureService.editPicture(pictureEditRequest, loginUser);
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

    /**
     * 图片审核请求
     *
     * @param pictureReviewRequest 图片审核请求封装类
     * @param request
     * @return
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 通过 url 上传图片的方法
     *
     * @param pictureUploadRequest
     * @param request
     * @return
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByURL(@RequestBody PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadRequest == null, ErrorCode.PARAMS_ERROR, "请求为空");
        String fileUrl = pictureUploadRequest.getFileUrl();
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 批量抓取 bing 中的图片的方法
     *
     * @param pictureUploadByBatchRequest
     * @param request
     * @return count 返回抓取到的图片总数
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }


    /**
     * 构造本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();


    /**
     * 使用 多级缓存
     * Caffeine 和 Redis 的listPictureVOByPage 的方法
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户只能查看过审的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
        // 构建缓存 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "listPictureVOByPage:" + hashKey;

        // 1.从 Caffeine 中查，命中就返回，
        String cacheValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cacheValue != null) {
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        // 2.Caffeine 不命中,查 从Redis 中查
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cacheValue = valueOps.get(cacheKey);
        if (cacheValue != null) {
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }

        // 3. redis 也缓存不命中,查数据库,
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 设置 Redis 缓存 10 分钟之内的过期时间,防止雪崩.
        int cacheExpireTIme = 300 + RandomUtil.randomInt(0, 300);
        // 把结果 放到 caffeine 和 Redis
        LOCAL_CACHE.put(cacheKey, JSONUtil.toJsonStr(pictureVOPage));
        valueOps.set(cacheKey, JSONUtil.toJsonStr(pictureVOPage), cacheExpireTIme);
        // 返回结果
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 以图搜图接口
     * @param searchPictureByPictureRequest 请求封装
     * @return
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest){
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> imageList = ImageSearchApiFacade.searchImage(oldPicture.getUrl());
        return ResultUtils.success(imageList);
    }


    /**
     * 按照颜色搜索图片
     * @param searchPictureByColorRequest
     * @param request
     * @return
     */
    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 图片批处理方法
     * 批量更新图片的额分类和标签
     * 业务层实现了两个方法，使用线程池分批异步进行优化
     * 现在调用的是优化前的方法
     * @param pictureEditByBatchRequest
     * @param request
     * @return
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 创建 AI 扩图任务
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }


    /**
     * 前端轮询查询，后端不主动查
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }


}
