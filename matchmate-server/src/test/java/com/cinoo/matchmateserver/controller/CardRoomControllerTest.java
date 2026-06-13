package com.cinoo.matchmateserver.controller;

import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.model.request.JoinRoomRequest;
import com.cinoo.matchmateserver.service.CardRoomService;
import com.cinoo.matchmateserver.websocket.AuthHandshakeInterceptor;
import com.cinoo.matchmateserver.websocket.CardWebSocketHandler;
import com.cinoo.matchmateserver.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardRoomController 集成测试 —— 验证 Controller Bean 正确注入和未登录拦截。
 */
@SpringBootTest(
        properties = {
                "matchmate.cache.enabled=false",
                "matchmate.cache.warmup.enabled=false",
                "spring.cache.type=simple",
                "spring.session.store-type=none",
                "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                "DB_URL=jdbc:mysql://localhost:3306/matchmate",
                "DB_USERNAME=root",
                "DB_PASSWORD=1234"
        }
)
class CardRoomControllerTest {

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private CardWebSocketHandler cardWebSocketHandler;

    @MockitoBean
    private ChatWebSocketHandler chatWebSocketHandler;

    @MockitoBean
    private AuthHandshakeInterceptor authHandshakeInterceptor;

    @Autowired
    private CardRoomController controller;

    @Autowired
    private CardRoomService cardRoomService;

    @Test
    void contextLoads() {
        assertNotNull(controller);
        assertNotNull(cardRoomService);
    }

    @Test
    void createRoom_notLoggedIn_shouldThrow() {
        assertThrows(BusinessException.class, () ->
                controller.createRoom(new MockHttpServletRequest()));
    }

    @Test
    void joinRoom_notLoggedIn_shouldThrow() {
        JoinRoomRequest req = new JoinRoomRequest();
        req.setRoomCode("123456");
        assertThrows(BusinessException.class, () ->
                controller.joinRoom(req, new MockHttpServletRequest()));
    }

    @Test
    void getRoomDetail_notLoggedIn_shouldThrow() {
        assertThrows(BusinessException.class, () ->
                controller.getRoomDetail(1L, new MockHttpServletRequest()));
    }

    @Test
    void activeRoom_notLoggedIn_shouldThrow() {
        // getActiveRoom 内部调用 loginUser，无 session 抛异常
        assertThrows(BusinessException.class, () ->
                controller.getActiveRoom(new MockHttpServletRequest()));
    }

    @Test
    void history_notLoggedIn_shouldThrow() {
        assertThrows(BusinessException.class, () ->
                controller.getHistory(10, new MockHttpServletRequest()));
    }

    @Test
    void ranking_notLoggedIn_shouldThrow() {
        assertThrows(BusinessException.class, () ->
                controller.getRanking(5, new MockHttpServletRequest()));
    }
}
