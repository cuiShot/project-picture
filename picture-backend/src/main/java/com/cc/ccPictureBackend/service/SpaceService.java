package com.cc.ccPictureBackend.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cc.ccPictureBackend.model.dto.space.SpaceAddRequest;
import com.cc.ccPictureBackend.model.dto.space.SpaceQueryRequest;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.vo.SpaceVO;

import javax.management.Query;
import javax.servlet.http.HttpServletRequest;

/**
 * @Author cuicui
 * @Description
 */
public interface SpaceService extends IService<Space> {
    /**
     * 校验空间数据 Space 数据，flag true代表 创建的时候校验
     *                       flag flase 代表编辑是的校验
     * @param space
     * @param flag
     */
    void validSpace(Space space,boolean flag);

    /**
     * 创建 Space 的时候，需要根据级别自动填充限额数据
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);


    /**
     * 用户创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    Long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     *获取查询条件
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWapper(SpaceQueryRequest spaceQueryRequest);


    /**
     * 获取VOPage
     * @param page
     * @param request
     * @return
     */
    Page<SpaceVO> getSapceVOPage(Page<Space> page, HttpServletRequest request);

    /**
     * 获取封装类
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);
}
