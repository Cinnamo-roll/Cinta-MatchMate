package com.cinoo.matchmateserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataRetentionScheduler {

    private final DataRetentionService retentionService;

    public DataRetentionScheduler(DataRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${matchmate.retention.cleanup-cron:0 5 * * * *}")
    public void cleanupExpiredData() {
        cleanup();
    }

    private void cleanup() {
        try {
            retentionService.cleanupExpiredCardRooms();
            retentionService.cleanupExpiredChatMessages();
        } catch (RuntimeException e) {
            log.error("Scheduled data retention cleanup failed", e);
        }
    }
}
