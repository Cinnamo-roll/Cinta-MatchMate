package com.cinoo.matchmateserver.card.service.support;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CardRedisLockExecutor {

    private static final String DEFAULT_BUSY_MESSAGE = "操作太频繁，请稍后再试";

    private final RedissonClient redissonClient;

    public void run(String lockKey, long waitSeconds, Runnable action) {
        call(lockKey, waitSeconds, ErrorCode.SYSTEM_ERROR, DEFAULT_BUSY_MESSAGE, () -> {
            action.run();
            return null;
        });
    }

    public <T> T call(String lockKey, long waitSeconds, Supplier<T> action) {
        return call(lockKey, waitSeconds, ErrorCode.SYSTEM_ERROR, DEFAULT_BUSY_MESSAGE, action);
    }

    public <T> T call(
            String lockKey,
            long waitSeconds,
            ErrorCode lockFailureCode,
            String lockFailureMessage,
            Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(waitSeconds, TimeUnit.SECONDS)) {
                throw buildLockFailure(lockFailureCode, lockFailureMessage);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        } finally {
            releaseLockAfterTransaction(lock);
        }
    }

    private BusinessException buildLockFailure(ErrorCode errorCode, String message) {
        if (message == null || message.isBlank()) {
            return new BusinessException(errorCode);
        }
        return new BusinessException(errorCode, message);
    }

    private void releaseLockAfterTransaction(RLock lock) {
        if (!lock.isHeldByCurrentThread()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            lock.unlock();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
    }
}
