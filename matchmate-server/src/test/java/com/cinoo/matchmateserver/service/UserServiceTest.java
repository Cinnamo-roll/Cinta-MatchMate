package com.cinoo.matchmateserver.service;

import com.cinoo.matchmateserver.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.cache.CacheNames;
import com.cinoo.matchmateserver.cache.DistributedCacheService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.constant.UserConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.UserMapper;
import com.cinoo.matchmateserver.model.domain.User;
import com.cinoo.matchmateserver.model.request.UpdateUserProfileRequest;
import com.cinoo.matchmateserver.model.vo.UserVO;
import com.cinoo.matchmateserver.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
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
                cacheInvalidationService
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
        assertTrue(passwordService.matches(PASSWORD, captor.getValue().getUserPassword()));
        verify(cacheInvalidationService).userCollectionChanged();
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

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount(ACCOUNT);
        user.setUserStatus(0);
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        return user;
    }
}
