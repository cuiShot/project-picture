package com.cc.ccPictureBackend.manager.auth;


import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.cc.ccPictureBackend.constant.SpaceUserPermissionConstant;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.model.entity.Picture;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.SpaceUser;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.SpaceRoleEnum;
import com.cc.ccPictureBackend.model.enums.SpaceTypeEnum;
import com.cc.ccPictureBackend.service.PictureService;
import com.cc.ccPictureBackend.service.SpaceService;
import com.cc.ccPictureBackend.service.SpaceUserService;
import com.cc.ccPictureBackend.service.UserService;
import jdk.nashorn.internal.ir.annotations.Reference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.cc.ccPictureBackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @Author cuicui
 * @Description 自定义权限加载接口实现类
 */
@Component
public class StpInterfaceImpl implements StpInterface {


    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /*
     * 返回一个账号所拥有的权限码集合
     * 具体业务逻辑如下：：
     * 1.校验登录类型：首先检查登录类型 loginType，如果不是“space”类型，则直接返回一个空的权限列表。
     * 2.管理员权限处理：如果当前用户是管理员，则直接返回管理员的权限列表。
     * 3.获取上下文对象：从请求中获取 SpaceUserAuthContext 上下文对象，并检查其中的字段是否为空。如果所有字段都为空（即没有空间或图片信息），则视为公共图库操作，直接返回管理员权限列表。
     * 4.校验登录状态：通过 loginId 获取当前登录用户的信息。如果用户未登录，则抛出未授权异常；如果已登录，则获取用户的唯一标识userId，用于后续的权限判断。
     * 5.从上下文中优先获取 SpaceUser 对象：如果上下文中存在SpaceUser对象，则直接根据其角色获取权限码列表。
     * 6.通过 spaceUserId 获取空间用户信息：如果上下文中存在 spaceUserId：
     *   查询对应的 SpaceUser 数据。如果未找到，则抛出数据未找到异常。
     *   校验当前登录用户是否属于该空间，如果不是，则返回空权限列表。
     *   否则，根据登录用户在该空间的角色，返回相应的权限码列表
     * 7.通过spaceId或pictureId获取空间或图片信息
     *   如果spaceId不存在：使用pictureId查询图片信息。通过图片的spaceId继续判断权限。如果pictureId和spaceId均为空，默认视为管理员权限。
     *   对于公共图库：如果图片是当前用户上传的，或者当前用户为管理员，返回管理员权限列表。如果图片不是当前用户上传的，返回仅允许查看的权限码。
     * 8.获取Space对象并判断空间类型查询Space信息：如果未找到空间数据，抛出数据未找到异常。否则根据空间类型进行判断。
     * 私有空间：
     * 仅空间所有者和管理员有权限（即返回全部权限），其他用户返回空权限列表。
     * 团队空间：
     * 查询登录用户在该空间的角色，并返回对应的权限码列表。
     * 如果用户不属于该空间，返回空权限列表。
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 判断 loginType，仅对类型为 "space" 进行权限校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        // 管理员权限，表示权限校验通过
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 如果所有字段都为空，表示查询公共图库，可以通过
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }
        // 获取 userId
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();
        // 优先从上下文中获取 SpaceUser 对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        // 如果有 spaceUserId，必然是团队空间，通过数据库查询 SpaceUser 对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 取出当前登录用户对应的 spaceUser
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();
        if (spaceId == null) {
            // 如果没有 spaceId，通过 pictureId 获取 Picture 对象和 Space 对象
            Long pictureId = authContext.getPictureId();
            // 图片 id 也没有，则默认通过权限校验
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            // 公共图库，仅本人或管理员可操作
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        // 获取 Space 对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }


    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 本 list 仅做模拟，实际项目中要根据具体业务逻辑来查询角色
        List<String> list = new ArrayList<String>();
        list.add("admin");
        list.add("super-admin");
        return list;
    }


    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }



    @Value("${server.servlet.context-path}")
    private String contextPath; // 在配置文件中,就是 "/api"

    /**
     * 从请求中获取上下文对象
     * 根据路由信息确定填充 SpaceUserAuthContext 的什么参数
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 兼容 get 和 post 操作
        if (ContentType.JSON.getValue().equals(contentType)) {// JSON 格式，兼容 get 类型请求
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {                                              // 表单格式，兼容 post 请求
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            String requestUri = request.getRequestURI();
            String partUri = requestUri.replace(contextPath + "/", "");// 去掉 "/api/"
            String moduleName = StrUtil.subBefore(partUri, "/", false);// 第一个 "/" 前面的字符串
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

}
