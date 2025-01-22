package com.cc.ccPictureBackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cc.ccPictureBackend.annotation.AuthCheck;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.manager.CosManager;
import com.cc.ccPictureBackend.manager.FileManager;
import com.cc.ccPictureBackend.manager.upload.FilePictureUpload;
import com.cc.ccPictureBackend.manager.upload.PictureUploadTemplate;
import com.cc.ccPictureBackend.manager.upload.UrlPictureUpload;
import com.cc.ccPictureBackend.mapper.PictureMapper;
import com.cc.ccPictureBackend.model.dto.file.UploadPictureResult;
import com.cc.ccPictureBackend.model.dto.picture.PictureEditRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureQueryRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureUploadByBatchRequest;
import com.cc.ccPictureBackend.model.dto.picture.PictureUploadRequest;
import com.cc.ccPictureBackend.model.dto.reviewPicture.PictureReviewRequest;
import com.cc.ccPictureBackend.model.entity.Picture;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.PictureReviewStatusEnmu;
import com.cc.ccPictureBackend.model.vo.PictureVO;
import com.cc.ccPictureBackend.model.vo.UserVO;
import com.cc.ccPictureBackend.service.PictureService;
import com.cc.ccPictureBackend.service.SpaceService;
import com.cc.ccPictureBackend.service.UserService;
import com.qcloud.cos.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Author cuicui
 * @Description
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService {

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Autowired
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;


    /**
     * 上传图片，返回 Picture 视图
     * <p>
     * Space 加入后，新增 图片上传空间的逻辑
     *
     * @param inputSource          更改实现逻辑，实现两种方式的图片上传
     * @param pictureUploadRequest
     * @param loginUser
     * @return 返回 Picture 视图
     */

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 请求校验
        ThrowUtils.throwIf(inputSource == null, ErrorCode.PARAMS_ERROR, "图片为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 新增图片空间的校验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "图片所属空间不存在");
            // 管理员（现在的系统设计就是空间创建人）本人校验
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有此空间上传权限");
            }

            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        Long pictureId = null;
        String fileUrl = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }

        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            // 新增空间逻辑
            // 校验空间是否一致,没传 spaceId 用 oldPicture的,传了的话用新的
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else { // 传了，但不一致需要处理。一致的话就不用管
                ThrowUtils.throwIf(spaceId != oldPicture.getSpaceId(), ErrorCode.PARAMS_ERROR, "空间Id不一致");
            }
        }


        // 1.2 是上传图片
        // 根据用户 id 划分文件上传目录 ==> 如果图片有空间 spaceId 用spaceId 作为目录
        String uploadPathPrefix = String.format("/public/%s", loginUser.getId());
        if (spaceId != null) {
            uploadPathPrefix = String.format("/space/%s", loginUser.getId());
        }


        // 新实现，判断上传类型，调用通用的 upload 方法
        // 根据 inputSource 区分上传类型
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        // 得到上传结果
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 构造要入库的图片信息 批量抓取传入的名字
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);

        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        picture.setSpaceId(spaceId);// 空间信息


        // 补充审核参数
        fillReviewParams(picture, loginUser);
        //操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 保存,更新数据库，更新两个表，Picture 表 和 Space表
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        return PictureVO.objToVo(picture);
    }


    /**
     * 从查询请求中获取查询条件
     *
     * @param pictureQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return pictureQueryWrapper;
        }

        // 获取查询请求对象的各个属性值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();

        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        pictureQueryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);


        // 多字段搜索 searchText 同时搜索名称，简介等.既要在 name 中搜索，又要在 introduction 中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // qw 是传递给 and 方法的 Lambda 参数，代表当前的查询构造器。
            pictureQueryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }

        // 将获得的查询条件设置在 queryMapper中
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        pictureQueryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        pictureQueryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        pictureQueryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        pictureQueryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        pictureQueryWrapper.isNull(nullSpaceId, "spaceId");


        // 标签查询:JSON 数组查询.前端传过来的标签是数组,数据库中存储的是 JSON 格式
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                pictureQueryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        // 排序
        pictureQueryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return pictureQueryWrapper;
    }

    /**
     * 图片封装,关联创建用户信息
     *
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片VO封装
     *
     * @param picturePage Picture 封装
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        // 原始分页信息复制到 新分页
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        // 原始分页为空，返回空分页
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // pictureList 封装到 pictureVOList,使用stream流
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // pictureVOList 中的 pictureVO对象 关联用户信息
        // 这里不对 每个 Picture 中的userId进行获取，而是直接获取全部图片的 userId 得到userIdSet
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1、2 => user2
        // 实际 每个id对应就一个对象,但是方法groupingBy只能返回 list
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 给 pictureVOList 中的每一个 pictureVO 对象设置 user
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));

        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 图片数据校验方法，用于更新和修改图片的时候进行判断
     * 校验 id、url、introduction 是否符合要求
     *
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核业务
     * 管理员审核图片，前端的的按钮 意思就是 通过 或者 拒绝 ，所以request封装的审核状态请求就是 pass 或者 reject
     *
     * @param pictureReviewRequest 图片审核请求 封装类
     * @param loginUser            当前登录 user
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long pictureId = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus(); // 其实就是 pass 或 reject
        String reviewMessage = pictureReviewRequest.getReviewMessage();

        // 获取当前审核状态
        PictureReviewStatusEnmu currentReviewStatus = PictureReviewStatusEnmu.getEnum(reviewStatus);
        //
        if (pictureId == null || currentReviewStatus == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (currentReviewStatus.equals(PictureReviewStatusEnmu.REVIEWING)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断图片是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 不要重复审核，重复设置 reviewStatus 状态,提醒
        ThrowUtils.throwIf(oldPicture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR, "请勿重复审核");

        //更新审核状态
        Picture newPicture = new Picture();
        BeanUtils.copyProperties(oldPicture, newPicture);
        newPicture.setReviewerId(loginUser.getId());
        newPicture.setReviewStatus(reviewStatus); // 发送过来的审核状态设置到对象
        newPicture.setReviewMessage(reviewMessage);
        boolean b = this.updateById(newPicture);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR, "系统错误，审核失败");
    }


    /**
     * 通用的补充审核参数的方法，根据用户的角色给对象填充审核字段的值
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnmu.REVIEWING.getValue());
        }
    }


    /**
     * 最主要的是从 网页中 得到 div 得到 img 组件，进而解析到 img 服务器的地址，
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText(); // 搜索文本
        Integer count = pictureUploadByBatchRequest.getCount(); // 搜索数量
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多添加三十条数据");
        // 要抓取的地址，先写死 到 bing 去搜索
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (Exception e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }

        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "获取元素失败");
        }

        Elements imgElementsList = div.select("img.mimg");
        int uploadCount = 0;
        // 获取批量抓取的图片名字
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 获取每一张图片
        for (Element imgElement : imgElementsList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isNotBlank(fileUrl)) {
                log.info("当前连接为空，已经跳过：{}", fileUrl);
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // 设置批量抓取的图片名字
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if (StrUtil.isNotBlank(namePrefix)) {
                // 设置图片名称，序号连续递增
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }

            pictureUploadRequest.setFileUrl(fileUrl);
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
            }
            if (uploadCount >= count) {
                break;
            }

        }
        return uploadCount;
    }


    /**
     * 删除 COS 中旧的对象的方法,异步调用
     * TODO 将该方法应用到 实际业务的接口中
     *
     * @param oldPicture
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }

        String urlString = oldPicture.getUrl();
        // 清理 webp 图
        cosManager.deleteObject(getKeyFromUrl(urlString));
        // 清理缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(getKeyFromUrl(thumbnailUrl));
        }
    }

    /**
     * 从 完整 URL 得到 key
     *
     * @param totalUrl
     * @return
     */
    public String getKeyFromUrl(String totalUrl) {
        URL url = null;
        try {
            url = new URL(totalUrl);
            return url.getPath();
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片url不正确");
        }
    }


    /**
     * 编辑图片方法封装
     *
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 删除图片方法
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);

        // 加入事务,更新两个表了
        transactionTemplate.execute(status -> {
            // 操作Picture 表
            boolean removed = this.removeById(pictureId);
            ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR);
            // Space 表，释放资源
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId,spaceId)
                        .setSql("totalSize = totalSize - " +oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1").update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR);
            }
            return true;
        });


        // 异步清理 COS 上的文件
        this.clearPictureFile(oldPicture);
    }


    /**
     * 空间校验封装方法
     * 私有空间 or 公共空间
     *
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }


}
