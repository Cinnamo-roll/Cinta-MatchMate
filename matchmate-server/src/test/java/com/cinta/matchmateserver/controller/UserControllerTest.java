package com.cinta.matchmateserver.controller;

import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.exception.GlobalExceptionHandler;
import com.cinta.matchmateserver.model.vo.UserVO;
import com.cinta.matchmateserver.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerReturnsUserId() throws Exception {
        when(userService.userRegister("testuser", "12345678", "12345678")).thenReturn(10L);

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userAccount": "testuser",
                                  "userPassword": "12345678",
                                  "checkPassword": "12345678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    void registerValidationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(userService);
    }

    @Test
    void loginReturnsSafeUserView() throws Exception {
        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUserAccount("testuser");
        when(userService.doLogin(eq("testuser"), eq("12345678"), any())).thenReturn(userVO);

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userAccount": "testuser",
                                  "userPassword": "12345678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userAccount").value("testuser"))
                .andExpect(jsonPath("$.data.userPassword").doesNotExist());
    }

    @Test
    void currentUserWithoutLoginReturnsUnauthorized() throws Exception {
        when(userService.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN));

        mockMvc.perform(get("/user/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN.getCode()));
    }

    @Test
    void nonAdminCannotDeleteUser() throws Exception {
        when(userService.isAdmin(any())).thenReturn(false);

        mockMvc.perform(delete("/user/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH.getCode()));

        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    void adminCanDeleteUser() throws Exception {
        when(userService.isAdmin(any())).thenReturn(true);

        mockMvc.perform(delete("/user/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).deleteUser(2L);
    }

    @Test
    void searchUsersSupportsKeywordAndTags() throws Exception {
        when(userService.searchUserByTags("玉桂狗", java.util.List.of("咖啡")))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/user/search/tags")
                        .param("keyword", "玉桂狗")
                        .param("tagList", "咖啡"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).searchUserByTags("玉桂狗", java.util.List.of("咖啡"));
    }

    @Test
    void updateTagsRejectsMoreThanThreeTags() throws Exception {
        mockMvc.perform(put("/user/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagList": ["跑步", "摄影", "咖啡", "电影"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(userService, never()).updateCurrentUserTags(any(), any());
    }

    @Test
    void updateProfileRejectsInvalidGender() throws Exception {
        mockMvc.perform(put("/user/current")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "测试用户",
                                  "gender": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(userService, never()).updateCurrentUserProfile(any(), any());
    }
}
