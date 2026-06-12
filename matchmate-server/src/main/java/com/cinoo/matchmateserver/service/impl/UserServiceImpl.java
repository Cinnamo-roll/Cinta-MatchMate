package com.cinoo.matchmateserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cinoo.matchmateserver.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.cache.CacheKeys;
import com.cinoo.matchmateserver.cache.CacheNames;
import com.cinoo.matchmateserver.cache.DistributedCacheService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.common.PageResponse;
import com.cinoo.matchmateserver.constant.UserConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.UserMapper;
import com.cinoo.matchmateserver.model.domain.User;
import com.cinoo.matchmateserver.model.request.UpdateUserProfileRequest;
import com.cinoo.matchmateserver.model.vo.UserVO;
import com.cinoo.matchmateserver.service.PasswordService;
import com.cinoo.matchmateserver.service.TagService;
import com.cinoo.matchmateserver.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
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
    private static final int MAX_RECOMMENDATION_LIMIT = 50;
    private static final int MAX_SEARCH_TAGS = 3;
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,16}$");

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final TagService tagService;
    private final DistributedCacheService cacheService;
    private final CacheInvalidationService cacheInvalidationService;

    public UserServiceImpl(
            UserMapper userMapper,
            PasswordService passwordService,
            TagService tagService,
            DistributedCacheService cacheService,
            CacheInvalidationService cacheInvalidationService) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
        this.tagService = tagService;
        this.cacheService = cacheService;
        this.cacheInvalidationService = cacheInvalidationService;
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
        cacheInvalidationService.userCollectionChanged();
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

        saveLoginState(request, user.getId());
        return toUserVO(user);
    }

    @Override
    public UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        return cacheService.get(
                CacheNames.USER_VIEWS,
                CacheKeys.user(user.getId()),
                () -> buildUserVO(user)
        );
    }

    private UserVO buildUserVO(User user) {
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
        userVO.setUserTags(tagService.getUserTagNames(user.getId()));
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
        cacheInvalidationService.userDeleted(userId);
    }

    @Override
    public void userLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public List<UserVO> searchUserByTags(String keyword, List<String> tagList) {
        List<String> normalizedTags = normalizeTags(tagList);
        String normalizedKeyword = StringUtils.trimToNull(keyword);
        if (normalizedKeyword != null) {
            return loadSearchResults(normalizedKeyword, normalizedTags);
        }
        return cacheService.get(
                CacheNames.USER_SEARCHES,
                CacheKeys.search(normalizedTags),
                () -> loadSearchResults(null, normalizedTags)
        );
    }

    public List<UserVO> refreshSearchCache(List<String> tagList) {
        List<String> normalizedTags = normalizeTags(tagList);
        return cacheService.refresh(
                CacheNames.USER_SEARCHES,
                CacheKeys.search(normalizedTags),
                () -> loadSearchResults(null, normalizedTags)
        );
    }

    private List<UserVO> loadSearchResults(String keyword, List<String> normalizedTags) {
        return new ArrayList<>(userMapper.searchByKeywordAndTags(
                        keyword,
                        normalizedTags,
                        normalizedTags.size()
                ).stream()
                .map(this::toUserVO)
                .toList());
    }

    @Override
    public UserVO updateCurrentUserTags(List<String> tagList, HttpServletRequest request) {
        User user = getLoginUser(request);
        tagService.replaceUserTags(user.getId(), tagList);
        return toUserVO(user);
    }

    @Override
    public UserVO updateCurrentUserProfile(
            UpdateUserProfileRequest updateRequest,
            HttpServletRequest request) {
        User currentUser = getLoginUser(request);
        validateProfileUpdate(updateRequest);

        User updateUser = new User();
        updateUser.setId(currentUser.getId());
        updateUser.setUsername(StringUtils.trimToNull(updateRequest.getUsername()));
        updateUser.setAvatarUrl(StringUtils.trimToNull(updateRequest.getAvatarUrl()));
        updateUser.setGender(updateRequest.getGender());
        updateUser.setPhone(StringUtils.trimToNull(updateRequest.getPhone()));
        updateUser.setEmail(StringUtils.trimToNull(updateRequest.getEmail()));

        if (userMapper.updateById(updateUser) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户资料更新失败");
        }

        cacheInvalidationService.userChanged(currentUser.getId());
        User updatedUser = userMapper.selectById(currentUser.getId());
        return toUserVO(updatedUser);
    }

    @Override
    public void updateCurrentUserPassword(
            String currentPassword,
            String newPassword,
            String checkPassword,
            HttpServletRequest request) {
        User currentUser = getLoginUser(request);
        if (!passwordService.matches(currentPassword, currentUser.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前密码错误");
        }
        if (!Objects.equals(newPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次输入的新密码不一致");
        }
        if (Objects.equals(currentPassword, newPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码不能与当前密码相同");
        }

        User updateUser = new User();
        updateUser.setId(currentUser.getId());
        updateUser.setUserPassword(passwordService.encode(newPassword));
        if (userMapper.updateById(updateUser) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密码修改失败");
        }
    }

    // TODO: 后期改为基于标签匹配的智能推荐算法
    @Override
    public List<UserVO> recommendUsers(int limit) {
        validateRecommendationLimit(limit);
        return cacheService.get(
                CacheNames.USER_RECOMMENDATIONS,
                CacheKeys.recommendation(limit),
                () -> loadRecommendations(limit)
        );
    }

    public List<UserVO> refreshRecommendationCache(int limit) {
        validateRecommendationLimit(limit);
        return cacheService.refresh(
                CacheNames.USER_RECOMMENDATIONS,
                CacheKeys.recommendation(limit),
                () -> loadRecommendations(limit)
        );
    }

    private List<UserVO> loadRecommendations(int limit) {
        return new ArrayList<>(userMapper.recommendUsers(limit).stream()
                .map(this::toUserVO)
                .toList());
    }

    @Override
    public void deleteCurrentUser(String userPassword, HttpServletRequest request) {
        User currentUser = getLoginUser(request);
        if (!passwordService.matches(userPassword, currentUser.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码错误");
        }
        if (userMapper.deleteById(currentUser.getId()) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "账号注销失败");
        }
        cacheInvalidationService.userDeleted(currentUser.getId());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
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

    private void validateRecommendationLimit(int limit) {
        if (limit <= 0 || limit > MAX_RECOMMENDATION_LIMIT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "推荐数量必须为 1 到 50");
        }
    }

    private List<String> normalizeTags(List<String> tagList) {
        if (tagList == null) {
            return List.of();
        }
        List<String> normalizedTags = tagList.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedTags.size() > MAX_SEARCH_TAGS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "最多选择 3 个标签");
        }
        return normalizedTags;
    }

    private void validateProfileUpdate(UpdateUserProfileRequest updateRequest) {
        if (updateRequest.getGender() != null
                && updateRequest.getGender() != 1
                && updateRequest.getGender() != 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "性别只能为男或女");
        }
    }
}
