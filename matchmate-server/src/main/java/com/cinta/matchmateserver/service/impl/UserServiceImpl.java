package com.cinta.matchmateserver.service.impl;

import java.util.Date;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cinta.matchmateserver.contant.UserConstant;
import com.cinta.matchmateserver.model.domain.User;
import com.cinta.matchmateserver.service.UserService;
import com.cinta.matchmateserver.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * @author CinnamoRoll
 * @description UserService实现
 * @createDate 2026-06-10 15:28:16
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String SALT = "cinta";

    @Resource
    private UserMapper userMapper;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // TODO 修改为自定义异常
        // 账户不能为空
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            return -1;
        }
        // 账户长度小于4
        if (userAccount.length() < 4) {
            return -1;
        }
        // 密码长度小于8
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            return -1;
        }
        // 只允许字母和数字的用户名
        String validate = "^[a-zA-Z0-9]{4,16}$";
        if (!userAccount.matches(validate)) {
            return -1;
        }
        // 密码和校验密码不相同
        if (!userPassword.equals(checkPassword)) {
            return -1;
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            return -1;
        }

        // 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            return -1;
        }
        return user.getId();
    }

    @Override
    public User doLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 账户不能为空
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return null;
        }
        // 账户长度小于4
        if (userAccount.length() < 4) {
            return null;
        }
        // 密码长度小于8
        if (userPassword.length() < 8) {
            return null;
        }
        // 只允许字母和数字的用户名
        String validate = "^[a-zA-Z0-9]{4,16}$";
        if (!userAccount.matches(validate)) {
            return null;
        }
        // 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("Users do not exist");
            return null;
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
        safetyUser.setCreateTime(new Date());
        safetyUser.setUserRole(originUser.getUserRole());
        return safetyUser;
    }
}




