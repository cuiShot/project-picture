package com.cc.ccPictureBackend.aop;

import com.cc.ccPictureBackend.annotation.AuthCheck;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.UserRoleEnum;
import com.cc.ccPictureBackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @Author cuicui
 * @Description 实现角色权限校验的 aop
 */
@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;

    /**
     * 在 注解 authCheck 的around 范围插入切点
     *
     * @param pjp       切点
     * @param authCheck 注解
     * @return 返回切点继续执行
     * @throws Throwable
     */
    @Around("@annotation(authCheck)")
    public Object doIntercept(ProceedingJoinPoint pjp, AuthCheck authCheck) throws Throwable {
        // 注解中的 mustRole 信息,这是使用这个注解的方法需要的 角色信息
        String mustRole = authCheck.mustRole();
        // 1.获取 HttpServletRequest 对象，然后获取当前登录信息
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = servletRequestAttributes.getRequest();
        User loginUser = userService.getLoginUser(httpServletRequest);

        // 根据注解中的 mustRole 信息获取 用户类型的枚举类对象.这是使用注解的方法要求的类型
        UserRoleEnum mustUserRole = UserRoleEnum.getEnumByValue(mustRole);

        //1.userRoleEnum 为空,使用 authCheck 的注解的方法不需要权限,放行
        if (mustUserRole == null) {
            return pjp.proceed();
        }
        // 2.mustUserRole 不为空，需要权限判断
        // 获取当前用户具有的权限
        UserRoleEnum currenUserRole = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 2.1 ，没有权限，拒绝
        if (currenUserRole == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 2.2 要求有管理员权限,但是 currenUserRole 不是管理员权限,拒绝
        if (mustUserRole.equals(UserRoleEnum.ADMIN)
                && !currenUserRole.equals(UserRoleEnum.ADMIN)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 2.3 放行
        return pjp.proceed();
    }
}
