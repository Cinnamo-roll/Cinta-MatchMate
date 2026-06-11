package com.cinta.matchmateserver.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.constant.UserConstant;
import com.cinta.matchmateserver.exception.BusinessException;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.service.UserService;
import com.cinta.matchmateserver.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * @author CinnamoRoll
 * @description UserService实现
 * @createDate 2026-06-10 15:28:16
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * TODO: 后续升级为 BCryptPasswordEncoder，使用更安全的自适应哈希算法
     */
    private static final String SALT = "cinta";

    @Resource
    private UserMapper userMapper;

    /**
     * 使用 SHA-256 对密码加盐哈希
     */
    private String encryptPassword(String userPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((SALT + userPassword).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加密算法不可用");
        }
    }

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 账户不能为空
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账户不能为空");
        }
        // 账户长度小于4
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账户长度小于4");
        }
        // 密码长度小于8
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度小于8");
        }
        // 只允许字母和数字的用户名
        String validate = "^[a-zA-Z0-9]{4,16}$";
        if (!userAccount.matches(validate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只允许字母和数字的用户名");
        }
        // 密码和校验密码不相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码不相同");
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已存在");
        }

        // 加密
        String encryptPassword = encryptPassword(userPassword);

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
        }
        return user.getId();
    }

    @Override
    public User doLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 账户不能为空
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账户不能为空");
        }
        // 账户长度小于4
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账户长度小于4");
        }
        // 密码长度小于8
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度小于8");
        }
        // 只允许字母和数字的用户名
        String validate = "^[a-zA-Z0-9]{4,16}$";
        if (!userAccount.matches(validate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只允许字母和数字的用户名");
        }
        // 加密
        String encryptPassword = encryptPassword(userPassword);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("Users do not exist");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在或密码错误");
        }

        // 脱敏
        User safetyUser = getSafetyUser(user);

        // 登录成功，记录用户登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, safetyUser);

        return safetyUser;
    }

    @Override
    public User getSafetyUser(User originUser) {
        User safetyUser = new User();
        safetyUser.setId(originUser.getId());
        safetyUser.setUsername(originUser.getUsername());
        safetyUser.setUserAccount(originUser.getUserAccount());
        safetyUser.setAvatarUrl(originUser.getAvatarUrl());
        safetyUser.setGender(originUser.getGender());
        safetyUser.setPhone(originUser.getPhone());
        safetyUser.setEmail(originUser.getEmail());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setUserRole(originUser.getUserRole());
        return safetyUser;
    }

    @Override
    public void userLogout(HttpServletRequest request) {
       request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
    }
}