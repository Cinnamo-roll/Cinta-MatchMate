package com.cinoo.matchmateserver.cache;

import lombok.extern.slf4j.Slf4j;
import com.cinoo.matchmateserver.config.CacheProperties;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache-aside helper with cross-instance locking and database fallback.
 */
@Component
@Slf4j
public class DistributedCacheService {

    private static final String LOCK_PREFIX = "matchmate:lock:cache:";

    private final CacheManager cacheManager;
    private final RedissonClient redissonClient;
    private final boolean enabled;
    private final Duration lockWait;

    public DistributedCacheService(
            CacheManager cacheManager,
            RedissonClient redissonClient,
            CacheProperties cacheProperties) {
        this.cacheManager = cacheManager;
        this.redissonClient = redissonClient;
        this.enabled = cacheProperties.isEnabled();
        this.lockWait = cacheProperties.getLockWait();
    }

    public <T> T get(String cacheName, String key, Supplier<T> loader) {
        if (!enabled) {
            return loader.get();
        }

        T cachedValue = read(cacheName, key);
        if (cachedValue != null) {
            return cachedValue;
        }

        RLock lock = null;
        boolean locked = false;
        try {
            lock = redissonClient.getLock(LOCK_PREFIX + cacheName + ":" + key);
            locked = lock.tryLock(lockWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        } catch (RuntimeException e) {
            log.warn("Redis cache unavailable, falling back to database: cache={}, key={}",
                    cacheName, key, e);
            return loader.get();
        }

        try {
            if (locked) {
                cachedValue = read(cacheName, key);
                if (cachedValue != null) {
                    return cachedValue;
                }
            }

            T loadedValue = loader.get();
            if (locked) {
                put(cacheName, key, loadedValue);
            }
            return loadedValue;
        } finally {
            unlockQuietly(lock, locked);
        }
    }

    public <T> T refresh(String cacheName, String key, Supplier<T> loader) {
        T loadedValue = loader.get();
        if (enabled) {
            put(cacheName, key, loadedValue);
        }
        return loadedValue;
    }

    public void evict(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to evict Redis cache: cache={}, key={}", cacheName, key, e);
        }
    }

    public void clear(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException e) {
            log.warn("Failed to clear Redis cache: cache={}", cacheName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T read(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            Cache.ValueWrapper wrapper = cache == null ? null : cache.get(key);
            return wrapper == null ? null : (T) wrapper.get();
        } catch (RuntimeException e) {
            log.warn("Failed to read Redis cache: cache={}, key={}", cacheName, key, e);
            return null;
        }
    }

    private void put(String cacheName, String key, Object value) {
        if (value == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to write Redis cache: cache={}, key={}", cacheName, key, e);
        }
    }

    private void unlockQuietly(RLock lock, boolean locked) {
        if (!locked || lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException e) {
            log.warn("Failed to release Redis cache lock: lock={}", lock.getName(), e);
        }
    }
}
