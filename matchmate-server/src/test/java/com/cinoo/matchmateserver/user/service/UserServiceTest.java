package com.cinoo.matchmateserver.user.service;

import com.cinoo.matchmateserver.infrastructure.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.infrastructure.cache.CacheNames;
import com.cinoo.matchmateserver.infrastructure.cache.DistributedCacheService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.user.constant.UserConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.user.mapper.UserMapper;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.user.model.request.UpdateUserProfileRequest;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import com.cinoo.matchmateserver.user.service.UserServiceImpl;
import com.cinoo.matchmateserver.infrastructure.oss.OssUtils;
import com.cinoo.matchmateserver.chat.service.OnlineUserService;
import com.cinoo.matchmateserver.chat.websocket.ChatWebSocketHandler;
import com.cinoo.matchmateserver.tag.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String ACCOUNT = "testuser";
    private static final String PASSWORD = "12345678";

    @Mock
    private UserMapper userMapper;

    @Mock
    private TagService tagService;

    @Mock
    private DistributedCacheService cacheService;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Mock
    private OssUtils ossUtils;

    @Mock
    private OnlineUserService onlineUserService;

    @Mock
    private ChatWebSocketHandler chatWebSocketHandler;

    private PasswordService passwordService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        lenient().when(cacheService.get(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> loader = invocation.getArgument(2);
                    return loader.get();
                });
        userService = new UserServiceImpl(
                userMapper,
                passwordService,
                tagService,
                cacheService,
                cacheInvalidationService,
                ossUtils,
                onlineUserService,
                chatWebSocketHandler
        );
    }

    @Test
    void registerStoresBcryptPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });

        long userId = userService.userRegister(ACCOUNT, PASSWORD, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(10L, userId);
        assertNotEquals(PASSWORD, captor.getValue().getUserPassword());
        assertEquals(ACCOUNT, captor.getValue().getUsername());
        assertEquals(UserConstant.DEFAULT_GENDER, captor.getValue().getGender());
        assertTrue(passwordService.matches(PASSWORD, captor.getValue().getUserPassword()));
        verify(cacheInvalidationService).userCollectionChanged();
    }

    @Test
    void registerNormalizesAccountToLowercase() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });

        userService.userRegister("TestUser", PASSWORD, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(ACCOUNT, captor.getValue().getUserAccount());
    }

    @Test
    void registerRejectsPasswordMismatch() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.userRegister(ACCOUNT, PASSWORD, "87654321")
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(userMapper);
    }

    @Test
    void loginStoresOnlyUserIdInSession() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(tagService.getUserTagNames(user.getId())).thenReturn(List.of("Java"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        UserVO result = userService.doLogin(ACCOUNT, PASSWORD, request);

        assertEquals(user.getId(), result.getId());
        assertEquals(user.getId(), request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE));
    }

    @Test
    void loginAcceptsAccountWithDifferentCase() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();

        UserVO result = userService.doLogin("TestUser", PASSWORD, request);

        assertEquals(user.getId(), result.getId());
        assertEquals(user.getId(), request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE));
    }

    @Test
    void loginDoesNotExposePassword() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);

        UserVO result = userService.doLogin(ACCOUNT, PASSWORD, new MockHttpServletRequest());

        assertNotNull(result);
        assertFalse(List.of(result.getClass().getDeclaredFields()).stream()
                .anyMatch(field -> field.getName().equals("userPassword")));
    }

    @Test
    void loginRejectsDisabledUser() {
        User user = activeUser();
        user.setUserStatus(1);
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.doLogin(ACCOUNT, PASSWORD, new MockHttpServletRequest())
        );

        assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        assertEquals("账号已被封禁，请联系管理员", exception.getDescription());
    }

    @Test
    void getLoginUserReloadsLatestUser() {
        User user = activeUser();
        when(userMapper.selectById(user.getId())).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user.getId());

        User result = userService.getLoginUser(request);

        assertSame(user, result);
        verify(userMapper).selectById(user.getId());
    }

    @Test
    void searchUsesKeywordAndAllSelectedTags() {
        User user = activeUser();
        when(userMapper.searchByKeywordAndTags("test", List.of("Java", "跑步"), 2))
                .thenReturn(List.of(user));
        when(tagService.getUserTagNames(user.getId())).thenReturn(List.of("Java", "跑步"));

        List<UserVO> result = userService.searchUserByTags(
                " test ",
                List.of("Java", "跑步", "Java")
        );

        assertEquals(1, result.size());
        assertEquals(List.of("Java", "跑步"), result.get(0).getUserTags());
        verify(userMapper).searchByKeywordAndTags("test", List.of("Java", "跑步"), 2);
        verify(cacheService, never()).get(
                eq(CacheNames.USER_SEARCHES),
                anyString(),
                any()
        );
    }

    @Test
    void emptySearchUsesSharedCache() {
        when(userMapper.searchByKeywordAndTags(null, List.of(), 0)).thenReturn(List.of());

        userService.searchUserByTags("", List.of());

        verify(cacheService).get(
                eq(CacheNames.USER_SEARCHES),
                anyString(),
                any()
        );
    }

    @Test
    void recommendationRejectsUnboundedLimit() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.recommendUsers(51)
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(userMapper);
    }

    @Test
    void searchRejectsMoreThanThreeTags() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.searchUserByTags(
                        "",
                        List.of("Java", "跑步", "摄影", "电影")
                )
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(userMapper);
    }

    @Test
    void updateCurrentUserTagsUsesCurrentUser() {
        User user = activeUser();
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(tagService.getUserTagNames(user.getId())).thenReturn(List.of("跑步", "摄影"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user.getId());

        UserVO result = userService.updateCurrentUserTags(List.of("跑步", "摄影"), request);

        verify(tagService).replaceUserTags(user.getId(), List.of("跑步", "摄影"));
        assertEquals(List.of("跑步", "摄影"), result.getUserTags());
    }

    @Test
    void updateCurrentUserProfileUpdatesAllowedFields() {
        User user = activeUser();
        User updatedUser = activeUser();
        updatedUser.setUsername("新昵称");
        when(userMapper.selectById(user.getId()))
                .thenReturn(user)
                .thenReturn(updatedUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(tagService.getUserTagNames(user.getId())).thenReturn(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user.getId());
        UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest();
        updateRequest.setUsername(" 新昵称 ");
        updateRequest.setGender(2);

        UserVO result = userService.updateCurrentUserProfile(updateRequest, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("新昵称", captor.getValue().getUsername());
        assertEquals(2, captor.getValue().getGender());
        assertEquals("新昵称", result.getUsername());
    }

    @Test
    void updateCurrentUserPasswordStoresNewBcryptPassword() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user.getId());

        userService.updateCurrentUserPassword(PASSWORD, "newPassword123", "newPassword123", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertTrue(passwordService.matches("newPassword123", captor.getValue().getUserPassword()));
        assertFalse(passwordService.matches(PASSWORD, captor.getValue().getUserPassword()));
    }

    @Test
    void updateCurrentUserPasswordRejectsWrongCurrentPassword() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectById(user.getId())).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user.getId());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateCurrentUserPassword(
                        "wrongPassword",
                        "newPassword123",
                        "newPassword123",
                        request
                )
        );

        assertEquals("当前密码错误", exception.getDescription());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void adminCanBanNormalUser() {
        User admin = activeUser();
        admin.setUserRole(UserConstant.ADMIN_ROLE);
        User target = activeUser();
        target.setId(2L);
        when(userMapper.selectById(1L)).thenReturn(admin);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updateUserStatus(
                target.getId(),
                UserConstant.BANNED_STATUS,
                loggedInRequest(admin.getId())
        );

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(target.getId(), captor.getValue().getId());
        assertEquals(UserConstant.BANNED_STATUS, captor.getValue().getUserStatus());
        verify(cacheInvalidationService).userChanged(target.getId());
        verify(chatWebSocketHandler).pushAccountBannedAndDisconnect(
                eq(target.getId()),
                contains("封禁")
        );
        verify(onlineUserService).userOffline(target.getId());
    }

    @Test
    void normalUserCannotUpdateUserStatus() {
        User user = activeUser();
        when(userMapper.selectById(user.getId())).thenReturn(user);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(
                        2L,
                        UserConstant.BANNED_STATUS,
                        loggedInRequest(user.getId())
                )
        );

        assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void adminCannotUpdateOwnStatusOrAnotherAdmin() {
        User admin = activeUser();
        admin.setUserRole(UserConstant.ADMIN_ROLE);
        when(userMapper.selectById(admin.getId())).thenReturn(admin);

        assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(
                        admin.getId(),
                        UserConstant.BANNED_STATUS,
                        loggedInRequest(admin.getId())
                )
        );

        User anotherAdmin = activeUser();
        anotherAdmin.setId(2L);
        anotherAdmin.setUserRole(UserConstant.ADMIN_ROLE);
        when(userMapper.selectById(2L)).thenReturn(anotherAdmin);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(
                        anotherAdmin.getId(),
                        UserConstant.BANNED_STATUS,
                        loggedInRequest(admin.getId())
                )
        );

        assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void uploadAvatarPersistsNewUrlBeforeDeletingOldObject() {
        User user = activeUser();
        user.setAvatarUrl("https://cdn.example.com/avatars/1/old.jpg");
        User updatedUser = activeUser();
        updatedUser.setAvatarUrl("https://cdn.example.com/avatars/1/new.jpg");
        when(userMapper.selectById(user.getId()))
                .thenReturn(user)
                .thenReturn(updatedUser);
        when(ossUtils.uploadAvatar(eq(user.getId()), any()))
                .thenReturn(updatedUser.getAvatarUrl());
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        MockHttpServletRequest request = loggedInRequest(user.getId());

        UserVO result = userService.uploadAvatar(
                new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1}),
                request
        );

        assertEquals(updatedUser.getAvatarUrl(), result.getAvatarUrl());
        InOrder order = inOrder(ossUtils, userMapper);
        order.verify(ossUtils).uploadAvatar(eq(user.getId()), any());
        order.verify(userMapper).updateById(any(User.class));
        order.verify(ossUtils).deleteIfManaged(user.getAvatarUrl());
        verify(cacheInvalidationService).userChanged(user.getId());
    }

    @Test
    void uploadAvatarDeletesNewObjectWhenDatabaseUpdateFails() {
        User user = activeUser();
        user.setAvatarUrl("https://cdn.example.com/avatars/1/old.jpg");
        String newAvatarUrl = "https://cdn.example.com/avatars/1/new.jpg";
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(ossUtils.uploadAvatar(eq(user.getId()), any())).thenReturn(newAvatarUrl);
        when(userMapper.updateById(any(User.class))).thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> userService.uploadAvatar(
                        new MockMultipartFile(
                                "file",
                                "avatar.jpg",
                                "image/jpeg",
                                new byte[]{1}
                        ),
                        loggedInRequest(user.getId())
                )
        );

        verify(ossUtils).deleteIfManaged(newAvatarUrl);
        verify(ossUtils, never()).deleteIfManaged(user.getAvatarUrl());
        verify(cacheInvalidationService, never()).userChanged(anyLong());
    }

    private MockHttpServletRequest loggedInRequest(long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, userId);
        return request;
    }

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount(ACCOUNT);
        user.setUserStatus(0);
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        return user;
    }
}
