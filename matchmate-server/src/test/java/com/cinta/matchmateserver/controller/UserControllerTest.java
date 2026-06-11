package com.cinta.matchmateserver.controller;

import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.constant.UserConstant;
import com.cinta.matchmateserver.model.domain.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author CinnamoRoll
 * @description UserController 集成测试 (SpringBootTest + MockMvc)
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerTest {

    @Resource
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    private static final String VALID_ACCOUNT = "testuser";
    private static final String VALID_PASSWORD = "12345678";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // ==================== 注册接口 ====================

    @Test
    @Order(1)
    @DisplayName("POST /user/register - 注册成功")
    void testRegisterSuccess() throws Exception {
        String body = "{\"userAccount\":\"testuser\",\"userPassword\":\"12345678\",\"checkPassword\":\"12345678\"}";
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @Order(2)
    @DisplayName("POST /user/register - 请求体为null")
    void testRegisterNullBody() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @Order(3)
    @DisplayName("POST /user/register - 参数字段全为空字符串")
    void testRegisterEmptyParams() throws Exception {
        String body = "{\"userAccount\":\"\",\"userPassword\":\"\",\"checkPassword\":\"\"}";
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 登录接口 ====================

    @Test
    @Order(4)
    @DisplayName("POST /user/login - 登录成功")
    void testLoginSuccess() throws Exception {
        // 先注册测试用户
        String registerBody = "{\"userAccount\":\"logintest\",\"userPassword\":\"12345678\",\"checkPassword\":\"12345678\"}";
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String body = "{\"userAccount\":\"logintest\",\"userPassword\":\"12345678\"}";
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userAccount").value("logintest"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /user/login - 登录失败（用户不存在）")
    void testLoginFailed() throws Exception {
        String body = "{\"userAccount\":\"nouser99\",\"userPassword\":\"12345678\"}";
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 登出接口 ====================

    @Test
    @Order(6)
    @DisplayName("POST /user/logout - 登出成功")
    void testLogout() throws Exception {
        mockMvc.perform(post("/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 获取当前用户 ====================

    @Test
    @Order(7)
    @DisplayName("GET /user/current - 未登录返回未登录错误")
    void testGetCurrentUserNotLogin() throws Exception {
        mockMvc.perform(get("/user/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN.getCode()))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    // ==================== 用户查询（管理员） ====================

    @Test
    @Order(8)
    @DisplayName("GET /user/search - 管理员搜索用户")
    void testSearchUsersByAdmin() throws Exception {
        String registerBody = "{\"userAccount\":\"searchtest\",\"userPassword\":\"12345678\",\"checkPassword\":\"12345678\"}";
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUserRole(UserConstant.ADMIN_ROLE);

        mockMvc.perform(get("/user/search")
                        .param("username", "search")
                        .sessionAttr(UserConstant.USER_LOGIN_STATE, adminUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(9)
    @DisplayName("GET /user/search - 非管理员搜索被拒绝")
    void testSearchUsersByNonAdmin() throws Exception {
        User normalUser = new User();
        normalUser.setId(2L);
        normalUser.setUserRole(UserConstant.DEFAULT_ROLE);

        mockMvc.perform(get("/user/search")
                        .param("username", "target")
                        .sessionAttr(UserConstant.USER_LOGIN_STATE, normalUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH.getCode()));
    }

    // ==================== 用户删除（管理员） ====================

    @Test
    @Order(10)
    @DisplayName("POST /user/delete - 非管理员删除被拒绝")
    void testDeleteUserByNonAdmin() throws Exception {
        User normalUser = new User();
        normalUser.setId(2L);
        normalUser.setUserRole(UserConstant.DEFAULT_ROLE);

        mockMvc.perform(post("/user/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("999")
                        .sessionAttr(UserConstant.USER_LOGIN_STATE, normalUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH.getCode()));
    }
}