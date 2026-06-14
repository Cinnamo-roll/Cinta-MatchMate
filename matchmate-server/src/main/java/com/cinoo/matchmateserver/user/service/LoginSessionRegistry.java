package com.cinoo.matchmateserver.user.service;

import com.cinoo.matchmateserver.user.constant.UserConstant;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the current active HTTP session for each user.
 */
@Service
@Slf4j
public class LoginSessionRegistry {

    private final Map<Long, HttpSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();

    public boolean hasOtherActiveSession(Long userId, String currentSessionId) {
        HttpSession existingSession = userSessions.get(userId);
        if (!isActive(existingSession)) {
            userSessions.remove(userId, existingSession);
            return false;
        }
        return !Objects.equals(existingSession.getId(), currentSessionId);
    }

    public void register(Long userId, HttpSession session) {
        HttpSession oldSession = userSessions.put(userId, session);
        if (oldSession != null) {
            sessionUsers.remove(safeSessionId(oldSession));
        }
        sessionUsers.put(session.getId(), userId);
    }

    public void invalidateOtherSession(Long userId, String currentSessionId) {
        HttpSession existingSession = userSessions.get(userId);
        if (!isActive(existingSession) || Objects.equals(existingSession.getId(), currentSessionId)) {
            return;
        }
        String existingSessionId = safeSessionId(existingSession);
        userSessions.remove(userId, existingSession);
        sessionUsers.remove(existingSessionId);
        try {
            existingSession.invalidate();
        } catch (IllegalStateException e) {
            log.debug("Login session was already invalidated, sessionId={}", existingSessionId, e);
        }
    }

    public void remove(HttpSession session) {
        if (session == null) {
            return;
        }
        String sessionId = safeSessionId(session);
        if (sessionId == null) {
            return;
        }
        Long userId = sessionUsers.remove(sessionId);
        if (userId != null) {
            userSessions.remove(userId, session);
        }
    }

    public void removeUser(Long userId) {
        HttpSession session = userSessions.remove(userId);
        if (session != null) {
            sessionUsers.remove(safeSessionId(session));
        }
    }

    private boolean isActive(HttpSession session) {
        if (session == null) {
            return false;
        }
        try {
            session.getId();
            return session.getAttribute(UserConstant.USER_LOGIN_STATE) instanceof Long;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private String safeSessionId(HttpSession session) {
        try {
            return session == null ? null : session.getId();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
