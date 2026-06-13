package com.cinoo.matchmateserver.retention;

import com.cinoo.matchmateserver.config.RetentionProperties;
import com.cinoo.matchmateserver.card.constant.CardConstant;
import com.cinoo.matchmateserver.card.mapper.CardRoomMapper;
import com.cinoo.matchmateserver.chat.mapper.ConversationMapper;
import com.cinoo.matchmateserver.chat.mapper.MessageMapper;
import com.cinoo.matchmateserver.chat.model.entity.Conversation;
import com.cinoo.matchmateserver.chat.model.entity.Message;
import com.cinoo.matchmateserver.chat.service.ChatRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class DataRetentionService {

    private final CardRoomMapper cardRoomMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ChatRedisService chatRedisService;
    private final RetentionProperties properties;

    public DataRetentionService(
            CardRoomMapper cardRoomMapper,
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            ChatRedisService chatRedisService,
            RetentionProperties properties) {
        this.cardRoomMapper = cardRoomMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatRedisService = chatRedisService;
        this.properties = properties;
    }

    @Transactional
    public int cleanupExpiredCardRooms() {
        List<Long> roomIds = cardRoomMapper.selectExpiredEndedRoomIds(
                CardConstant.HISTORY_RETENTION_COUNT);
        if (roomIds.isEmpty()) {
            return 0;
        }

        cardRoomMapper.deleteFundParticipantsByRoomIds(roomIds);
        cardRoomMapper.deleteFundsByRoomIds(roomIds);
        cardRoomMapper.deleteRoundScoresByRoomIds(roomIds);
        cardRoomMapper.deleteRoundsByRoomIds(roomIds);
        cardRoomMapper.deleteMembersByRoomIds(roomIds);
        int deleted = cardRoomMapper.deleteRoomsByIds(roomIds);
        log.info("Deleted {} expired card rooms", deleted);
        return deleted;
    }

    @Transactional
    public int cleanupExpiredChatMessages() {
        Instant cutoffInstant = Instant.now().minus(properties.getChatMessageAge());
        Date cutoff = Date.from(cutoffInstant);
        List<Long> conversationIds = messageMapper.selectConversationIdsBefore(cutoff);
        if (conversationIds.isEmpty()) {
            return 0;
        }

        int deleted = messageMapper.deleteBefore(cutoff);
        for (Long conversationId : conversationIds) {
            refreshConversationSummary(conversationId);
        }
        afterCommit(() -> evictConversationState(conversationIds));
        log.info("Deleted {} chat messages older than {}", deleted, cutoffInstant);
        return deleted;
    }

    private void refreshConversationSummary(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        Message latest = messageMapper.selectLatestByConversationId(conversationId);
        conversation.setLastMessage(latest == null ? null : latest.getContent());
        conversation.setLastMessageTime(latest == null ? null : latest.getCreateTime());
        conversationMapper.updateById(conversation);
    }

    private void evictConversationState(List<Long> conversationIds) {
        for (Long conversationId : conversationIds) {
            Conversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null) {
                continue;
            }
            try {
                clearUserConversationState(conversation.getUserId1(), conversationId);
                clearUserConversationState(conversation.getUserId2(), conversationId);
            } catch (RuntimeException e) {
                log.warn("Failed to clear chat cache for conversationId={}", conversationId, e);
            }
        }
    }

    private void clearUserConversationState(Long userId, Long conversationId) {
        chatRedisService.clearUnread(userId, conversationId);
        chatRedisService.evictConversations(userId);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
