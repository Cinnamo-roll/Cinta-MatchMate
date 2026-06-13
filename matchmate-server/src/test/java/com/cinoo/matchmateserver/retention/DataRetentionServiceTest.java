package com.cinoo.matchmateserver.retention;

import com.cinoo.matchmateserver.chat.service.ChatRedisService;
import com.cinoo.matchmateserver.config.RetentionProperties;
import com.cinoo.matchmateserver.card.mapper.CardRoomMapper;
import com.cinoo.matchmateserver.chat.mapper.ConversationMapper;
import com.cinoo.matchmateserver.chat.mapper.MessageMapper;
import com.cinoo.matchmateserver.chat.model.entity.Conversation;
import com.cinoo.matchmateserver.chat.model.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock private CardRoomMapper cardRoomMapper;
    @Mock private ConversationMapper conversationMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private ChatRedisService chatRedisService;

    private DataRetentionService retentionService;

    @BeforeEach
    void setUp() {
        RetentionProperties properties = new RetentionProperties();
        properties.setChatMessageAge(Duration.ofDays(1));
        retentionService = new DataRetentionService(
                cardRoomMapper,
                conversationMapper,
                messageMapper,
                chatRedisService,
                properties);
    }

    @Test
    void cleanupExpiredCardRooms_deletesChildrenBeforeRooms() {
        List<Long> roomIds = List.of(11L, 12L);
        when(cardRoomMapper.selectExpiredEndedRoomIds(6)).thenReturn(roomIds);
        when(cardRoomMapper.deleteRoomsByIds(roomIds)).thenReturn(2);

        int deleted = retentionService.cleanupExpiredCardRooms();

        assertEquals(2, deleted);
        InOrder order = inOrder(cardRoomMapper);
        order.verify(cardRoomMapper).deleteFundParticipantsByRoomIds(roomIds);
        order.verify(cardRoomMapper).deleteFundsByRoomIds(roomIds);
        order.verify(cardRoomMapper).deleteRoundScoresByRoomIds(roomIds);
        order.verify(cardRoomMapper).deleteRoundsByRoomIds(roomIds);
        order.verify(cardRoomMapper).deleteMembersByRoomIds(roomIds);
        order.verify(cardRoomMapper).deleteRoomsByIds(roomIds);
    }

    @Test
    void cleanupExpiredChatMessages_refreshesSummaryAndClearsCaches() {
        Conversation conversation = new Conversation();
        conversation.setId(20L);
        conversation.setUserId1(1L);
        conversation.setUserId2(2L);
        Message latest = new Message();
        latest.setContent("latest");
        latest.setCreateTime(new Date());

        when(messageMapper.selectConversationIdsBefore(any(Date.class)))
                .thenReturn(List.of(20L));
        when(messageMapper.deleteBefore(any(Date.class))).thenReturn(3);
        when(conversationMapper.selectById(20L)).thenReturn(conversation);
        when(messageMapper.selectLatestByConversationId(20L)).thenReturn(latest);

        int deleted = retentionService.cleanupExpiredChatMessages();

        assertEquals(3, deleted);
        assertEquals("latest", conversation.getLastMessage());
        assertEquals(latest.getCreateTime(), conversation.getLastMessageTime());
        verify(conversationMapper).updateById(conversation);
        verify(chatRedisService).clearUnread(1L, 20L);
        verify(chatRedisService).clearUnread(2L, 20L);
        verify(chatRedisService).evictConversations(1L);
        verify(chatRedisService).evictConversations(2L);
    }
}
