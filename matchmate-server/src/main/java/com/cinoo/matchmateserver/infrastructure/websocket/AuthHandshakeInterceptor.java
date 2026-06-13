package com.cinoo.matchmateserver.infrastructure.websocket;

import com.cinoo.matchmateserver.user.constant.UserConstant;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器，从 HttpSession 中获取登录用户 ID。
 */
@Component
@Slf4j
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session == null) {
                log.warn("WebSocket handshake rejected: no HttpSession");
                return false;
            }
            Object loginState = session.getAttribute(UserConstant.USER_LOGIN_STATE);
            if (loginState instanceof Long userId) {
                attributes.put("userId", userId);
                return true;
            }
            log.warn("WebSocket handshake rejected: not logged in");
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing
    }
}
