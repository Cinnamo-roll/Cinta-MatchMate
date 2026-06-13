package com.cinoo.matchmateserver.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final CardWebSocketHandler cardWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final CardRoomHandshakeInterceptor cardRoomHandshakeInterceptor;
    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           CardWebSocketHandler cardWebSocketHandler,
                           AuthHandshakeInterceptor authHandshakeInterceptor,
                           CardRoomHandshakeInterceptor cardRoomHandshakeInterceptor,
                           @Value("${matchmate.cors.allowed-origins}") String allowedOrigins,
                           @Value("${matchmate.cors.allowed-origin-patterns:}") String allowedOriginPatterns) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.cardWebSocketHandler = cardWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.cardRoomHandshakeInterceptor = cardRoomHandshakeInterceptor;
        this.allowedOrigins = splitCsv(allowedOrigins);
        this.allowedOriginPatterns = splitCsv(allowedOriginPatterns);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var chatRegistration = registry.addHandler(chatWebSocketHandler, "/ws/chat");
        if (allowedOrigins.length > 0) {
            chatRegistration.setAllowedOrigins(allowedOrigins);
        }
        if (allowedOriginPatterns.length > 0) {
            chatRegistration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        chatRegistration.addInterceptors(authHandshakeInterceptor);

        var cardRegistration = registry.addHandler(cardWebSocketHandler, "/ws/card/{roomId}");
        if (allowedOrigins.length > 0) {
            cardRegistration.setAllowedOrigins(allowedOrigins);
        }
        if (allowedOriginPatterns.length > 0) {
            cardRegistration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        cardRegistration.addInterceptors(authHandshakeInterceptor, cardRoomHandshakeInterceptor);
    }

    private String[] splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return value.trim().split("\\s*,\\s*");
    }
}
