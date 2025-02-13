package com.cc.ccPictureBackend.webSocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.cc.ccPictureBackend.constant.SpaceUserPermissionConstant;
import com.cc.ccPictureBackend.manager.auth.SpaceUserAuthManager;
import com.cc.ccPictureBackend.model.entity.Picture;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.SpaceTypeEnum;
import com.cc.ccPictureBackend.service.PictureService;
import com.cc.ccPictureBackend.service.SpaceService;
import com.cc.ccPictureBackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * @Author cuicui
 * @Description
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Override
    public boolean beforeHandshake( ServerHttpRequest request,  ServerHttpResponse response,  WebSocketHandler wsHandler,  Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 获取请求参数
            String pictureId = servletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("pictureId is null");
                return false;
            }
            // 校验用户登录状态
            User loginUser = userService.getLoginUser(servletRequest);
            if (ObjUtil.isEmpty(loginUser)) {
                log.error("loginUser is null");
                return false;
            }
            // 图片是否存在
            Picture picture = pictureService.getById(pictureId);
            if (ObjUtil.isEmpty(picture)) {
                log.error("picture is null");
                return false;
            }
            // 校验空间 Space
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                if (ObjUtil.isEmpty(space)) {
                    log.error("space is null");
                    return false;
                }
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.error("space type is not team");
                    return false;
                }
            }

            //校验权限
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("no permission");
                return false;
            }

            //设置 attributes
            attributes.put("user", loginUser);
            attributes.put("pictureId", Long.valueOf(pictureId));
            attributes.put("space", spaceId);
        }
        return true;

    }
        @Override
        public void afterHandshake (ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler
        wsHandler, Exception exception){

        }
    }
