package com.cinoo.matchmateserver.infrastructure.cache;

import com.cinoo.matchmateserver.config.CacheProperties;
import com.cinoo.matchmateserver.tag.service.TagServiceImpl;
import com.cinoo.matchmateserver.user.service.UserServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cluster-safe cache warmup and refresh jobs.
 */
@Component
@Slf4j
public class CacheWarmupScheduler {

    private static final String WARMUP_LOCK = "matchmate:lock:cache:warmup";
    private static final String RECOMMENDATION_JOB_LOCK =
            "matchmate:lock:scheduled:recommendations";
    private static final String CATEGORY_JOB_LOCK =
            "matchmate:lock:scheduled:tag-categories";

    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;
    private final UserServiceImpl userService;
    private final TagServiceImpl tagService;

    public CacheWarmupScheduler(
            RedissonClient redissonClient,
            CacheProperties cacheProperties,
            UserServiceImpl userService,
            TagServiceImpl tagService) {
        this.redissonClient = redissonClient;
        this.cacheProperties = cacheProperties;
        this.userService = userService;
        this.tagService = tagService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupAfterStartup() {
        if (!cacheProperties.isEnabled() || !cacheProperties.getWarmup().isEnabled()) {
            return;
        }
        runWithLock(WARMUP_LOCK, this::refreshAll);
    }

    @Scheduled(
            fixedDelayString = "${matchmate.cache.schedule.recommendation-refresh:3m}",
            initialDelayString = "30s"
    )
    public void refreshRecommendations() {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        runWithLock(RECOMMENDATION_JOB_LOCK, this::refreshUserCollections);
    }

    @Scheduled(
            fixedDelayString = "${matchmate.cache.schedule.category-refresh:1h}",
            initialDelayString = "1m"
    )
    public void refreshCategories() {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        runWithLock(CATEGORY_JOB_LOCK, tagService::refreshCategoriesCache);
    }

    private void refreshAll() {
        tagService.refreshCategoriesCache();
        refreshUserCollections();
        log.info("Redis cache warmup completed");
    }

    private void refreshUserCollections() {
        // The mobile home page calls the empty search endpoint.
        userService.refreshSearchCache(List.of());
        for (Integer limit : cacheProperties.getWarmup().getRecommendationLimits()) {
            if (limit != null) {
                userService.refreshRecommendationCache(limit);
            }
        }
    }

    private void runWithLock(String lockName, Runnable task) {
        RLock lock = null;
        boolean locked = false;
        try {
            lock = redissonClient.getLock(lockName);
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (locked) {
                task.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Distributed cache task failed: lock={}", lockName, e);
        } finally {
            if (locked && lock != null) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (RuntimeException e) {
                    log.warn("Failed to release distributed task lock: lock={}", lockName, e);
                }
            }
        }
    }
}
