package com.pulsepay.wallet_service.repository;

import com.pulsepay.wallet_service.entity.IdempotencyRecord;
import com.pulsepay.wallet_service.enums.IdempotencyOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByAccountIdAndIdempotencyKeyAndOperation(UUID accountId, String idempotencyKey, IdempotencyOperation operation);

}
