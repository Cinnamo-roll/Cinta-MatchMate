package com.cinoo.matchmateserver.user.controller;

import com.cinoo.matchmateserver.common.BaseResponse;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.common.PageResponse;
import com.cinoo.matchmateserver.common.ResultUtils;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.user.model.request.UserLoginRequest;
import com.cinoo.matchmateserver.user.model.request.UserRegisterRequest;
import com.cinoo.matchmateserver.user.model.request.UpdateUserTagsRequest;
import com.cinoo.matchmateserver.user.model.request.DeleteAccountRequest;
import com.cinoo.matchmateserver.user.model.request.UpdateUserProfileRequest;
import com.cinoo.matchmateserver.user.model.request.UpdatePasswordRequest;
import com.cinoo.matchmateserver.user.model.request.UpdateUserStatusRequest;
import com.cinoo.matchmateserver.user.model.vo.UserRecommendationVO;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import com.cinoo.matchmateserver.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    @Operation(summary = "更新当前用户资料", description = "更新当前登录用户的公开资料")
    @PutMapping("/current")
    public BaseResponse<UserVO> updateCurrentUser(
            @Valid @RequestBody UpdateUserProfileRequest updateRequest,
            HttpServletRequest request) {
        return ResultUtils.success(
                userService.updateCurrentUserProfile(updateRequest, request)
        );
    }

    @Operation(summary = "修改密码", description = "验证当前密码后修改当前登录用户密码")
    @PutMapping("/password")
    public BaseResponse<Void> updateCurrentUserPassword(
            @Valid @RequestBody UpdatePasswordRequest updateRequest,
            HttpServletRequest request) {
        userService.updateCurrentUserPassword(
                updateRequest.getCurrentPassword(),
                updateRequest.getNewPassword(),
                updateRequest.getCheckPassword(),
                request
        );
        return ResultUtils.success(null);
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

    @Operation(summary = "更新用户状态", description = "管理员封停或解封普通用户")
    @PutMapping("/{id}/status")
    public BaseResponse<Void> updateUserStatus(
            @PathVariable long id,
            @Valid @RequestBody UpdateUserStatusRequest updateRequest,
            HttpServletRequest request) {
        userService.updateUserStatus(id, updateRequest.getUserStatus(), request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "用户搜索", description = "用户搜索接口")
    @GetMapping("/search/tags")
    public BaseResponse<PageResponse<UserVO>> searchUsersByTags(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tagList,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            HttpServletRequest request) {
        return ResultUtils.success(
                userService.searchUserByTags(keyword, tagList, pageNum, pageSize, request)
        );
    }

    @Operation(summary = "推荐用户", description = "随机推荐用户，默认8人")
    @GetMapping("/recommend")
    public BaseResponse<PageResponse<UserRecommendationVO>> recommendUsers(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            HttpServletRequest request) {
        return ResultUtils.success(
                userService.recommendUsers(pageNum, pageSize, request)
        );
    }

    @Operation(summary = "更新个人标签", description = "替换当前登录用户的全部标签，最多 3 个")
    @PutMapping("/tags")
    public BaseResponse<UserVO> updateCurrentUserTags(
            @Valid @RequestBody UpdateUserTagsRequest updateRequest,
            HttpServletRequest request) {
        return ResultUtils.success(
                userService.updateCurrentUserTags(updateRequest.getTagList(), request)
        );
    }

    @Operation(summary = "注销账户", description = "注销当前登录用户账户，需验证密码")
    @DeleteMapping("/current")
    public BaseResponse<Void> deleteCurrentUser(
            @Valid @RequestBody DeleteAccountRequest deleteRequest,
            HttpServletRequest request) {
        userService.deleteCurrentUser(deleteRequest.getUserPassword(), request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "上传头像", description = "上传当前用户头像图片至 OSS")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<UserVO> uploadAvatar(
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        return ResultUtils.success(userService.uploadAvatar(file, request));
    }
}
