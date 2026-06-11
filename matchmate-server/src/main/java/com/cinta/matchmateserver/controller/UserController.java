package com.cinta.matchmateserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cinta.matchmateserver.common.BaseResponse;
import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.common.ResultUtils;
import com.cinta.matchmateserver.constant.UserConstant;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.model.request.UserLoginRequest;
import com.cinta.matchmateserver.model.request.UserRegisterRequest;
import com.cinta.matchmateserver.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

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
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录信息
     * @return 登录成功返回用户信息，失败返回null
     */
    @PostMapping("/login")
    public BaseResponse<User> doLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        User user = userService.doLogin(userAccount, userPassword, request);
        return ResultUtils.success(user);
    }

    /**
     * 用户登出
     *
     * @return 登出成功返回1，失败返回null
     */
    @PostMapping("/logout")
    public BaseResponse<Integer> doLogout(HttpServletRequest request) {
        if (request == null)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        userService.userLogout(request);
        return ResultUtils.success(1);
    }

    /**
     * 获取当前用户
     *
     * @return 当前用户信息
     */
    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(HttpServletRequest request) {
        User currentUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (currentUser == null)
            return ResultUtils.error(ErrorCode.NOT_LOGIN, "未登录", "");
        // TODO 检验用户是否合法

        User safetyUser = userService.getSafetyUser(currentUser);
        return ResultUtils.success(safetyUser);
    }


    /**
     * 用户查询
     *
     * @param username 用户名
     * @return 搜索结果
     */
    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(String username, HttpServletRequest request) {
        if (!isAdmin(request))
            throw new BusinessException(ErrorCode.NO_AUTH);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like("username", username);
        }
        List<User> userList = userService.list(queryWrapper);
        List<User> list = userList.stream().map(user -> userService.getSafetyUser(user)).toList();
        return ResultUtils.success(list);
    }

    /**
     * 用户删除（管理员权限）
     *
     * @param id 用户id
     * @return 删除成功返回true，失败返回false
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody long id, HttpServletRequest request) {
        if (!isAdmin(request))
            throw new BusinessException(ErrorCode.NO_AUTH);
        if (id <= 0)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户id不合法");
        Boolean isSuccess = userService.removeById(id);
        return ResultUtils.success(isSuccess);
    }

    /**
     * 判断是否为管理员
     *
     * @param request 请求
     * @return true为管理员，false为非管理员
     */
    private boolean isAdmin(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null)
            return false;
        User user = (User) userObj;
        return user.getUserRole() == UserConstant.ADMIN_ROLE;
    }
}