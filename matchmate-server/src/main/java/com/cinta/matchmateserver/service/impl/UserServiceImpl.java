package com.cinta.matchmateserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.common.PageResponse;
import com.cinta.matchmateserver.constant.UserConstant;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.mapper.UserMapper;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.model.vo.UserVO;
import com.cinta.matchmateserver.service.PasswordService;
import com.cinta.matchmateserver.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户业务服务实现。
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final int NORMAL_USER_STATUS = 0;
    private static final int MIN_ACCOUNT_LENGTH = 4;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final long MAX_PAGE_SIZE = 100;
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,16}$");

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final ObjectMapper objectMapper;

    public UserServiceImpl(UserMapper userMapper, PasswordService passwordService, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
        this.objectMapper = objectMapper;
    }

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        validateAccountAndPassword(userAccount, userPassword);
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码不相同");
        }

        if (accountExists(userAccount)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已存在");
        }

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(passwordService.encode(userPassword));

        try {
            int insertedRows = userMapper.insert(user);
            if (insertedRows != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已存在");
        }
        return user.getId();
    }

    @Override
    public UserVO doLogin(String userAccount, String userPassword, HttpServletRequest request) {
        validateAccountAndPassword(userAccount, userPassword);

        User user = findByAccount(userAccount);
        if (user == null || !passwordService.matches(userPassword, user.getUserPassword())) {
            log.info("User login failed, account={}", userAccount);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在或密码错误");
        }
        if (!isActive(user)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "用户状态异常");
        }

        upgradePasswordIfNecessary(user, userPassword);
        saveLoginState(request, user.getId());
        return toUserVO(user);
    }

    @Override
    public UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setUserAccount(user.getUserAccount());
        userVO.setAvatarUrl(user.getAvatarUrl());
        userVO.setGender(user.getGender());
        userVO.setPhone(user.getPhone());
        userVO.setEmail(user.getEmail());
        userVO.setUserStatus(user.getUserStatus());
        userVO.setCreateTime(user.getCreateTime());
        userVO.setUserRole(user.getUserRole());
        userVO.setUserTags(user.getUserTags());
        return userVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = getLoginUserId(session);
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        User user = userMapper.selectById(userId);
        if (user == null || !isActive(user)) {
            session.removeAttribute(UserConstant.USER_LOGIN_STATE);
            throw new BusinessException(ErrorCode.NOT_LOGIN, "登录状态已失效");
        }
        return user;
    }

    @Override
    public boolean isAdmin(HttpServletRequest request) {
        return Objects.equals(getLoginUser(request).getUserRole(), UserConstant.ADMIN_ROLE);
    }

    @Override
    public PageResponse<UserVO> searchUsers(String username, long pageNum, long pageSize) {
        validatePageParameters(pageNum, pageSize);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotBlank(username), User::getUsername, username)
                .orderByDesc(User::getCreateTime);

        Page<User> userPage = userMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);
        List<UserVO> records = userPage.getRecords().stream()
                .map(this::toUserVO)
                .toList();
        return new PageResponse<>(userPage.getTotal(), userPage.getCurrent(), userPage.getSize(), records);
    }

    @Override
    public void deleteUser(long userId) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户 id 不合法");
        }
        if (userMapper.deleteById(userId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    @Override
    public void userLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public List<UserVO> searchUserByTags(List<String> tagList) {
        if (CollectionUtils.isEmpty(tagList)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<String> normalizedTags = normalizeTags(tagList);

        // LIKE 仅用于缩小候选集；随后解析 JSON 并精确判断，避免子串误匹配。
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        normalizedTags.forEach(tag -> queryWrapper.like(User::getUserTags, tag));

        return userMapper.selectList(queryWrapper).stream()
                .filter(user -> containsAllTags(user, normalizedTags))
                .map(this::toUserVO)
                .toList();
    }

    private void validateAccountAndPassword(String userAccount, String userPassword) {
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账号和密码不能为空");
        }
        if (userAccount.length() < MIN_ACCOUNT_LENGTH || !ACCOUNT_PATTERN.matcher(userAccount).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账号只能包含 4 到 16 位字母或数字");
        }
        if (userPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度不能少于 8 位");
        }
    }

    private boolean accountExists(String userAccount) {
        return userMapper.selectCount(accountQuery(userAccount)) > 0;
    }

    private User findByAccount(String userAccount) {
        return userMapper.selectOne(accountQuery(userAccount));
    }

    private LambdaQueryWrapper<User> accountQuery(String userAccount) {
        return new LambdaQueryWrapper<User>().eq(User::getUserAccount, userAccount);
    }

    private boolean isActive(User user) {
        return Objects.equals(user.getUserStatus(), NORMAL_USER_STATUS);
    }

    /**
     * 旧账号仍可能保存 SHA-256 密码。验证成功后立即升级为 BCrypt，
     * 可以在不中断已有用户登录的情况下逐步完成密码迁移。
     */
    private void upgradePasswordIfNecessary(User user, String rawPassword) {
        if (!passwordService.needsUpgrade(user.getUserPassword())) {
            return;
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserPassword(passwordService.encode(rawPassword));
        userMapper.updateById(updateUser);
    }

    /**
     * 登录成功后更换 Session ID，降低 Session 固定攻击风险。
     */
    private void saveLoginState(HttpServletRequest request, Long userId) {
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, userId);
    }

    private Long getLoginUserId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object loginState = session.getAttribute(UserConstant.USER_LOGIN_STATE);
        return loginState instanceof Long userId ? userId : null;
    }

    private void validatePageParameters(long pageNum, long pageSize) {
        if (pageNum <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分页参数不合法");
        }
    }

    private List<String> normalizeTags(List<String> tagList) {
        List<String> normalizedTags = tagList.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedTags.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "标签不能为空");
        }
        return normalizedTags;
    }

    private boolean containsAllTags(User user, List<String> requiredTags) {
        if (StringUtils.isBlank(user.getUserTags())) {
            return false;
        }
        try {
            Set<String> userTags = objectMapper.readValue(
                    user.getUserTags(),
                    new TypeReference<Set<String>>() {
                    }
            );
            return userTags != null && userTags.containsAll(requiredTags);
        } catch (JacksonException e) {
            log.warn("Ignoring malformed tags for userId={}", user.getId());
            return false;
        }
    }
}
