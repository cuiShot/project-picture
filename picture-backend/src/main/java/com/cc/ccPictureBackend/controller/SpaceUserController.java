package com.cc.ccPictureBackend.controller;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cc.ccPictureBackend.common.BaseResponse;
import com.cc.ccPictureBackend.common.DeleteRequest;
import com.cc.ccPictureBackend.common.ResultUtils;
import com.cc.ccPictureBackend.constant.SpaceUserPermissionConstant;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.cc.ccPictureBackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.cc.ccPictureBackend.model.dto.spaceuser.SpaceUserEditRequest;
import com.cc.ccPictureBackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.cc.ccPictureBackend.model.entity.SpaceUser;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.vo.SpaceUserVO;
import com.cc.ccPictureBackend.service.SpaceUserService;
import com.cc.ccPictureBackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Author cuicui
 * @Description
 */
@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;


    /**
     * 添加成员
     * @param spaceUserAddRequest
     * @return
     */
    @PostMapping("/add")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = spaceUserService.addSpaceUser(spaceUserAddRequest);
        return ResultUtils.success(id);
    }

    /**
     * 删除
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request){
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Long id = deleteRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);

        boolean b = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(b);
    }


    /**
     * 查询某个成员在某个空间的信息
     * @param spaceUserQueryRequest
     * @return
     */
    @PostMapping("/get")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest){
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(ObjUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);

        QueryWrapper<SpaceUser> queryWapper = spaceUserService.getQueryWapper(spaceUserQueryRequest);
        SpaceUser spaceUser = spaceUserService.getOne(queryWapper);
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);

    }


//    /**
//     * 查询成员信息列表
//     * @param spaceUserQueryRequest
//     * @param request
//     * @return
//     */
//    @PostMapping("/list")
//    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
//    public BaseResponse<List<SpaceUserVO>> getSpaceUserVOList(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest
//    , HttpServletRequest request){
//        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
//        QueryWrapper<SpaceUser> queryWapper = spaceUserService.getQueryWapper(spaceUserQueryRequest);
//        List<SpaceUser> spaceUserList = spaceUserService.list(queryWapper);
//        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
//    }


    /**
     * 编辑成员信息（设置权限）
     * @param spaceUserEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest
    ,HttpServletRequest request){
        ThrowUtils.throwIf(spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        // 校验数据
        spaceUserService.validSpaceUser(spaceUser,false);
        // 判断老数据是否存在
        Long id = spaceUserEditRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);

    }


    /**
     * 查询成员信息列表
     */
    @PostMapping("/list")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUserVO(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

    /**
     * 查询我加入的团队空间列表
     * @param request
     * @return
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());
        QueryWrapper<SpaceUser> queryWapper = spaceUserService.getQueryWapper(spaceUserQueryRequest);
        List<SpaceUser> spaceUserList = spaceUserService.list(queryWapper);
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

}
