package com.cinta.matchmateserver.service;

import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.model.domain.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author CinnamoRoll
 * @description UserService 完整测试用例
 */
@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

    @Resource
    private UserService userService;

    private static final String VALID_ACCOUNT = "testuser";
    private static final String VALID_PASSWORD = "12345678";
    private static Long registeredUserId;

    // ==================== 注册测试 ====================

    @Test
    @Order(1)
    @DisplayName("注册成功 - 合法账号密码")
    void testRegisterSuccess() {
        long id = userService.userRegister(VALID_ACCOUNT, VALID_PASSWORD, VALID_PASSWORD);
        registeredUserId = id;
        assertTrue(id > 0, "注册成功后应返回正数ID");
        System.out.println("注册成功，用户ID: " + id);
    }

    @Test
    @Order(2)
    @DisplayName("注册失败 - 账号为空")
    void testRegisterEmptyAccount() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.userRegister("", VALID_PASSWORD, VALID_PASSWORD)
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(3)
    @DisplayName("注册失败 - 密码为空")
    void testRegisterEmptyPassword() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.userRegister(VALID_ACCOUNT, "", "")
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(4)
    @DisplayName("注册失败 - 账号长度小于4")
    void testRegisterShortAccount() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.userRegister("ab", VALID_PASSWORD, VALID_PASSWORD)
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(5)
    @DisplayName("注册失败 - 密码长度小于8")
    void testRegisterShortPassword() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.userRegister("validAcc", "1234567", "1234567")
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(6)
    @DisplayName("注册失败 - 账号含特殊字符")
    void testRegisterInvalidAccountFormat() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.userRegister("test@user", VALID_PASSWORD, VALID_PASSWORD)
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(7)
    @DisplayName("注册失败 - 两次密码不一致")
    void testRegisterPasswordMismatch() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.userRegister(VALID_ACCOUNT, VALID_PASSWORD, "differentPwd123")
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(8)
    @DisplayName("注册失败 - 账号已存在")
    void testRegisterDuplicateAccount() {
        // 先注册一个用户
        userService.userRegister("dupuser", VALID_PASSWORD, VALID_PASSWORD);
        // 再用相同账号注册应抛异常
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.userRegister("dupuser", VALID_PASSWORD, VALID_PASSWORD)
        );
        assertEquals(40000, ex.getCode());
    }

    // ==================== 登录测试 ====================

    @Test
    @Order(9)
    @DisplayName("登录成功 - 正确账号密码")
    void testLoginSuccess() {
        // 先注册
        userService.userRegister("logintest", VALID_PASSWORD, VALID_PASSWORD);

        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = userService.doLogin("logintest", VALID_PASSWORD, request);

        assertNotNull(user, "登录成功应返回用户信息");
        assertEquals("logintest", user.getUserAccount());
        assertNull(user.getUserPassword(), "返回的用户不应包含密码");
        // 验证session中有登录态
        assertNotNull(request.getSession().getAttribute("userLoginState"));
    }

    @Test
    @Order(10)
    @DisplayName("登录失败 - 密码错误")
    void testLoginWrongPassword() {
        userService.userRegister("pwdtest", VALID_PASSWORD, VALID_PASSWORD);

        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.doLogin("pwdtest", "wrongpwd999", request)
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(11)
    @DisplayName("登录失败 - 用户不存在")
    void testLoginNonexistentUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.doLogin("nouser999", VALID_PASSWORD, request)
        );
        assertEquals(40000, ex.getCode());
    }

    @Test
    @Order(12)
    @DisplayName("登录失败 - 账号为空")
    void testLoginEmptyAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.doLogin("", VALID_PASSWORD, request)
        );
        assertEquals(40000, ex.getCode());
    }

    // ==================== 脱敏测试 ====================

    @Test
    @Order(13)
    @DisplayName("脱敏 - 返回用户不含密码")
    void testGetSafetyUser() {
        User origin = new User();
        origin.setId(1L);
        origin.setUsername("safetyTest");
        origin.setUserAccount("safetyAcc");
        origin.setAvatarUrl("https://example.com/avatar.png");
        origin.setGender(1);
        origin.setUserPassword("secretPassword");
        origin.setPhone("13800138000");
        origin.setEmail("safety@test.com");
        origin.setUserStatus(0);
        origin.setUserRole(0);

        User safety = userService.getSafetyUser(origin);

        assertEquals(origin.getId(), safety.getId());
        assertEquals(origin.getUsername(), safety.getUsername());
        assertEquals(origin.getUserAccount(), safety.getUserAccount());
        assertEquals(origin.getAvatarUrl(), safety.getAvatarUrl());
        assertEquals(origin.getGender(), safety.getGender());
        assertEquals(origin.getPhone(), safety.getPhone());
        assertEquals(origin.getEmail(), safety.getEmail());
        assertEquals(origin.getUserStatus(), safety.getUserStatus());
        assertEquals(origin.getUserRole(), safety.getUserRole());
        assertNull(safety.getUserPassword(), "脱敏后密码应为null");
    }

    // ==================== 登出测试 ====================

    @Test
    @Order(14)
    @DisplayName("登出成功 - 清除session登录态")
    void testLogout() {
        // 先注册并登录
        userService.userRegister("logouttest", VALID_PASSWORD, VALID_PASSWORD);
        MockHttpServletRequest request = new MockHttpServletRequest();
        userService.doLogin("logouttest", VALID_PASSWORD, request);

        // 验证登录态存在
        assertNotNull(request.getSession().getAttribute("userLoginState"));

        // 登出
        userService.userLogout(request);

        // 验证登录态已清除
        assertNull(request.getSession().getAttribute("userLoginState"));
    }

    // ==================== 数据库直接操作测试 ====================

    @Test
    @Order(15)
    @DisplayName("MyBatis-Plus save - 直接插入用户")
    void testInsert() {
        User user = new User();
        user.setUsername("testName");
        user.setUserAccount("123");
        user.setAvatarUrl("https://baomidou.com/assets/asset.cIbiVTt_.svg");
        user.setGender(0);
        user.setUserPassword("12345");
        user.setPhone("13125200421");
        user.setEmail("1391571546@qq.com");
        boolean result = userService.save(user);
        System.out.println("测试用户id：" + user.getId());
        assertTrue(result);
    }
}