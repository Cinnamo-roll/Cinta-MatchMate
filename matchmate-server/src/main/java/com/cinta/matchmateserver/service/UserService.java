package com.cinta.matchmateserver.service;

import com.cinta.matchmateserver.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author CinnamoRoll
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-06-10 15:28:16
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param request HTTP请求对象
     * @return 脱敏后的用户信息
     */
    User doLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户脱敏
     *
     * @param originUser 原用户
     * @return 脱敏后用户
     */
    User getSafetyUser(User originUser);

    /**
     * 用户注销
     *
     * @param request HTTP请求对象
     */
    void userLogout(HttpServletRequest request);
}
