package com.cinoo.matchmateserver.user.controller;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.exception.GlobalExceptionHandler;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import com.cinoo.matchmateserver.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
    void updateUserStatusUsesValidatedRequest() throws Exception {
        mockMvc.perform(put("/user/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userStatus": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).updateUserStatus(eq(2L), eq(1), any());
    }

    @Test
    void updateUserStatusRejectsUnsupportedStatus() throws Exception {
        mockMvc.perform(put("/user/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userStatus": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(userService, never()).updateUserStatus(anyLong(), anyInt(), any());
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

    @Test
    void updatePasswordUsesValidatedRequest() throws Exception {
        mockMvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "12345678",
                                  "newPassword": "newPassword123",
                                  "checkPassword": "newPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).updateCurrentUserPassword(
                eq("12345678"),
                eq("newPassword123"),
                eq("newPassword123"),
                any()
        );
    }

    @Test
    void uploadAvatarAcceptsMultipartImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );
        UserVO user = new UserVO();
        user.setAvatarUrl("https://cdn.example.com/avatar.png");
        when(userService.uploadAvatar(any(), any())).thenReturn(user);

        mockMvc.perform(multipart("/user/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(user.getAvatarUrl()));

        verify(userService).uploadAvatar(any(), any());
    }

    @Test
    void uploadAvatarRequiresFilePart() throws Exception {
        mockMvc.perform(multipart("/user/avatar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(userService, never()).uploadAvatar(any(), any());
    }
}
