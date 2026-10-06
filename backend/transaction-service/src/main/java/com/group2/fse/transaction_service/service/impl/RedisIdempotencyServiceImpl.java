package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Task: FIN-303
 * Redis implementation of IdempotencyService utilizing atomic SETNX mutex locks
 * and separate response caching so the 60s in-flight lock is never overwritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisIdempotencyServiceImpl implements IdempotencyService {

    private static final String LOCK_PREFIX = "idempotency:remittance:";
    private static final String RESPONSE_PREFIX = "idempotency:remittance:response:";
    private static final String STATUS_PROCESSING = "PROCESSING";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean tryAcquire(String idempotencyKey, Duration lockTtl) {
        String key = LOCK_PREFIX + idempotencyKey.trim();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, STATUS_PROCESSING, lockTtl);
        boolean success = Boolean.TRUE.equals(acquired);
        if (success) {
            log.debug("Idempotency lock acquired for key: {}", idempotencyKey);
        } else {
            log.warn("Duplicate request detected for idempotency key: {}", idempotencyKey);
        }
        return success;
    }

    @Override
    public boolean isProcessing(String idempotencyKey) {
        String key = LOCK_PREFIX + idempotencyKey.trim();
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    @Override
    public Optional<String> getCachedResponse(String idempotencyKey) {
        String key = RESPONSE_PREFIX + idempotencyKey.trim();
        String val = stringRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(val);
    }

    @Override
    public void complete(String idempotencyKey, String responseBodyJson, Duration cacheTtl) {
        // Cache response under separate key so the 60s mutex lock is preserved for the full TTL window
        String key = RESPONSE_PREFIX + idempotencyKey.trim();
        stringRedisTemplate.opsForValue().set(key, responseBodyJson, cacheTtl);
        log.info("Idempotency response cached for key: {}", idempotencyKey);
    }

    @Override
    public void release(String idempotencyKey) {
        String lockKey = LOCK_PREFIX + idempotencyKey.trim();
        String responseKey = RESPONSE_PREFIX + idempotencyKey.trim();
        stringRedisTemplate.delete(lockKey);
        stringRedisTemplate.delete(responseKey);
        log.warn("Idempotency lock and cache released due to failure: {}", idempotencyKey);
    }
}
