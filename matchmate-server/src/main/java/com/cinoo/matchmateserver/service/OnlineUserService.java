package com.cinoo.matchmateserver.service;

import java.util.Set;

/**
 * 在线用户服务，基于 Redis 记录在线用户。
 */
public interface OnlineUserService {

    /**
     * 用户上线。
     */
    void userOnline(Long userId);

    /**
     * 用户下线。
     */
    void userOffline(Long userId);

    /**
     * 判断用户是否在线。
     */
    boolean isOnline(Long userId);

    /**
     * 获取所有在线用户 ID。
     */
    Set<Long> getAllOnlineUserIds();
}
