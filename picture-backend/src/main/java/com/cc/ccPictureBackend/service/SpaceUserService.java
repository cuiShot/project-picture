package com.cc.ccPictureBackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cc.ccPictureBackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.cc.ccPictureBackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.cc.ccPictureBackend.model.entity.SpaceUser;
import com.cc.ccPictureBackend.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Author cuicui
 * @Description
 */
public interface SpaceUserService extends IService<SpaceUser> {
    Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
