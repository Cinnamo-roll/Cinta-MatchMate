package com.cinoo.matchmateserver.chat.service;

import com.cinoo.matchmateserver.chat.service.ChatRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class ChatRedisServiceImpl implements ChatRedisService {

    private static final String UNREAD_PREFIX = "unread:";
    private static final String CURRENT_CONV_PREFIX = "current:conv:";
    private static final String LAST_ONLINE_PREFIX = "last:online:";
    private static final String CONV_CACHE_PREFIX = "cache:conv:";
    private static final Duration CURRENT_CONV_TTL = Duration.ofMinutes(10);
    private static final Duration CONV_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration LAST_ONLINE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public ChatRedisServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementUnread(Long userId, Long conversationId) {
        String key = unreadKey(userId, conversationId);
        redisTemplate.opsForValue().increment(key);
    }

    @Override
    public Long getUnreadCount(Long userId, Long conversationId) {
        String key = unreadKey(userId, conversationId);
        String val = redisTemplate.opsForValue().get(key);
        return val == null ? null : Long.parseLong(val);
    }

    @Override
    public void setUnreadCount(Long userId, Long conversationId, long count) {
        redisTemplate.opsForValue().set(
                unreadKey(userId, conversationId),
                String.valueOf(count)
        );
    }

    @Override
    public void clearUnread(Long userId, Long conversationId) {
        String key = unreadKey(userId, conversationId);
        redisTemplate.delete(key);
    }

    @Override
    public void setCurrentConversation(Long userId, Long conversationId) {
        String key = currentConvKey(userId);
        redisTemplate.opsForValue().set(key, conversationId.toString(), CURRENT_CONV_TTL);
    }

    @Override
    public Long getCurrentConversation(Long userId) {
        String key = currentConvKey(userId);
        String val = redisTemplate.opsForValue().get(key);
        return val == null ? null : Long.parseLong(val);
    }

    @Override
    public void clearCurrentConversation(Long userId) {
        String key = currentConvKey(userId);
        redisTemplate.delete(key);
    }

    @Override
    public void setLastOnline(Long userId) {
        String key = LAST_ONLINE_PREFIX + userId;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), LAST_ONLINE_TTL);
    }

    @Override
    public java.util.Date getLastOnline(Long userId) {
        String key = LAST_ONLINE_PREFIX + userId;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) return null;
        return new java.util.Date(Long.parseLong(val));
    }

    @Override
    public void cacheConversations(Long userId, String json) {
        String key = CONV_CACHE_PREFIX + userId;
        redisTemplate.opsForValue().set(key, json, CONV_CACHE_TTL);
    }

    @Override
    public String getCachedConversations(Long userId) {
        String key = CONV_CACHE_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void evictConversations(Long userId) {
        String key = CONV_CACHE_PREFIX + userId;
        redisTemplate.delete(key);
    }

    private static String unreadKey(Long userId, Long conversationId) {
        return UNREAD_PREFIX + userId + ":" + conversationId;
    }

    private static String currentConvKey(Long userId) {
        return CURRENT_CONV_PREFIX + userId;
    }
}
