package com.cc.ccPictureBackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.cc.ccPictureBackend.constant.UserConstant;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.mapper.UserMapper;
import com.cc.ccPictureBackend.model.dto.user.UserQueryRequest;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.UserRoleEnum;
import com.cc.ccPictureBackend.model.vo.LoginUserVO;
import com.cc.ccPictureBackend.model.vo.UserVO;
import com.cc.ccPictureBackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.cc.ccPictureBackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return id 新注册用户的id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1.校验
        if(StrUtil.hasBlank(userAccount, userPassword,checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if (StrUtil.isBlank(userAccount)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号不能为空");
        }
        if(userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号长度不能小于4");
        }
        if (StrUtil.isBlank(userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码不能为空");
        }
        if (StrUtil.isBlank(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"确认密码不能为空");
        }
        if (userPassword.length() < 8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度不能小于8位");
        }

        if(!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次输入的密码不一样！");
        }

        // 2.注册业务
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        // 2.1 检查重复
        userQueryWrapper.eq("userAccount", userAccount);
        Long count = this.baseMapper.selectCount(userQueryWrapper);// 查出来的count
        if (count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号已存在");
        }

        // 2.2 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 2.3 插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName(userAccount);
        boolean b = this.save(user);
        if (!b){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"注册失败，系统错误！");
        }
        return user.getId();
    }




    /**
     * 用户登录
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户 viewObj
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 用户名、密码格式校验
        //1.校验
        if(StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号或密码不能为空！");
        }
        if(userAccount.length() < 4 ){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号长度不能小于4");
        }
        if (userPassword.length() < 8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度不能小于8位");
        }
        // 2.加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 查询用户是否存在
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("userAccount", userAccount);
        userQueryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(userQueryWrapper);

        // 不存在,报错
        if (user == null){
            log.info("user not exist or password is wrong");
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在或密码错误！");
        }
        // 用户存在,记录用户登录状态,返回脱敏后用户信息
        request.getSession().setAttribute(USER_LOGIN_STATE,user);
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录的用户 ,从request请求对象的session中获取保存的用户信息,无需其它参数
     * @param request http请求
     * @return 用户对象
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        if (user == null || user.getId() == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return user;
    }


    /**
     * 密码加密
     * @param userPassword 原始密码
     * @return 加密后密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "ccui";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }


    /**
     * 获取脱敏后的用户信息
     * @param user
     * @return 脱敏后的用户信息
     */
    public LoginUserVO getLoginUserVO (User user) {
        if(user == null){
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user,loginUserVO);
        return loginUserVO;
    }

    /**
     * 获取用户信息，脱敏后的
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if(user == null){
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user,userVO);
        return userVO;
    }

    /**
     * 获取用户列表,脱敏后的
     * 使用 stream 流
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if(CollUtil.isEmpty(userList)){
            return new ArrayList<>();
        }
        return userList.stream().map(user -> this.getUserVO(user)).collect(Collectors.toList());
//        return Collections.emptyList();
    }

    /**
     * 用户注销
     * @param request
     * @return 返回 bool
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 1.判断登录状态
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if(userObj == null){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"未登录");
        }

        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 将查询请求转化为 QueryWrapper 对象
     * @param userQueryRequest 查询用户请求
     * @return QueryWrapper 对象
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 判断用户是否是管理员
     * @param user
     * @return
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }


}




