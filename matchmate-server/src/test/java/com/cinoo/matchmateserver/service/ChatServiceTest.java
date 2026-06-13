package com.cinoo.matchmateserver.service;

import com.cinoo.matchmateserver.mapper.ConversationMapper;
import com.cinoo.matchmateserver.mapper.MessageMapper;
import com.cinoo.matchmateserver.mapper.UserMapper;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;
import com.cinoo.matchmateserver.model.domain.Conversation;
import com.cinoo.matchmateserver.model.domain.Message;
import com.cinoo.matchmateserver.model.domain.User;
import com.cinoo.matchmateserver.model.vo.ConversationVO;
import com.cinoo.matchmateserver.model.vo.MessageVO;
import com.cinoo.matchmateserver.service.impl.ChatServiceImpl;
import com.cinoo.matchmateserver.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ChatWebSocketHandler chatWebSocketHandler;
    @Mock
    private OnlineUserService onlineUserService;
    @Mock
    private ChatRedisService chatRedisService;
    @Mock
    private ObjectMapper objectMapper;

    private ChatService chatService;
    private User currentUser;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                userService,
                userMapper,
                conversationMapper,
                messageMapper,
                chatWebSocketHandler,
                onlineUserService,
                chatRedisService,
                objectMapper
        );
        currentUser = user(1L, "me");
        when(userService.getLoginUser(any())).thenReturn(currentUser);
    }

    @Test
    void getConversationsLoadsTargetUsersInOneBatchAndRefreshesDynamicState() throws Exception {
        Conversation first = conversation(10L, 1L, 2L);
        Conversation second = conversation(11L, 3L, 1L);
        when(conversationMapper.selectByUserId(1L)).thenReturn(List.of(first, second));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(2L, "alice"), user(3L, "bob")));
        when(chatRedisService.getUnreadCount(1L, 10L)).thenReturn(2L);
        when(onlineUserService.isOnline(2L)).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        List<ConversationVO> result = chatService.getConversations(new MockHttpServletRequest());

        assertEquals(2, result.size());
        assertEquals("alice", result.get(0).getTargetUsername());
        assertEquals(2L, result.get(0).getUnreadCount());
        assertEquals(true, result.get(0).getIsOnline());
        verify(chatRedisService).clearCurrentConversation(1L);
        verify(userMapper).selectBatchIds(any());
        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    void sendMessageTrimsContentAndSetsTimestampsBeforeInsert() {
        User receiver = user(2L, "alice");
        Conversation conversation = conversation(10L, 1L, 2L);
        when(userMapper.selectById(2L)).thenReturn(receiver);
        when(conversationMapper.selectByUserIds(1L, 2L)).thenReturn(conversation);

        MessageVO result = chatService.sendMessage(
                2L,
                "  hello  ",
                new MockHttpServletRequest()
        );

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(messageCaptor.capture());
        Message savedMessage = messageCaptor.getValue();
        assertEquals("hello", savedMessage.getContent());
        assertNotNull(savedMessage.getCreateTime());
        assertNotNull(savedMessage.getUpdateTime());
        assertEquals(savedMessage.getCreateTime(), result.getCreateTime());
        assertEquals("hello", conversation.getLastMessage());
        assertNotNull(conversation.getLastMessageTime());
    }

    @Test
    void getConversationRestoresTargetProfile() {
        Conversation conversation = conversation(10L, 1L, 2L);
        when(conversationMapper.selectById(10L)).thenReturn(conversation);
        when(userMapper.selectById(2L)).thenReturn(user(2L, "alice"));
        when(onlineUserService.isOnline(2L)).thenReturn(true);

        ConversationVO result = chatService.getConversation(
                10L,
                new MockHttpServletRequest()
        );

        assertEquals(2L, result.getTargetUserId());
        assertEquals("alice", result.getTargetUsername());
        assertEquals(true, result.getIsOnline());
    }

    @Test
    void getConversationRestoresUnreadCountFromDatabaseWhenRedisIsEmpty() {
        Conversation conversation = conversation(10L, 1L, 2L);
        when(conversationMapper.selectById(10L)).thenReturn(conversation);
        when(userMapper.selectById(2L)).thenReturn(user(2L, "alice"));
        when(chatRedisService.getUnreadCount(1L, 10L)).thenReturn(null);
        when(messageMapper.countUnread(10L, 1L)).thenReturn(4L);

        ConversationVO result = chatService.getConversation(
                10L,
                new MockHttpServletRequest()
        );

        assertEquals(4L, result.getUnreadCount());
        verify(chatRedisService).setUnreadCount(1L, 10L, 4L);
    }

    @Test
    void openingConversationPushesReadReceiptWhenMessagesChanged() {
        Conversation conversation = conversation(10L, 1L, 2L);
        when(conversationMapper.selectById(10L)).thenReturn(conversation);
        when(messageMapper.markAsRead(10L, 1L)).thenReturn(2);

        chatService.openConversation(10L, new MockHttpServletRequest());

        verify(chatWebSocketHandler).pushMessagesRead(2L, 10L, 1L);
    }

    @Test
    void sendMessageRejectsBannedReceiver() {
        User receiver = user(2L, "alice");
        receiver.setUserStatus(1);
        when(userMapper.selectById(2L)).thenReturn(receiver);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chatService.sendMessage(
                        2L,
                        "hello",
                        new MockHttpServletRequest()
                )
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(messageMapper, never()).insert(any(Message.class));
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setIsDelete(0);
        user.setUserStatus(0);
        return user;
    }

    private Conversation conversation(Long id, Long userId1, Long userId2) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setUserId1(userId1);
        conversation.setUserId2(userId2);
        conversation.setIsDelete(0);
        return conversation;
    }
}
