package com.cinoo.matchmateserver.websocket;

import com.cinoo.matchmateserver.constant.CardRoomEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardWebSocketHandlerTest {

    private CardWebSocketHandler handler;
    private WebSocketSession session1;
    private WebSocketSession session2;
    private static final Long ROOM_ID = 100L;
    private static final Long USER1_ID = 10L;
    private static final Long USER2_ID = 20L;

    @BeforeEach
    void setUp() {
        handler = new CardWebSocketHandler(new ObjectMapper());
        session1 = mockSession("s1", USER1_ID, ROOM_ID);
        session2 = mockSession("s2", USER2_ID, ROOM_ID);
    }

    private WebSocketSession mockSession(String id, Long userId, Long roomId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getUri()).thenReturn(URI.create("/api/ws/card/" + roomId));
        when(session.getAttributes()).thenReturn(Map.of("userId", userId));
        return session;
    }

    @Test
    void afterConnectionEstablished_shouldRegisterSession() {
        handler.afterConnectionEstablished(session1);
        assertEquals(1, handler.onlineCount(ROOM_ID));
    }

    @Test
    void afterConnectionEstablished_invalidUserId_shouldClose() {
        WebSocketSession bad = mock(WebSocketSession.class);
        when(bad.getAttributes()).thenReturn(Map.of());
        when(bad.getUri()).thenReturn(URI.create("/api/ws/card/" + ROOM_ID));

        handler.afterConnectionEstablished(bad);
        assertEquals(0, handler.onlineCount(ROOM_ID));
    }

    @Test
    void afterConnectionClosed_shouldRemoveSession() {
        handler.afterConnectionEstablished(session1);
        assertEquals(1, handler.onlineCount(ROOM_ID));
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);
        assertEquals(0, handler.onlineCount(ROOM_ID));
    }

    @Test
    void multipleConnections_shouldTrackAll() {
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);
        assertEquals(2, handler.onlineCount(ROOM_ID));
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);
        assertEquals(1, handler.onlineCount(ROOM_ID));
        handler.afterConnectionClosed(session2, CloseStatus.NORMAL);
        assertEquals(0, handler.onlineCount(ROOM_ID));
    }

    @Test
    void pushEvent_shouldSendToAllSessions() throws Exception {
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        handler.pushEvent(ROOM_ID, null, CardRoomEventType.ROUND_CREATED, "test-data");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void pushEvent_shouldExcludeSender() throws Exception {
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        handler.pushEvent(ROOM_ID, USER1_ID, CardRoomEventType.MEMBER_JOINED, "data");

        verify(session1, never()).sendMessage(any());
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void pushEvent_shouldNotSendToClosedSession() throws Exception {
        when(session1.isOpen()).thenReturn(false);
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        handler.pushEvent(ROOM_ID, null, "test", "data");

        verify(session1, never()).sendMessage(any());
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void pushEvent_sendFailure_shouldNotThrow() throws Exception {
        handler.afterConnectionEstablished(session1);
        doThrow(new java.io.IOException("send failed")).when(session1).sendMessage(any());

        assertDoesNotThrow(() -> handler.pushEvent(ROOM_ID, null, "test", "data"));
    }

    @Test
    void pushPayload_shouldContainTypeRoomIdAndData() throws Exception {
        handler.afterConnectionEstablished(session1);

        handler.pushEvent(ROOM_ID, null, CardRoomEventType.ROUND_CREATED,
                Map.of("score", 5));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        String json = captor.getValue().getPayload();

        assertTrue(json.contains("card_room_round_created"), "missing event type");
        assertTrue(json.contains("\"roomId\":100"), "missing roomId");
        // data should contain the map
        assertTrue(json.contains("\"score\":5"), "missing data");
    }

    @Test
    void emptyRoom_pushShouldNotFail() {
        assertDoesNotThrow(() -> handler.pushEvent(999L, null, "test", "data"));
    }
}
