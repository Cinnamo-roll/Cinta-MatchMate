package com.cinoo.matchmateserver.card.service.support;

import com.cinoo.matchmateserver.card.websocket.CardWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardRoomEventPublisher {

    private final CardWebSocketHandler cardWebSocketHandler;

    public void pushAfterCommit(Long roomId, Long excludeUserId, String type, Object data) {
        runAfterCommit(() -> safePush(roomId, excludeUserId, type, data));
    }

    public void runAfterCommit(Runnable action) {
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

    private void safePush(Long roomId, Long excludeUserId, String type, Object data) {
        try {
            cardWebSocketHandler.pushEvent(roomId, excludeUserId, type, data);
        } catch (Exception e) {
            log.error("WS push failed type={} roomId={}", type, roomId, e);
        }
    }
}
