package com.cinoo.matchmateserver.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts cache entries after the surrounding transaction commits.
 */
@Component
public class CacheInvalidationService {

    private final DistributedCacheService cacheService;

    public CacheInvalidationService(DistributedCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public void userChanged(long userId) {
        afterCommit(() -> {
            cacheService.evict(CacheNames.USER_VIEWS, CacheKeys.user(userId));
            clearUserCollections();
        });
    }

    public void userTagsChanged(long userId) {
        afterCommit(() -> {
            cacheService.evict(CacheNames.USER_TAGS, CacheKeys.user(userId));
            cacheService.evict(CacheNames.USER_VIEWS, CacheKeys.user(userId));
            clearUserCollections();
        });
    }

    public void userDeleted(long userId) {
        afterCommit(() -> {
            cacheService.evict(CacheNames.USER_TAGS, CacheKeys.user(userId));
            cacheService.evict(CacheNames.USER_VIEWS, CacheKeys.user(userId));
            clearUserCollections();
        });
    }

    public void userCollectionChanged() {
        afterCommit(this::clearUserCollections);
    }

    private void clearUserCollections() {
        cacheService.clear(CacheNames.USER_RECOMMENDATIONS);
        cacheService.clear(CacheNames.USER_SEARCHES);
    }

    private void afterCommit(Runnable action) {
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
                }
        );
    }
}
