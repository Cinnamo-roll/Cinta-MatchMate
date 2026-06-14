package com.cinoo.matchmateserver.user.service;

import com.cinoo.matchmateserver.common.PageResponse;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.user.model.request.UpdateUserProfileRequest;
import com.cinoo.matchmateserver.user.model.vo.RegistrationPolicyVO;
import com.cinoo.matchmateserver.user.model.vo.UserRecommendationVO;
import com.cinoo.matchmateserver.user.model.vo.UserRegisterResultVO;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户业务服务。
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param checkPassword 校验密码
     * @return 注册结果
     */
    UserRegisterResultVO userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param forceLogin 是否强制接管已登录设备
     * @param request HTTP请求对象
     * @return 脱敏后的用户信息
     */
    UserVO doLogin(
            String userAccount,
            String userPassword,
            boolean forceLogin,
            HttpServletRequest request
    );

    /**
     * 将用户实体转换为接口响应对象。
     *
     * @param user 原用户
     * @return 脱敏后用户
     */
    UserVO toUserVO(User user);

    /**
     * 获取当前登录用户，并从数据库刷新用户状态。
     *
     * @param request HTTP 请求
     * @return 当前登录用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 判断当前登录用户是否为管理员。
     *
     * @param request HTTP 请求
     * @return 当前用户是否为管理员
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 分页搜索用户。
     *
     * @param username 用户昵称
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 用户分页数据
     */
    PageResponse<UserVO> searchUsers(String username, long pageNum, long pageSize);

    /**
     * 逻辑删除用户。
     *
     * @param userId 用户 ID
     */
    void deleteUser(long userId);

    /**
     * 管理员封停或解封普通用户。
     *
     * @param userId 目标用户 ID
     * @param userStatus 0 正常，1 封停
     * @param request HTTP 请求
     */
    void updateUserStatus(long userId, int userStatus, HttpServletRequest request);

    RegistrationPolicyVO getRegistrationPolicy(HttpServletRequest request);

    RegistrationPolicyVO updateRegistrationDailyLimit(
            int dailyLimit,
            HttpServletRequest request
    );

    PageResponse<UserVO> listPendingRegistrations(
            long pageNum,
            long pageSize,
            HttpServletRequest request
    );

    void approveRegistration(long userId, HttpServletRequest request);

    void rejectRegistration(long userId, HttpServletRequest request);

    /**
     * 用户登出。
     *
     * @param request HTTP请求对象
     */
    void userLogout(HttpServletRequest request);

    /**
     * 根据标签搜索用户。
     *
     * @param tagList 必须全部匹配的标签
     * @return 匹配的用户列表
     */
    PageResponse<UserVO> searchUserByTags(
            String keyword,
            List<String> tagList,
            long pageNum,
            long pageSize,
            HttpServletRequest request
    );

    /**
     * 更新当期用户标签。
     *
     * @param tagList 标签列表
     * @param request HTTP请求对象，用于获取当前登录用户信息
     * @return 更新后的用户信息（包含最新标签）
     */
    UserVO updateCurrentUserTags(List<String> tagList, HttpServletRequest request);

    /**
     * 更新当期用户信息。
     *
     * @param updateRequest 包含要更新的用户信息的请求对象
     * @param request HTTP请求对象，用于获取当前登录用户信息
     * @return 更新后的用户信息
     */
    UserVO updateCurrentUserProfile(
            UpdateUserProfileRequest updateRequest,
            HttpServletRequest request
    );

    /**
     * 修改当前登录用户密码。
     *
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     * @param checkPassword 确认新密码
     * @param request HTTP 请求
     */
    void updateCurrentUserPassword(
            String currentPassword,
            String newPassword,
            String checkPassword,
            HttpServletRequest request
    );

    /**
     * 推荐用户（随机）。
     *
     * @param limit 最大推荐数量
     * @return 推荐用户列表
     */
    PageResponse<UserRecommendationVO> recommendUsers(
            long pageNum,
            long pageSize,
            HttpServletRequest request
    );

    /**
     * 注销当前登录用户账户（需验证密码）。
     *
     * @param userPassword 用户密码
     * @param request HTTP 请求
     */
    void deleteCurrentUser(String userPassword, HttpServletRequest request);

    /**
     * 上传当前用户头像至 OSS，同时更新数据库。
     *
     * @param file 头像图片文件
     * @param request HTTP 请求
     * @return 更新后的用户信息
     */
    UserVO uploadAvatar(MultipartFile file, HttpServletRequest request);
}
