package com.cinta.matchmateserver.service;
import java.util.Date;

import com.cinta.matchmateserver.model.domain.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * @author CinnamoRoll
 * @description 针对表【user(用户表)】的数据库操作Service测试用例
 * @createDate 2026-06-10 15:28:16
 */
@SpringBootTest
class UserServiceTest {

    @Resource
    private UserService userService;

    @Test
    void testInsert() {
        User user = new User();
        user.setUsername("testName");
        user.setUserAccount("123");
        user.setAvatarUrl("https://baomidou.com/assets/asset.cIbiVTt_.svg");
        user.setGender(0);
        user.setUserPassword("12345");
        user.setPhone("13125200421");
        user.setEmail("1391571546@qq.com");
        boolean result = userService.save(user);
        System.out.println("测试用户id：" + user.getId());
        Assertions.assertTrue(result);
    }

    @Test
    void userRegister() {
        String userAccount = "user";
        String userPassword = "1234567";
        String checkPassword = "1234567";
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        Assertions.assertEquals(-1, result);
    }
}