package com.cc.ccPictureBackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc.ccPictureBackend.annotation.AuthCheck;
import com.cc.ccPictureBackend.common.BaseResponse;
import com.cc.ccPictureBackend.common.DeleteRequest;
import com.cc.ccPictureBackend.common.ResultUtils;
import com.cc.ccPictureBackend.constant.UserConstant;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.model.dto.space.*;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.SpaceLevelEnum;
import com.cc.ccPictureBackend.model.enums.UserRoleEnum;
import com.cc.ccPictureBackend.model.vo.SpaceVO;
import com.cc.ccPictureBackend.service.SpaceService;
import com.cc.ccPictureBackend.service.UserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author cuicui
 * @Description
 */
@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {
    @Resource
    private SpaceService spaceService;
    @Autowired
    private UserService userService;

    /*
    更新空间
    删除空间
    新建空间
    编辑空间
     */

    /**
     * 枚举抓换成对象列表返回给前端
     * @return
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }


    /**
     * 更新空间，仅管理员可用，更新空间级别
     *
     * @param spaceUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = "UserConstant.ADMIN_ROLE")
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false);
        // 判读数据库中的老数据是否存在
        Long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "数据不存在");
        // 更新数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(result, ErrorCode.OPERATION_ERROR, "操作错误，更新失败");
        return ResultUtils.success(true);
    }


    /**
     * 创建空间
     *
     * @param spaceAddRequest
     * @return
     */
    @PostMapping("add")
    public BaseResponse<Long> createSpace(@RequestBody SpaceAddRequest spaceAddRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long spaceId = spaceService.addSpace(spaceAddRequest,loginUser);
        return ResultUtils.success(spaceId);

    }

    /**
     * 删除空间，管理员任意。空间创建人本人删除
     *
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() < 0,
                ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        Long spaceId = deleteRequest.getId();
        Space oldSpace = spaceService.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR,"数据不存在");

        if(!userService.isAdmin(loginUser) || !oldSpace.getUserId() .equals(loginUser.getId()) ){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean removed = spaceService.removeById(spaceId);

        // TODO 删除空间下的图片，可以异步调用

        ThrowUtils.throwIf(!removed,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取空间（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(space);
    }


    /**
     * 根据 id 获取空间（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVO(space, request));
    }

    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }


    /**
     *分页获取空间列表(封装类)
     * @param spaceQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,HttpServletRequest request){

        int current = spaceQueryRequest.getCurrent();
        int pageSize = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize >= 20, ErrorCode.PARAMS_ERROR);
        // 查数据库
        Page<Space> page = spaceService.page(new Page<>(current, pageSize), spaceService.getQueryWapper(spaceQueryRequest));
        // 获取封装类
        return ResultUtils.success(spaceService.getSapceVOPage(page,request));
    }

    /**
     * 编辑空间，允许空间创建人编辑部分信息，注意不能编辑空间的额级别
     *
     * @param spaceEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() < 0 ,ErrorCode.PARAMS_ERROR);

        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        spaceService.fillSpaceBySpaceLevel(space);
        spaceService.validSpace(space, false);
        space.setEditTime(new Date());
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_FOUND_ERROR);

        Space oldSpace = spaceService.getById(spaceEditRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        Long createrId = oldSpace.getUserId();
        if(!createrId.equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"非创建者本人不能修改空间信息");
        }

        boolean update = spaceService.updateById(space);
        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
}
