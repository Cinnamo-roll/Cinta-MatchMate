package com.cinoo.matchmateserver.infrastructure.cache;

import com.cinoo.matchmateserver.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedCacheServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private DistributedCacheService cacheService;

    @BeforeEach
    void setUp() {
        CacheProperties properties = new CacheProperties();
        properties.setLockWait(Duration.ofMillis(100));
        cacheService = new DistributedCacheService(cacheManager, redissonClient, properties);
        lenient().when(cacheManager.getCache(CacheNames.USER_SEARCHES)).thenReturn(cache);
    }

    @Test
    void cacheHitDoesNotAcquireDistributedLock() {
        when(cache.get("home")).thenReturn(() -> "cached");
        Supplier<String> loader = mock(Supplier.class);

        String result = cacheService.get(CacheNames.USER_SEARCHES, "home", loader);

        assertEquals("cached", result);
        verifyNoInteractions(redissonClient, loader);
    }

    @Test
    void cacheMissLoadsOnceWhileHoldingDistributedLock() throws Exception {
        when(cache.get("home")).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = cacheService.get(
                CacheNames.USER_SEARCHES,
                "home",
                () -> "database"
        );

        assertEquals("database", result);
        verify(cache).put("home", "database");
        verify(lock).unlock();
    }

    @Test
    void redisLockFailureFallsBackToDatabase() {
        when(cache.get("home")).thenReturn(null);
        when(redissonClient.getLock(anyString()))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        String result = cacheService.get(
                CacheNames.USER_SEARCHES,
                "home",
                () -> "database"
        );

        assertEquals("database", result);
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void unlockFailureDoesNotDiscardLoadedValue() throws Exception {
        when(cache.get("home")).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new IllegalStateException("Redis unavailable")).when(lock).unlock();

        String result = cacheService.get(
                CacheNames.USER_SEARCHES,
                "home",
                () -> "database"
        );

        assertEquals("database", result);
        verify(cache).put("home", "database");
    }

    @Test
    void loaderFailureIsNotRetriedOrReportedAsRedisFailure() throws Exception {
        when(cache.get("home")).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("Database unavailable");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> cacheService.get(CacheNames.USER_SEARCHES, "home", () -> {
                    attempts.incrementAndGet();
                    throw failure;
                })
        );

        assertSame(failure, thrown);
        assertEquals(1, attempts.get());
        verify(lock).unlock();
        verify(cache, never()).put(anyString(), any());
    }
}
