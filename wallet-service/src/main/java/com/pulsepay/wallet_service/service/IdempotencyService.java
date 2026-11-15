package com.pulsepay.wallet_service.service;

import com.pulsepay.wallet_service.entity.IdempotencyRecord;
import com.pulsepay.wallet_service.enums.IdempotencyOperation;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyService {
    Optional<IdempotencyRecord> findExisting(UUID accountId, String idempotencyKey, IdempotencyOperation operation);
    IdempotencyRecord createProcessingRecord(UUID accountId, String idempotencyKey, IdempotencyOperation operation, String requestFingerprint);
    void validateRequestFingerprint(IdempotencyRecord record, String requestFingerprint);
    void validateNotProcessing(IdempotencyRecord record);
    void markCompleted(IdempotencyRecord record, UUID resourceId);
}
