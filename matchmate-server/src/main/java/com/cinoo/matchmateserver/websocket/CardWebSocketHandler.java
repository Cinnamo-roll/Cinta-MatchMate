package com.cinoo.matchmateserver.websocket;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 打牌房间 WebSocket 推送。
 * 只负责结果通知，不修改核心数据。
 */
@Component
@Slf4j
public class CardWebSocketHandler extends TextWebSocketHandler {

    /** roomId → 在线 session 集合 */
    private final Map<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    /** sessionId → roomId（用于关闭时清理） */
    private final Map<String, Long> sessionRoomMap = new ConcurrentHashMap<>();
    /** sessionId → userId */
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    // ── 事件类型常量 ──
    public static final String EVENT_MEMBER_JOINED = "card_room_member_joined";
    public static final String EVENT_MEMBER_LEFT   = "card_room_member_left";
    public static final String EVENT_ROUND_CREATED = "card_room_round_created";
    public static final String EVENT_FUND_CREATED = "card_room_fund_created";
    public static final String EVENT_ROOM_CLOSED   = "card_room_closed";

    public CardWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        Long roomId = getRoomId(session);
        if (userId == null || roomId == null) {
            try { session.close(); } catch (Exception ignored) {}
            return;
        }
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRoomMap.put(session.getId(), roomId);
        sessionUserMap.put(session.getId(), userId);
        log.info("Card WS connected: userId={}, roomId={}, sessionId={}", userId, roomId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = sessionRoomMap.remove(session.getId());
        sessionUserMap.remove(session.getId());
        if (roomId != null) {
            Set<WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) roomSessions.remove(roomId);
            }
        }
        log.info("Card WS disconnected: sessionId={}", session.getId());
    }

    /**
     * 推送事件到房间。excludeUserId 为事件发起者，不会被推送。
     * 推送失败只记日志，不影响调用方事务。
     */
    public void pushEvent(Long roomId, Long excludeUserId, String type, Object data) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) return;

        // 构造统一载荷：{ type, roomId, data }
        PushPayload payload = new PushPayload(type, roomId, data);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize card WS payload type={} roomId={}", type, roomId, e);
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            Long uid = sessionUserMap.get(session.getId());
            if (excludeUserId != null && excludeUserId.equals(uid)) continue;
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("Failed to push to session {} in room {}", session.getId(), roomId, e);
            }
        }
    }

    /** 仅测试用：当前房间在线 session 数 */
    int onlineCount(Long roomId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions == null ? 0 : sessions.size();
    }

    private Long getUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get("userId");
        return attr instanceof Long ? (Long) attr : null;
    }

    private Long getRoomId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        try {
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    /** 统一推送载荷 */
    public record PushPayload(String type, Long roomId, Object data) {}
}
