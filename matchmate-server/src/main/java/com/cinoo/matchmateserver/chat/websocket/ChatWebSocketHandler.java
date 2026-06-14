package com.cinoo.matchmateserver.chat.websocket;

import com.cinoo.matchmateserver.chat.model.vo.MessageVO;
import com.cinoo.matchmateserver.chat.service.ChatRedisService;
import com.cinoo.matchmateserver.chat.service.OnlineUserService;
import com.cinoo.matchmateserver.infrastructure.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final OnlineUserService onlineUserService;
    private final ChatRedisService chatRedisService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(WebSocketSessionManager sessionManager,
                                OnlineUserService onlineUserService,
                                ChatRedisService chatRedisService,
                                ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.onlineUserService = onlineUserService;
        this.chatRedisService = chatRedisService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) {
            sessionManager.register(userId, session);
            onlineUserService.userOnline(userId);
            chatRedisService.setLastOnline(userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null && sessionManager.remove(userId, session)) {
            onlineUserService.userOffline(userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = getUserId(session);
        if (userId != null) {
            onlineUserService.userOnline(userId);
        }
    }

    public void pushNewMessage(Long receiverId, MessageVO messageVO) {
        push(receiverId, "new_message", messageVO);
    }

    public void pushMessagesRead(Long receiverId, Long conversationId, Long readerId) {
        push(receiverId, "messages_read", new MessagesReadPayload(conversationId, readerId));
    }

    public void pushAccountBannedAndDisconnect(Long userId, String message) {
        pushNoticeAndDisconnect(userId, "account_banned", new AccountBannedPayload(message));
    }

    public void pushLoginTakenOverAndDisconnect(Long userId, String message) {
        pushNoticeAndDisconnect(userId, "login_taken_over", new LoginTakenOverPayload(message));
    }

    private void pushNoticeAndDisconnect(Long userId, String type, Object data) {
        WebSocketSession session = sessionManager.getSession(userId);
        if (session == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    new PushPayload(type, data)
            );
            session.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            log.warn("Failed to push {} notice to user {}", type, userId, e);
        } finally {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception e) {
                log.warn("Failed to disconnect user {}", userId, e);
            }
            if (sessionManager.remove(userId, session)) {
                try {
                    onlineUserService.userOffline(userId);
                } catch (RuntimeException e) {
                    log.warn("Failed to clear online state for disconnected user {}", userId, e);
                }
            }
        }
    }

    private void push(Long receiverId, String type, Object data) {
        WebSocketSession session = sessionManager.getSession(receiverId);
        if (session == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    new PushPayload(type, data)
            );
            session.sendMessage(new TextMessage(payload));
        } catch (IOException e) {
            log.error("Failed to push message to user {}", receiverId, e);
        }
    }

    private Long getUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get("userId");
        return attr instanceof Long ? (Long) attr : null;
    }

    private record PushPayload(String type, Object data) {
    }

    private record MessagesReadPayload(Long conversationId, Long readerId) {
    }

    private record AccountBannedPayload(String message) {
    }

    private record LoginTakenOverPayload(String message) {
    }
}
