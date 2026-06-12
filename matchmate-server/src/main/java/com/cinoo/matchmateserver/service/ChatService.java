package com.cinoo.matchmateserver.service;

import com.cinoo.matchmateserver.model.vo.ConversationVO;
import com.cinoo.matchmateserver.model.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface ChatService {

    /**
     * 发送消息。如果两个用户间没有会话则自动创建。
     *
     * @param receiverId 接收方用户 ID
     * @param content    消息内容
     * @param request    当前请求
     * @return 发送的消息
     */
    MessageVO sendMessage(Long receiverId, String content, HttpServletRequest request);

    /**
     * 获取会话的消息历史，按时间倒序分页，同时标记已读。
     *
     * @param conversationId 会话 ID
     * @param page           页码，从 1 开始
     * @param pageSize       每页条数
     * @param request        当前请求
     * @return 消息列表（按时间升序）
     */
    List<MessageVO> getMessages(Long conversationId, long page, long pageSize, HttpServletRequest request);

    /**
     * 获取当前用户的会话列表，按最后消息时间降序。
     *
     * @param request 当前请求
     * @return 会话列表
     */
    List<ConversationVO> getConversations(HttpServletRequest request);

    ConversationVO getConversation(Long conversationId, HttpServletRequest request);

    /**
     * 打开/进入会话：清空 Redis 未读数，标记 MySQL 消息已读，记录当前会话。
     *
     * @param conversationId 会话 ID
     * @param request        当前请求
     */
    void openConversation(Long conversationId, HttpServletRequest request);

    void closeConversation(Long conversationId, HttpServletRequest request);

    /**
     * 查找当前用户与目标用户之间的已有会话 ID，没有返回 null。
     */
    Long findConversationId(Long targetUserId, HttpServletRequest request);
}
