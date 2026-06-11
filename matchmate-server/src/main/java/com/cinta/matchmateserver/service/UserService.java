package com.cinta.matchmateserver.service;

import com.cinta.matchmateserver.common.PageResponse;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

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
    UserVO doLogin(String userAccount, String userPassword, HttpServletRequest request);

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
    List<UserVO> searchUserByTags(List<String> tagList);
}
