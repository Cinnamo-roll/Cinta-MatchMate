package com.cinoo.matchmateserver.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理 WebSocket 连接：userId → WebSocketSession 映射。
 * 一个用户只保留一个连接，旧连接会被关闭。
 */
@Component
@Slf4j
public class WebSocketSessionManager {

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        WebSocketSession oldSession = sessions.put(userId, session);
        if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(session.getId())) {
            try {
                oldSession.close();
            } catch (Exception e) {
                log.warn("Failed to close old WebSocket session for user {}", userId, e);
            }
        }
        log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
    }

    public boolean remove(Long userId, WebSocketSession session) {
        boolean removed = sessions.remove(userId, session);
        if (removed) {
            log.info("WebSocket disconnected: userId={}, sessionId={}", userId, session.getId());
        }
        return removed;
    }

    public WebSocketSession getSession(Long userId) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            return session;
        }
        sessions.remove(userId);
        return null;
    }

    public boolean isOnline(Long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    public void disconnect(Long userId) {
        WebSocketSession session = getSession(userId);
        if (session == null) {
            return;
        }
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception e) {
            log.warn("Failed to close WebSocket session for user {}", userId, e);
        }
    }
}
