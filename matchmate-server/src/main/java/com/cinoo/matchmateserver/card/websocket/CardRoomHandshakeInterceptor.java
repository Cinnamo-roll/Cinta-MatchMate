package com.cinoo.matchmateserver.card.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.card.mapper.CardRoomMemberMapper;
import com.cinoo.matchmateserver.card.model.entity.CardRoomMember;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Ensures a card-room WebSocket can only be opened by a room member who can see updates.
 */
@Component
public class CardRoomHandshakeInterceptor implements HandshakeInterceptor {

    private final CardRoomMemberMapper cardRoomMemberMapper;

    public CardRoomHandshakeInterceptor(CardRoomMemberMapper cardRoomMemberMapper) {
        this.cardRoomMemberMapper = cardRoomMemberMapper;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Object userIdValue = attributes.get("userId");
        Long roomId = parseRoomId(request);
        if (!(userIdValue instanceof Long userId) || roomId == null) {
            return false;
        }

        return cardRoomMemberMapper.selectCount(
                new LambdaQueryWrapper<CardRoomMember>()
                        .eq(CardRoomMember::getRoomId, roomId)
                        .eq(CardRoomMember::getUserId, userId)
                        .in(CardRoomMember::getStatus,
                                CardConstant.MEMBER_STATUS_ACTIVE,
                                CardConstant.MEMBER_STATUS_REJOIN_REQUEST)
        ) > 0;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Nothing to clean up.
    }

    private Long parseRoomId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return null;
        }
        try {
            return Long.valueOf(path.substring(lastSlash + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
