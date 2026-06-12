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

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           CardWebSocketHandler cardWebSocketHandler,
                           AuthHandshakeInterceptor authHandshakeInterceptor,
                           CardRoomHandshakeInterceptor cardRoomHandshakeInterceptor,
                           @Value("${matchmate.cors.allowed-origins}") String allowedOrigins) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.cardWebSocketHandler = cardWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.cardRoomHandshakeInterceptor = cardRoomHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(authHandshakeInterceptor);
        registry.addHandler(cardWebSocketHandler, "/ws/card/{roomId}")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(authHandshakeInterceptor, cardRoomHandshakeInterceptor);
    }
}
