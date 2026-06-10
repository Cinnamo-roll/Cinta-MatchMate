package com.cinta.matchmateserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cinta.matchmateserver.contant.UserConstant;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.model.request.UserLoginRequest;
import com.cinta.matchmateserver.model.request.UserRegisterRequest;
import com.cinta.matchmateserver.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册信息
     * @return 注册成功返回用户id，失败返回null
     */
    @PostMapping("/register")
    public Long userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null)
            return null;
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword))
            return null;
        return userService.userRegister(userAccount, userPassword, checkPassword);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录信息
     * @return 登录成功返回用户信息，失败返回null
     */
    @PostMapping("/login")
    public User dologin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null)
            return null;
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword))
            return null;
        return userService.doLogin(userAccount, userPassword, request);
    }

    /**
     * 用户查询
     *
     * @param username 用户名
     * @return 搜索结果
     */
    @GetMapping("/search")
    public List<User> searchUsers(String username, HttpServletRequest request) {
        if (!isAdmin(request))
            return new ArrayList<>();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like("username", username);
        }
        List<User> userList = userService.list(queryWrapper);
        return userList.stream().map(user -> userService.getSafetyUser(user)).toList();
    }

    /**
     * 用户删除
     *
     * @param id 用户id
     * @return 删除成功返回true，失败返回false
     */
    @PostMapping("/delete")
    public boolean searchUsers(@RequestBody long id, HttpServletRequest request) {
        if (!isAdmin(request))
            return false;
        if (id <= 0)
            return false;
        return userService.removeById(id);
    }

    /**
     * 判断是否为管理员
     *
     * @param request 请求
     * @return true为管理员，false为非管理员
     */
    private boolean isAdmin(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getUserRole() == UserConstant.ADMIN_ROLE;
    }
}
