package com.group2.fse.transaction_service.service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service managing distributed idempotency locks (SETNX) and response caching.
 */
public interface IdempotencyService {

    /**
     * Attempts atomic acquisition of an idempotency lock via Redis SETNX.
     *
     * @param idempotencyKey the unique client key
     * @param lockTtl duration the lock remains valid
     * @return true if the lock was acquired, false if a concurrent lock already exists
     */
    boolean tryAcquire(String idempotencyKey, Duration lockTtl);

    /**
     * Checks if the key is currently in PROCESSING state.
     */
    boolean isProcessing(String idempotencyKey);

    /**
     * Retrieves the cached completed response payload if available.
     */
    Optional<String> getCachedResponse(String idempotencyKey);

    /**
     * Marks the idempotency key as completed and caches the JSON response.
     */
    void complete(String idempotencyKey, String responseBodyJson, Duration cacheTtl);

    /**
     * Releases the lock in case of processing failure so the client can retry.
     */
    void release(String idempotencyKey);
}
