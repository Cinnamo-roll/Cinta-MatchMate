package com.cinta.matchmateserver.service;

import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.constant.UserConstant;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.mapper.UserMapper;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.model.vo.UserVO;
import com.cinta.matchmateserver.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String ACCOUNT = "testuser";
    private static final String PASSWORD = "12345678";

    @Mock
    private UserMapper userMapper;

    private PasswordService passwordService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        userService = new UserServiceImpl(userMapper, passwordService, new ObjectMapper());
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
    void malformedTagsAreIgnored() {
        User malformed = activeUser();
        malformed.setUserTags("not-json");
        when(userMapper.selectList(any())).thenReturn(List.of(malformed));

        List<UserVO> result = userService.searchUserByTags(List.of("Java"));

        assertTrue(result.isEmpty());
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
