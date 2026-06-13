package com.cinoo.matchmateserver.card.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CardWebSocketHandler extends TextWebSocketHandler {

    private final Map<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionRoomMap = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public CardWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        Long roomId = getRoomId(session);
        if (userId == null || roomId == null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
            return;
        }
        roomSessions.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
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

    public void pushEvent(Long roomId, Long excludeUserId, String type, Object data) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) return;

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
            Long userId = sessionUserMap.get(session.getId());
            if (excludeUserId != null && excludeUserId.equals(userId)) continue;
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("Failed to push to session {} in room {}", session.getId(), roomId, e);
            }
        }
    }

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

    public record PushPayload(String type, Long roomId, Object data) {
    }
}
