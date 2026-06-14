package com.cinoo.matchmateserver.user.service;

import com.cinoo.matchmateserver.infrastructure.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.infrastructure.cache.DistributedCacheService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.common.PageResponse;
import com.cinoo.matchmateserver.user.constant.UserConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.user.mapper.UserMapper;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.user.model.request.UpdateUserProfileRequest;
import com.cinoo.matchmateserver.user.model.vo.UserRecommendationVO;
import com.cinoo.matchmateserver.user.model.vo.UserRegisterResultVO;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import com.cinoo.matchmateserver.user.service.impl.UserServiceImpl;
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
    private LoginSessionRegistry loginSessionRegistry;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        lenient().when(cacheService.get(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> loader = invocation.getArgument(2);
                    return loader.get();
                });
        lenient().when(userMapper.selectAppSettingInt(anyString())).thenReturn(20);
        lenient().when(userMapper.countRegistrationsByStatusAndTimeRange(anyInt(), any(), any()))
                .thenReturn(0L);
        loginSessionRegistry = new LoginSessionRegistry();
        userService = new UserServiceImpl(
                userMapper,
                passwordService,
                tagService,
                cacheService,
                cacheInvalidationService,
                ossUtils,
                onlineUserService,
                chatWebSocketHandler,
                loginSessionRegistry
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

        UserRegisterResultVO result = userService.userRegister(ACCOUNT, PASSWORD, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(10L, result.getUserId());
        assertFalse(result.getPendingReview());
        assertEquals(UserConstant.NORMAL_STATUS, captor.getValue().getUserStatus());
        assertNotEquals(PASSWORD, captor.getValue().getUserPassword());
        assertEquals(ACCOUNT, captor.getValue().getUsername());
        assertEquals(UserConstant.DEFAULT_GENDER, captor.getValue().getGender());
        assertTrue(passwordService.matches(PASSWORD, captor.getValue().getUserPassword()));
        verify(cacheInvalidationService).userCollectionChanged();
    }

    @Test
    void registerExceedingDailyLimitEntersPendingReview() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectAppSettingInt(anyString())).thenReturn(1);
        when(userMapper.countRegistrationsByStatusAndTimeRange(eq(UserConstant.NORMAL_STATUS), any(), any()))
                .thenReturn(1L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(11L);
            return 1;
        });

        UserRegisterResultVO result = userService.userRegister(ACCOUNT, PASSWORD, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(11L, result.getUserId());
        assertTrue(result.getPendingReview());
        assertEquals(UserConstant.PENDING_REVIEW_STATUS, captor.getValue().getUserStatus());
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

        UserVO result = userService.doLogin(ACCOUNT, PASSWORD, false, request);

        assertEquals(user.getId(), result.getId());
        assertEquals(user.getId(), request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE));
    }

    @Test
    void loginAcceptsAccountWithDifferentCase() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();

        UserVO result = userService.doLogin("TestUser", PASSWORD, false, request);

        assertEquals(user.getId(), result.getId());
        assertEquals(user.getId(), request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE));
    }

    @Test
    void loginDoesNotExposePassword() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);

        UserVO result = userService.doLogin(ACCOUNT, PASSWORD, false, new MockHttpServletRequest());

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
                () -> userService.doLogin(ACCOUNT, PASSWORD, false, new MockHttpServletRequest())
        );

        assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        assertEquals("账号已被封禁，请联系管理员", exception.getDescription());
    }

    @Test
    void loginRejectsPendingRegistration() {
        User user = activeUser();
        user.setUserStatus(UserConstant.PENDING_REVIEW_STATUS);
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.doLogin(ACCOUNT, PASSWORD, false, new MockHttpServletRequest())
        );

        assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        assertEquals("注册申请正在等待管理员审核", exception.getDescription());
    }

    @Test
    void loginRejectsAnotherActiveSessionWithoutForce() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(tagService.getUserTagNames(user.getId())).thenReturn(List.of());

        userService.doLogin(ACCOUNT, PASSWORD, false, new MockHttpServletRequest());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.doLogin(
                        ACCOUNT,
                        PASSWORD,
                        false,
                        new MockHttpServletRequest()
                )
        );

        assertEquals(ErrorCode.LOGIN_CONFLICT.getCode(), exception.getCode());
        assertEquals(
                "该账号已在其他设备保持登录，是否继续登录并使原设备下线？",
                exception.getDescription()
        );
    }

    @Test
    void forceLoginInvalidatesPreviousSessionAndNotifiesOldDevice() {
        User user = activeUser();
        user.setUserPassword(passwordService.encode(PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(tagService.getUserTagNames(user.getId())).thenReturn(List.of());
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        userService.doLogin(ACCOUNT, PASSWORD, false, firstRequest);
        var firstSession = firstRequest.getSession(false);

        MockHttpServletRequest takeoverRequest = new MockHttpServletRequest();
        UserVO result = userService.doLogin(ACCOUNT, PASSWORD, true, takeoverRequest);

        assertEquals(user.getId(), result.getId());
        assertThrows(
                IllegalStateException.class,
                () -> firstSession.getAttribute(UserConstant.USER_LOGIN_STATE)
        );
        assertEquals(
                user.getId(),
                takeoverRequest.getSession(false).getAttribute(UserConstant.USER_LOGIN_STATE)
        );
        verify(chatWebSocketHandler).pushLoginTakenOverAndDisconnect(
                eq(user.getId()),
                contains("其他设备登录")
        );
        verify(onlineUserService).userOffline(user.getId());
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
        Page<User> userPage = new Page<>(1, 10);
        userPage.setRecords(List.of(user));
        userPage.setTotal(1);
        when(userMapper.searchPageByKeywordAndTags(
                any(Page.class),
                eq("test"),
                eq(List.of("Java", "跑步")),
                eq(2),
                eq(99L),
                eq(UserConstant.ADMIN_ROLE)
        )).thenReturn(userPage);
        when(tagService.getUserTagNames(user.getId())).thenReturn(List.of("Java", "跑步"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, 99L);
        PageResponse<UserVO> result = userService.searchUserByTags(
                " test ",
                List.of("Java", "跑步", "Java"),
                1,
                10,
                request
        );

        assertEquals(1, result.getTotal());
        assertEquals(List.of("Java", "跑步"), result.getRecords().get(0).getUserTags());
        verify(userMapper).searchPageByKeywordAndTags(
                any(Page.class),
                eq("test"),
                eq(List.of("Java", "跑步")),
                eq(2),
                eq(99L),
                eq(UserConstant.ADMIN_ROLE)
        );
    }

    @Test
    void emptySearchUsesRequestedPage() {
        Page<User> userPage = new Page<>(2, 10);
        when(userMapper.searchPageByKeywordAndTags(
                any(Page.class),
                isNull(),
                eq(List.of()),
                eq(0),
                isNull(),
                eq(UserConstant.ADMIN_ROLE)
        )).thenReturn(userPage);

        PageResponse<UserVO> result = userService.searchUserByTags(
                "",
                List.of(),
                2,
                10,
                new MockHttpServletRequest()
        );

        assertEquals(2, result.getPageNum());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void recommendationRejectsUnboundedLimit() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.recommendUsers(
                        1,
                        101,
                        new MockHttpServletRequest()
                )
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(userMapper);
    }

    @Test
    void recommendationRanksCommonTagsBeforeOnlineOnly() {
        User commonTagUser = activeUser();
        commonTagUser.setId(2L);
        commonTagUser.setUserAccount("javafan");
        commonTagUser.setUsername("Java Fan");
        User onlineOnlyUser = activeUser();
        onlineOnlyUser.setId(3L);
        onlineOnlyUser.setUserAccount("runner");
        onlineOnlyUser.setUsername("Runner");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, 1L);
        when(tagService.getUserTagNames(1L)).thenReturn(List.of("Java"));
        when(tagService.getUserTagNames(2L)).thenReturn(List.of("Java", "音乐"));
        when(tagService.getUserTagNames(3L)).thenReturn(List.of("跑步"));
        when(onlineUserService.isOnline(2L)).thenReturn(false);
        when(onlineUserService.isOnline(3L)).thenReturn(true);
        when(userMapper.selectRecommendationCandidates(1L, 200))
                .thenReturn(List.of(onlineOnlyUser, commonTagUser));

        PageResponse<UserRecommendationVO> result = userService.recommendUsers(
                1,
                10,
                request
        );

        assertEquals(2, result.getTotal());
        assertEquals(2L, result.getRecords().get(0).getUser().getId());
        assertEquals(List.of("Java"), result.getRecords().get(0).getCommonTags());
        assertTrue(result.getRecords().get(0).getReason().contains("Java"));
    }

    @Test
    void searchRejectsMoreThanThreeTags() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.searchUserByTags(
                        "",
                        List.of("Java", "跑步", "摄影", "电影"),
                        1,
                        10,
                        new MockHttpServletRequest()
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
    void adminCannotTogglePendingRegistrationFromUserList() {
        User admin = activeUser();
        admin.setUserRole(UserConstant.ADMIN_ROLE);
        User target = activeUser();
        target.setId(2L);
        target.setUserStatus(UserConstant.PENDING_REVIEW_STATUS);
        when(userMapper.selectById(1L)).thenReturn(admin);
        when(userMapper.selectById(2L)).thenReturn(target);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(
                        target.getId(),
                        UserConstant.NORMAL_STATUS,
                        loggedInRequest(admin.getId())
                )
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("待审核注册请在注册审核中处理", exception.getDescription());
        verify(userMapper, never()).updateById(any(User.class));
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
