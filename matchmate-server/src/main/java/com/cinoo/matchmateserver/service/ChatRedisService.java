package com.cinoo.matchmateserver.service;

/**
 * 聊天相关的 Redis 操作：未读消息计数、当前会话追踪。
 */
public interface ChatRedisService {

    /**
     * 增加用户在某会话的未读消息数。
     */
    void incrementUnread(Long userId, Long conversationId);

    /**
     * 获取用户在某会话的未读消息数。
     */
    Long getUnreadCount(Long userId, Long conversationId);

    void setUnreadCount(Long userId, Long conversationId, long count);

    /**
     * 清除用户在某会话的未读消息数。
     */
    void clearUnread(Long userId, Long conversationId);

    /**
     * 记录用户当前正在查看的会话 ID。
     */
    void setCurrentConversation(Long userId, Long conversationId);

    /**
     * 获取用户当前正在查看的会话 ID，null 表示未处于任何会话。
     */
    Long getCurrentConversation(Long userId);

    /**
     * 清除用户当前会话标记（离开会话时调用）。
     */
    void clearCurrentConversation(Long userId);

    /**
     * 记录用户最后在线时间。
     */
    void setLastOnline(Long userId);

    /**
     * 获取用户最后在线时间，null 表示无记录。
     */
    java.util.Date getLastOnline(Long userId);

    /**
     * 缓存用户的会话列表（JSON 序列化）。
     */
    void cacheConversations(Long userId, String json);

    /**
     * 获取缓存的会话列表 JSON，null 表示未命中。
     */
    String getCachedConversations(Long userId);

    /**
     * 清除用户的会话列表缓存。
     */
    void evictConversations(Long userId);
}
