package com.cc.ccPictureBackend.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.mapper.SpaceUserMapper;
import com.cc.ccPictureBackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.cc.ccPictureBackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.SpaceUser;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.SpaceRoleEnum;
import com.cc.ccPictureBackend.model.vo.SpaceUserVO;
import com.cc.ccPictureBackend.model.vo.SpaceVO;
import com.cc.ccPictureBackend.model.vo.UserVO;
import com.cc.ccPictureBackend.service.SpaceService;
import com.cc.ccPictureBackend.service.SpaceUserService;
import com.cc.ccPictureBackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author cuicui
 * @Description
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserService {


    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private SpaceService spaceService;

    public SpaceUserServiceImpl(UserService userService) {
        this.userService = userService;
    }

    /**
     * 添加空间成员
     * @param spaceUserAddRequest
     * @return spaceUser 的 id
     */
    @Override
    public Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest){
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser,true);
        boolean saved = this.save(spaceUser);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

    /**
     * 校验空间成员对象，add用来区分创建时校验还是编辑时检验
     * @param spaceUser
     * @param add
     */
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        // 创建时，空间id和用户id必填
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if(add){
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR,"用户不存在");
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if(spaceRole == null ||spaceRoleEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间角色不存在");
        }
    }


    /**
     * 查询请求封装成查询对象
     * @param spaceUserQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<SpaceUser> getQueryWapper(SpaceUserQueryRequest spaceUserQueryRequest){
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        QueryWrapper<SpaceUser> spaceUserQueryWrapper = new QueryWrapper<>();

        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();

        spaceUserQueryWrapper.eq(ObjectUtil.isNotEmpty(id),"id", id);
        spaceUserQueryWrapper.eq(ObjectUtil.isNotEmpty(spaceId),"spaceId", spaceId);
        spaceUserQueryWrapper.eq(ObjectUtil.isNotEmpty(userId),"userId", userId);
        spaceUserQueryWrapper.eq(ObjectUtil.isNotEmpty(spaceRole),"spaceRole", spaceRole);

        return spaceUserQueryWrapper;
    }

    /**
     * 获取空间成员封装类，关联查询用户和空间的信息
     * @param spaceUser
     * @param request
     * @return
     */
    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request){
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        // 关联用户信息
        Long userId = spaceUser.getUserId();
        if(userId != null || userId > 0){
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceUserVO.setUser(userVO);
        }
        // 关联空间信息
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space,request);
            spaceUserVO.setSpace(spaceVO); // 假设 SpaceUserVO 有一个 setSpace 方法
        }

        return spaceUserVO;
    }


    /**
     * 查询封装类列表
     * @param spaceUserList
     * @return
     */
    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList){
        // 判断条件
        if (spaceUserList == null || spaceUserList.isEmpty()){
            return Collections.emptyList();
        }
        // 对象列表 =>封装对象列表
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream().map(SpaceUserVO::objToVo).collect(Collectors.toList());
        // 关联用户，空间

        // 1.收集需要关联的用户 id 和空间 id
        Set<Long> userIdSet = spaceUserList.stream()
                .map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream()
                .map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        // 2.批量查询用户和空间
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet)
                .stream().collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet)
                .stream().collect(Collectors.groupingBy(Space::getId));
        // 3.填充 SpaceUserVO 的用户和空间信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            // 填充用户信息
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceUserVO.setUser(userService.getUserVO(user));
            // 填充空间信息
            Space space = null;
            if (spaceIdSpaceListMap.containsKey(spaceId)) {
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });

        return spaceUserVOList;
    }

}
