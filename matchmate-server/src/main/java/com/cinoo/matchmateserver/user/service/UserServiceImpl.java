package com.cinoo.matchmateserver.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cinoo.matchmateserver.infrastructure.cache.CacheInvalidationService;
import com.cinoo.matchmateserver.infrastructure.cache.CacheKeys;
import com.cinoo.matchmateserver.infrastructure.cache.CacheNames;
import com.cinoo.matchmateserver.infrastructure.cache.DistributedCacheService;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.common.PageResponse;
import com.cinoo.matchmateserver.user.constant.UserConstant;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.user.mapper.UserMapper;
import com.cinoo.matchmateserver.user.model.entity.User;
import com.cinoo.matchmateserver.user.model.request.UpdateUserProfileRequest;
import com.cinoo.matchmateserver.user.model.vo.RegistrationPolicyVO;
import com.cinoo.matchmateserver.user.model.vo.UserRecommendationVO;
import com.cinoo.matchmateserver.user.model.vo.UserRegisterResultVO;
import com.cinoo.matchmateserver.user.model.vo.UserVO;
import com.cinoo.matchmateserver.user.service.PasswordService;
import com.cinoo.matchmateserver.tag.service.TagService;
import com.cinoo.matchmateserver.user.service.UserService;
import com.cinoo.matchmateserver.chat.service.OnlineUserService;
import com.cinoo.matchmateserver.infrastructure.oss.OssUtils;
import com.cinoo.matchmateserver.chat.websocket.ChatWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户业务服务实现。
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final int MIN_ACCOUNT_LENGTH = 4;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final long MAX_PAGE_SIZE = 100;
    private static final int MAX_RECOMMENDATION_CANDIDATES = 200;
    private static final int MAX_SEARCH_TAGS = 3;
    private static final int DEFAULT_DAILY_REGISTRATION_LIMIT = 20;
    private static final String REGISTRATION_DAILY_LIMIT_KEY = "registration.daily.limit";
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,16}$");

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final TagService tagService;
    private final DistributedCacheService cacheService;
    private final CacheInvalidationService cacheInvalidationService;
    private final OssUtils ossUtils;
    private final OnlineUserService onlineUserService;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public UserServiceImpl(
            UserMapper userMapper,
            PasswordService passwordService,
            TagService tagService,
            DistributedCacheService cacheService,
            CacheInvalidationService cacheInvalidationService,
            OssUtils ossUtils,
            OnlineUserService onlineUserService,
            ChatWebSocketHandler chatWebSocketHandler) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
        this.tagService = tagService;
        this.cacheService = cacheService;
        this.cacheInvalidationService = cacheInvalidationService;
        this.ossUtils = ossUtils;
        this.onlineUserService = onlineUserService;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public UserRegisterResultVO userRegister(String userAccount, String userPassword, String checkPassword) {
        String normalizedAccount = normalizeAccount(userAccount);
        validateAccountAndPassword(normalizedAccount, userPassword);
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码不相同");
        }

        if (accountExists(normalizedAccount)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已存在");
        }

        User user = new User();
        user.setUserAccount(normalizedAccount);
        user.setUsername(normalizedAccount);
        user.setGender(UserConstant.DEFAULT_GENDER);
        user.setUserPassword(passwordService.encode(userPassword));
        boolean pendingReview = shouldEnterRegistrationReview();
        user.setUserStatus(pendingReview
                ? UserConstant.PENDING_REVIEW_STATUS
                : UserConstant.NORMAL_STATUS);

        try {
            int insertedRows = userMapper.insert(user);
            if (insertedRows != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已存在");
        }
        cacheInvalidationService.userCollectionChanged();
        String message = pendingReview
                ? "今日注册名额已满，申请已提交给管理员审核"
                : "注册成功";
        return new UserRegisterResultVO(user.getId(), pendingReview, message);
    }

    @Override
    public UserVO doLogin(String userAccount, String userPassword, HttpServletRequest request) {
        String normalizedAccount = normalizeAccount(userAccount);
        validateAccountAndPassword(normalizedAccount, userPassword);

        User user = findByAccount(normalizedAccount);
        if (user == null || !passwordService.matches(userPassword, user.getUserPassword())) {
            log.info("User login failed, account={}", normalizedAccount);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在或密码错误");
        }
        if (Objects.equals(user.getUserStatus(), UserConstant.PENDING_REVIEW_STATUS)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "注册申请正在等待管理员审核");
        }
        if (!isActive(user)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "账号已被封禁，请联系管理员");
        }

        saveLoginState(request, user.getId());
        return toUserVO(user);
    }

    @Override
    public UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = cacheService.get(
                CacheNames.USER_VIEWS,
                CacheKeys.user(user.getId()),
                () -> buildUserVO(user)
        );
        userVO.setWinRate(calculateWinRate(userVO.getWins(), userVO.getLosses()));
        userVO.setIsOnline(onlineUserService.isOnline(user.getId()));
        return userVO;
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
        userVO.setIsOnline(onlineUserService.isOnline(user.getId()));
        userVO.setTotalScore(user.getTotalScore());
        userVO.setWins(user.getWins());
        userVO.setLosses(user.getLosses());
        userVO.setWinRate(calculateWinRate(user.getWins(), user.getLosses()));
        return userVO;
    }

    private BigDecimal calculateWinRate(Integer wins, Integer losses) {
        int winCount = Objects.requireNonNullElse(wins, 0);
        int lossCount = Objects.requireNonNullElse(losses, 0);
        int total = winCount + lossCount;
        if (total == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(winCount)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
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
            String description = user == null
                    ? "登录状态已失效"
                    : "账号已被封禁，请联系管理员";
            throw new BusinessException(ErrorCode.NOT_LOGIN, description);
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
        queryWrapper.and(
                        StringUtils.isNotBlank(username),
                        query -> query.like(User::getUsername, username)
                                .or()
                                .like(User::getUserAccount, username)
                )
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
    public void updateUserStatus(long userId, int userStatus, HttpServletRequest request) {
        User admin = getLoginUser(request);
        if (!Objects.equals(admin.getUserRole(), UserConstant.ADMIN_ROLE)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        if (userId <= 0
                || (userStatus != UserConstant.NORMAL_STATUS
                && userStatus != UserConstant.BANNED_STATUS)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户状态参数不合法");
        }
        if (Objects.equals(admin.getId(), userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能修改自己的账号状态");
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null || Objects.equals(targetUser.getIsDelete(), 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (Objects.equals(targetUser.getUserRole(), UserConstant.ADMIN_ROLE)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "不能修改管理员账号状态");
        }
        if (Objects.equals(targetUser.getUserStatus(), UserConstant.PENDING_REVIEW_STATUS)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "待审核注册请在注册审核中处理");
        }
        if (Objects.equals(targetUser.getUserStatus(), userStatus)) {
            return;
        }

        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setUserStatus(userStatus);
        if (userMapper.updateById(updateUser) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户状态更新失败");
        }
        cacheInvalidationService.userChanged(userId);
        if (userStatus == UserConstant.BANNED_STATUS) {
            chatWebSocketHandler.pushAccountBannedAndDisconnect(
                    userId,
                    "你已被管理员封禁，已强制退出登录"
            );
            try {
                onlineUserService.userOffline(userId);
            } catch (RuntimeException e) {
                log.warn("Failed to clear online state for banned userId={}", userId, e);
            }
        }
    }

    @Override
    public RegistrationPolicyVO getRegistrationPolicy(HttpServletRequest request) {
        requireAdmin(request);
        return buildRegistrationPolicy();
    }

    @Override
    public RegistrationPolicyVO updateRegistrationDailyLimit(
            int dailyLimit,
            HttpServletRequest request) {
        requireAdmin(request);
        if (dailyLimit < 0 || dailyLimit > 1000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "每日注册限额不合法");
        }
        userMapper.upsertAppSetting(
                REGISTRATION_DAILY_LIMIT_KEY,
                String.valueOf(dailyLimit)
        );
        return buildRegistrationPolicy();
    }

    @Override
    public PageResponse<UserVO> listPendingRegistrations(
            long pageNum,
            long pageSize,
            HttpServletRequest request) {
        requireAdmin(request);
        validatePageParameters(pageNum, pageSize);
        Page<User> userPage = userMapper.selectUsersByStatus(
                new Page<>(pageNum, pageSize),
                UserConstant.PENDING_REVIEW_STATUS
        );
        List<UserVO> records = userPage.getRecords().stream()
                .map(this::toUserVO)
                .toList();
        return new PageResponse<>(
                userPage.getTotal(),
                userPage.getCurrent(),
                userPage.getSize(),
                records
        );
    }

    @Override
    public void approveRegistration(long userId, HttpServletRequest request) {
        requireAdmin(request);
        User pendingUser = getPendingRegistrationUser(userId);
        User updateUser = new User();
        updateUser.setId(pendingUser.getId());
        updateUser.setUserStatus(UserConstant.NORMAL_STATUS);
        if (userMapper.updateById(updateUser) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册审核通过失败");
        }
        cacheInvalidationService.userChanged(userId);
    }

    @Override
    public void rejectRegistration(long userId, HttpServletRequest request) {
        requireAdmin(request);
        User pendingUser = getPendingRegistrationUser(userId);
        if (userMapper.deleteById(pendingUser.getId()) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册申请拒绝失败");
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
    public PageResponse<UserVO> searchUserByTags(
            String keyword,
            List<String> tagList,
            long pageNum,
            long pageSize,
            HttpServletRequest request) {
        validatePageParameters(pageNum, pageSize);
        List<String> normalizedTags = normalizeTags(tagList);
        String normalizedKeyword = StringUtils.trimToNull(keyword);
        Long currentUserId = getLoginUserId(
                request == null ? null : request.getSession(false)
        );
        Page<User> userPage = userMapper.searchPageByKeywordAndTags(
                new Page<>(pageNum, pageSize),
                normalizedKeyword,
                normalizedTags,
                normalizedTags.size(),
                currentUserId,
                UserConstant.ADMIN_ROLE
        );
        List<UserVO> records = userPage.getRecords().stream()
                .map(this::toUserVO)
                .toList();
        return new PageResponse<>(
                userPage.getTotal(),
                userPage.getCurrent(),
                userPage.getSize(),
                records
        );
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

    @Override
    public PageResponse<UserRecommendationVO> recommendUsers(
            long pageNum,
            long pageSize,
            HttpServletRequest request) {
        validatePageParameters(pageNum, pageSize);
        Long currentUserId = getLoginUserId(
                request == null ? null : request.getSession(false)
        );
        Set<String> currentUserTags = currentUserId == null
                ? Set.of()
                : new HashSet<>(tagService.getUserTagNames(currentUserId));

        List<RecommendationCandidate> candidates = userMapper
                .selectRecommendationCandidates(
                        currentUserId,
                        MAX_RECOMMENDATION_CANDIDATES
                )
                .stream()
                .map(this::toUserVO)
                .map(user -> scoreRecommendation(user, currentUserTags))
                .sorted(
                        Comparator.comparingInt(RecommendationCandidate::score)
                                .reversed()
                                .thenComparing(
                                        candidate -> candidate.commonTags().size(),
                                        Comparator.reverseOrder()
                                )
                                .thenComparing(
                                        candidate -> Boolean.TRUE.equals(
                                                candidate.user().getIsOnline()
                                        ),
                                        Comparator.reverseOrder()
                                )
                                .thenComparing(candidate -> candidate.user().getId())
                )
                .toList();

        long offset = (pageNum - 1) * pageSize;
        if (offset >= candidates.size()) {
            return new PageResponse<>(
                    candidates.size(),
                    pageNum,
                    pageSize,
                    List.of()
            );
        }
        int fromIndex = Math.toIntExact(offset);
        int toIndex = (int) Math.min(offset + pageSize, candidates.size());
        List<UserRecommendationVO> records = candidates
                .subList(fromIndex, toIndex)
                .stream()
                .map(candidate -> new UserRecommendationVO(
                        candidate.user(),
                        candidate.score(),
                        candidate.reason(),
                        candidate.commonTags()
                ))
                .toList();
        return new PageResponse<>(
                candidates.size(),
                pageNum,
                pageSize,
                records
        );
    }

    private RecommendationCandidate scoreRecommendation(
            UserVO user,
            Set<String> currentUserTags) {
        List<String> userTags = Objects.requireNonNullElse(
                user.getUserTags(),
                List.of()
        );
        List<String> commonTags = userTags.stream()
                .filter(currentUserTags::contains)
                .distinct()
                .limit(3)
                .toList();

        int score = commonTags.size() * 30;
        score += Boolean.TRUE.equals(user.getIsOnline()) ? 15 : 0;
        score += StringUtils.isNotBlank(user.getAvatarUrl()) ? 5 : 0;
        score += StringUtils.isNotBlank(user.getUsername()) ? 3 : 0;
        score += Math.min(userTags.size(), 3) * 2;

        String reason;
        if (!commonTags.isEmpty()) {
            reason = "你们都喜欢：" + String.join("、", commonTags);
        } else if (Boolean.TRUE.equals(user.getIsOnline())) {
            reason = "当前在线，可以马上聊聊";
        } else if (!userTags.isEmpty()) {
            reason = "兴趣标签丰富，值得认识";
        } else {
            reason = "新伙伴，来打个招呼吧";
        }
        return new RecommendationCandidate(user, score, reason, commonTags);
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

    @Override
    public UserVO uploadAvatar(MultipartFile file, HttpServletRequest request) {
        User currentUser = getLoginUser(request);
        String oldAvatarUrl = currentUser.getAvatarUrl();
        String newAvatarUrl = ossUtils.uploadAvatar(currentUser.getId(), file);

        User updateUser = new User();
        updateUser.setId(currentUser.getId());
        updateUser.setAvatarUrl(newAvatarUrl);
        try {
            if (userMapper.updateById(updateUser) != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像更新失败");
            }
        } catch (RuntimeException e) {
            ossUtils.deleteIfManaged(newAvatarUrl);
            throw e;
        }

        cacheInvalidationService.userChanged(currentUser.getId());
        ossUtils.deleteIfManaged(oldAvatarUrl);
        User updatedUser = userMapper.selectById(currentUser.getId());
        return toUserVO(updatedUser);
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

    private boolean shouldEnterRegistrationReview() {
        int dailyLimit = getRegistrationDailyLimit();
        if (dailyLimit <= 0) {
            return true;
        }
        return countApprovedRegistrationsToday() >= dailyLimit;
    }

    private RegistrationPolicyVO buildRegistrationPolicy() {
        return new RegistrationPolicyVO(
                getRegistrationDailyLimit(),
                countApprovedRegistrationsToday(),
                userMapper.countUsersByStatus(UserConstant.PENDING_REVIEW_STATUS)
        );
    }

    private int getRegistrationDailyLimit() {
        Integer dailyLimit = userMapper.selectAppSettingInt(REGISTRATION_DAILY_LIMIT_KEY);
        return dailyLimit == null ? DEFAULT_DAILY_REGISTRATION_LIMIT : Math.max(0, dailyLimit);
    }

    private long countApprovedRegistrationsToday() {
        RegistrationDayRange range = todayRange();
        Long count = userMapper.countRegistrationsByStatusAndTimeRange(
                UserConstant.NORMAL_STATUS,
                range.startTime(),
                range.endTime()
        );
        return count == null ? 0L : count;
    }

    private RegistrationDayRange todayRange() {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        Date startTime = Date.from(today.atStartOfDay(zoneId).toInstant());
        Date endTime = Date.from(today.plusDays(1).atStartOfDay(zoneId).toInstant());
        return new RegistrationDayRange(startTime, endTime);
    }

    private User requireAdmin(HttpServletRequest request) {
        User admin = getLoginUser(request);
        if (!Objects.equals(admin.getUserRole(), UserConstant.ADMIN_ROLE)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return admin;
    }

    private User getPendingRegistrationUser(long userId) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户 id 不合法");
        }
        User user = userMapper.selectById(userId);
        if (user == null || Objects.equals(user.getIsDelete(), 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "注册申请不存在");
        }
        if (!Objects.equals(user.getUserStatus(), UserConstant.PENDING_REVIEW_STATUS)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该用户不在注册审核中");
        }
        return user;
    }

    private User findByAccount(String userAccount) {
        return userMapper.selectOne(accountQuery(userAccount));
    }

    private LambdaQueryWrapper<User> accountQuery(String userAccount) {
        return new LambdaQueryWrapper<User>().apply("LOWER(userAccount) = {0}", userAccount);
    }

    private String normalizeAccount(String userAccount) {
        return StringUtils.trimToEmpty(userAccount).toLowerCase(Locale.ROOT);
    }

    private boolean isActive(User user) {
        return Objects.equals(user.getUserStatus(), UserConstant.NORMAL_STATUS);
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

    private record RecommendationCandidate(
            UserVO user,
            int score,
            String reason,
            List<String> commonTags) {
    }

    private record RegistrationDayRange(Date startTime, Date endTime) {
    }
}
