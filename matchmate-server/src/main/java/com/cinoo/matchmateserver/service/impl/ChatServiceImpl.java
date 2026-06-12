package com.cinoo.matchmateserver.service.impl;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.mapper.ConversationMapper;
import com.cinoo.matchmateserver.mapper.MessageMapper;
import com.cinoo.matchmateserver.mapper.UserMapper;
import com.cinoo.matchmateserver.model.domain.Conversation;
import com.cinoo.matchmateserver.model.domain.Message;
import com.cinoo.matchmateserver.model.domain.User;
import com.cinoo.matchmateserver.model.vo.ConversationVO;
import com.cinoo.matchmateserver.model.vo.MessageVO;
import com.cinoo.matchmateserver.service.ChatRedisService;
import com.cinoo.matchmateserver.service.ChatService;
import com.cinoo.matchmateserver.service.OnlineUserService;
import com.cinoo.matchmateserver.service.UserService;
import com.cinoo.matchmateserver.websocket.ChatWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    private static final long MAX_PAGE_SIZE = 100;
    private static final int MESSAGE_TYPE_TEXT = 0;
    private static final int MESSAGE_STATUS_UNREAD = 0;

    private final UserService userService;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final OnlineUserService onlineUserService;
    private final ChatRedisService chatRedisService;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(UserService userService,
                           UserMapper userMapper,
                           ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           ChatWebSocketHandler chatWebSocketHandler,
                           OnlineUserService onlineUserService,
                           ChatRedisService chatRedisService,
                           ObjectMapper objectMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.onlineUserService = onlineUserService;
        this.chatRedisService = chatRedisService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(Long receiverId, String content, HttpServletRequest request) {
        User sender = userService.getLoginUser(request);
        String normalizedContent = content.trim();

        if (Objects.equals(sender.getId(), receiverId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能给自己发送消息");
        }

        User receiver = userMapper.selectById(receiverId);
        if (receiver == null || receiver.getIsDelete() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "接收方用户不存在");
        }

        Conversation conversation = conversationMapper.selectByUserIds(sender.getId(), receiverId);
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setUserId1(sender.getId());
            conversation.setUserId2(receiverId);
            conversationMapper.insert(conversation);
        }

        Date now = new Date();
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(sender.getId());
        message.setReceiverId(receiverId);
        message.setContent(normalizedContent);
        message.setMessageType(MESSAGE_TYPE_TEXT);
        message.setStatus(MESSAGE_STATUS_UNREAD);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        messageMapper.insert(message);

        conversation.setLastMessage(normalizedContent);
        conversation.setLastMessageTime(now);
        conversationMapper.updateById(conversation);

        MessageVO messageVO = toMessageVO(message);
        chatWebSocketHandler.pushNewMessage(receiverId, messageVO);
        onlineUserService.userOnline(sender.getId());

        if (shouldIncrementUnread(receiverId, conversation.getId())) {
            incrementUnread(receiverId, conversation.getId());
        }

        chatRedisService.evictConversations(sender.getId());
        chatRedisService.evictConversations(receiverId);
        return messageVO;
    }

    @Override
    public List<MessageVO> getMessages(Long conversationId, long page, long pageSize,
                                       HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        getParticipantConversation(conversationId, currentUser.getId());

        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long offset = (normalizedPage - 1) * normalizedPageSize;
        List<Message> messages = messageMapper.selectByConversationId(
                conversationId,
                offset,
                normalizedPageSize
        );
        Collections.reverse(messages);

        markConversationRead(currentUser.getId(), conversationId);
        return messages.stream().map(this::toMessageVO).toList();
    }

    @Override
    public List<ConversationVO> getConversations(HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        Long currentUserId = currentUser.getId();
        chatRedisService.clearCurrentConversation(currentUserId);

        List<ConversationVO> cached = readCachedConversations(currentUserId);
        if (cached != null) {
            refreshDynamicConversationState(cached, currentUserId);
            return cached;
        }

        List<Conversation> conversations = conversationMapper.selectByUserId(currentUserId);
        if (conversations == null || conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> targetIds = conversations.stream()
                .map(conversation -> targetUserId(conversation, currentUserId))
                .distinct()
                .toList();
        Map<Long, User> targetUsers = userMapper.selectBatchIds(targetIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ConversationVO> result = new ArrayList<>(conversations.size());
        for (Conversation conversation : conversations) {
            Long targetId = targetUserId(conversation, currentUserId);
            result.add(toConversationVO(conversation, currentUserId, targetUsers.get(targetId)));
        }
        cacheConversations(currentUserId, result);
        return result;
    }

    @Override
    public ConversationVO getConversation(Long conversationId, HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        Conversation conversation = getParticipantConversation(conversationId, currentUser.getId());
        Long targetId = targetUserId(conversation, currentUser.getId());
        return toConversationVO(conversation, currentUser.getId(), userMapper.selectById(targetId));
    }

    @Override
    public void openConversation(Long conversationId, HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        getParticipantConversation(conversationId, currentUser.getId());
        markConversationRead(currentUser.getId(), conversationId);
    }

    @Override
    public void closeConversation(Long conversationId, HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        if (Objects.equals(
                chatRedisService.getCurrentConversation(currentUser.getId()),
                conversationId
        )) {
            chatRedisService.clearCurrentConversation(currentUser.getId());
        }
    }

    @Override
    public Long findConversationId(Long targetUserId, HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        if (Objects.equals(currentUser.getId(), targetUserId)) {
            return null;
        }
        Conversation conversation = conversationMapper.selectByUserIds(currentUser.getId(), targetUserId);
        return conversation != null ? conversation.getId() : null;
    }

    private boolean shouldIncrementUnread(Long receiverId, Long conversationId) {
        if (!onlineUserService.isOnline(receiverId)) {
            return true;
        }
        return !Objects.equals(
                chatRedisService.getCurrentConversation(receiverId),
                conversationId
        );
    }

    private void incrementUnread(Long userId, Long conversationId) {
        Long cachedUnread = chatRedisService.getUnreadCount(userId, conversationId);
        if (cachedUnread == null) {
            chatRedisService.setUnreadCount(
                    userId,
                    conversationId,
                    messageMapper.countUnread(conversationId, userId)
            );
            return;
        }
        chatRedisService.incrementUnread(userId, conversationId);
    }

    private void markConversationRead(Long userId, Long conversationId) {
        messageMapper.markAsRead(conversationId, userId);
        chatRedisService.clearUnread(userId, conversationId);
        chatRedisService.setCurrentConversation(userId, conversationId);
        chatRedisService.evictConversations(userId);
    }

    private Conversation getParticipantConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || conversation.getIsDelete() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!Objects.equals(conversation.getUserId1(), userId)
                && !Objects.equals(conversation.getUserId2(), userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "不是会话参与者");
        }
        return conversation;
    }

    private List<ConversationVO> readCachedConversations(Long userId) {
        String cached = chatRedisService.getCachedConversations(userId);
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    cached,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, ConversationVO.class)
            );
        } catch (Exception e) {
            log.warn("Failed to deserialize cached conversations for userId={}", userId, e);
            chatRedisService.evictConversations(userId);
            return null;
        }
    }

    private void cacheConversations(Long userId, List<ConversationVO> conversations) {
        try {
            chatRedisService.cacheConversations(userId, objectMapper.writeValueAsString(conversations));
        } catch (Exception e) {
            log.warn("Failed to cache conversations for userId={}", userId, e);
        }
    }

    private ConversationVO toConversationVO(Conversation conversation, Long currentUserId, User targetUser) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setLastMessage(conversation.getLastMessage());
        vo.setLastMessageTime(conversation.getLastMessageTime());
        vo.setTargetUserId(targetUserId(conversation, currentUserId));
        if (targetUser != null) {
            vo.setTargetUsername(targetUser.getUsername());
            vo.setTargetAvatarUrl(targetUser.getAvatarUrl());
        }
        refreshDynamicConversationState(vo, currentUserId);
        return vo;
    }

    private void refreshDynamicConversationState(List<ConversationVO> conversations, Long currentUserId) {
        conversations.forEach(conversation -> refreshDynamicConversationState(conversation, currentUserId));
    }

    private void refreshDynamicConversationState(ConversationVO conversation, Long currentUserId) {
        Long targetId = conversation.getTargetUserId();
        conversation.setUnreadCount(resolveUnreadCount(currentUserId, conversation.getId()));
        conversation.setIsOnline(onlineUserService.isOnline(targetId));
        conversation.setLastOnlineTime(chatRedisService.getLastOnline(targetId));
    }

    private Long targetUserId(Conversation conversation, Long currentUserId) {
        return Objects.equals(conversation.getUserId1(), currentUserId)
                ? conversation.getUserId2()
                : conversation.getUserId1();
    }

    private long resolveUnreadCount(Long userId, Long conversationId) {
        Long cachedUnread = chatRedisService.getUnreadCount(userId, conversationId);
        if (cachedUnread != null) {
            return cachedUnread;
        }
        long unread = messageMapper.countUnread(conversationId, userId);
        chatRedisService.setUnreadCount(userId, conversationId, unread);
        return unread;
    }

    private MessageVO toMessageVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderId(message.getSenderId());
        vo.setReceiverId(message.getReceiverId());
        vo.setContent(message.getContent());
        vo.setMessageType(message.getMessageType());
        vo.setStatus(message.getStatus());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
