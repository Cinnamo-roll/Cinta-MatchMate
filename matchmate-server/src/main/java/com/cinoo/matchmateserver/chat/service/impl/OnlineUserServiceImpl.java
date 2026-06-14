package com.cinoo.matchmateserver.chat.service.impl;

import com.cinoo.matchmateserver.chat.service.OnlineUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Redis Set 的在线用户记录。
 * Key: matchmate:online:users
 * TTL: 每次操作刷新 5 分钟，心跳机制由 WebSocket 连接维持。
 */
@Service
@Slf4j
public class OnlineUserServiceImpl implements OnlineUserService {

    private static final String ONLINE_USERS_KEY = "matchmate:online:users:v2";
    private static final long ONLINE_TTL_MILLIS = 5 * 60 * 1000L;

    private final StringRedisTemplate redisTemplate;

    public OnlineUserServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void userOnline(Long userId) {
        redisTemplate.opsForZSet().add(
                ONLINE_USERS_KEY,
                userId.toString(),
                System.currentTimeMillis()
        );
    }

    @Override
    public void userOffline(Long userId) {
        redisTemplate.opsForZSet().remove(ONLINE_USERS_KEY, userId.toString());
    }

    @Override
    public boolean isOnline(Long userId) {
        removeExpiredUsers();
        Double lastSeen = redisTemplate.opsForZSet().score(ONLINE_USERS_KEY, userId.toString());
        return lastSeen != null && lastSeen >= onlineCutoff();
    }

    @Override
    public Set<Long> getAllOnlineUserIds() {
        removeExpiredUsers();
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByScore(ONLINE_USERS_KEY, onlineCutoff(), Double.POSITIVE_INFINITY);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    private void removeExpiredUsers() {
        redisTemplate.opsForZSet().removeRangeByScore(
                ONLINE_USERS_KEY,
                Double.NEGATIVE_INFINITY,
                onlineCutoff()
        );
    }

    private double onlineCutoff() {
        return System.currentTimeMillis() - ONLINE_TTL_MILLIS;
    }
}
