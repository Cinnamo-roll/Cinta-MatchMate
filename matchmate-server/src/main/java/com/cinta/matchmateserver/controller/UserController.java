package com.cinta.matchmateserver.controller;

import com.cinta.matchmateserver.common.BaseResponse;
import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.common.PageResponse;
import com.cinta.matchmateserver.common.ResultUtils;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.model.request.UserLoginRequest;
import com.cinta.matchmateserver.model.request.UserRegisterRequest;
import com.cinta.matchmateserver.model.vo.UserVO;
import com.cinta.matchmateserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户相关接口")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户注册", description = "用户注册接口")
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@Valid @RequestBody UserRegisterRequest request) {
        long userId = userService.userRegister(
                request.getUserAccount(),
                request.getUserPassword(),
                request.getCheckPassword()
        );
        return ResultUtils.success(userId);
    }

    @Operation(summary = "用户登录", description = "用户登录接口")
    @PostMapping("/login")
    public BaseResponse<UserVO> doLogin(
            @Valid @RequestBody UserLoginRequest loginRequest,
            HttpServletRequest request) {
        UserVO user = userService.doLogin(
                loginRequest.getUserAccount(),
                loginRequest.getUserPassword(),
                request
        );
        return ResultUtils.success(user);
    }

    @Operation(summary = "用户登出", description = "用户登出接口")
    @PostMapping("/logout")
    public BaseResponse<Void> doLogout(HttpServletRequest request) {
        userService.userLogout(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "获取当前用户", description = "获取当前用户接口")
    @GetMapping("/current")
    public BaseResponse<UserVO> getCurrentUser(HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.toUserVO(currentUser));
    }
    @Operation(summary = "用户查询", description = "用户查询接口")
    @GetMapping("/search")
    public BaseResponse<PageResponse<UserVO>> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletRequest request) {
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return ResultUtils.success(userService.searchUsers(username, pageNum, pageSize));
    }

    @Operation(summary = "用户删除", description = "用户删除接口")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteUser(@PathVariable long id, HttpServletRequest request) {
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        userService.deleteUser(id);
        return ResultUtils.success(null);
    }

}
