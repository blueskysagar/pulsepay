package com.pulsepay.wallet_service.service.impl;

import com.pulsepay.wallet_service.entity.IdempotencyRecord;
import com.pulsepay.wallet_service.enums.IdempotencyOperation;
import com.pulsepay.wallet_service.enums.IdempotencyStatus;
import com.pulsepay.wallet_service.exception.IdempotencyKeyReuseException;
import com.pulsepay.wallet_service.exception.IdempotencyRequestInProgressException;
import com.pulsepay.wallet_service.repository.IdempotencyRecordRepository;
import com.pulsepay.wallet_service.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    @Transactional(readOnly = true)
    @Override
    /* * Look for an existing idempotency record belonging to this * account, request key, and business operation. * * We include the account ID because the same idempotency key * should not accidentally be shared across different accounts. * * We include the operation because the same key may conceptually * be used for different types of operations. */
    public Optional<IdempotencyRecord> findExisting(UUID accountId, String idempotencyKey, IdempotencyOperation operation){
        return idempotencyRecordRepository.findByAccountIdAndIdempotencyKeyAndOperation(accountId, idempotencyKey, operation);

    }
    @Override
    public IdempotencyRecord createProcessingRecord(UUID accountId, String idempotencyKey, IdempotencyOperation operation, String requestFingerprint){
        /*
         * IdempotencyRecord is an internal persistence object rather than
         * an API DTO, so there is no DTO/entity mapping boundary here.
         *
         * Builder gives us an explicit and readable construction of the
         * internal entity while avoiding a long positional constructor.
         */
        IdempotencyRecord record = IdempotencyRecord.builder()
                .accountId(accountId)
                .idempotencyKey(idempotencyKey)
                .operation(operation)
                .requestFingerprint(requestFingerprint)
                .status(IdempotencyStatus.PROCESSING)
                .build();

        /*
         * Persist the idempotency claim.
         *
         * The database uniqueness constraint is the final concurrency
         * protection if two requests attempt to create the same key
         * simultaneously.
         */
        return idempotencyRecordRepository.save(record);

    }
    @Override
    public void validateRequestFingerprint(IdempotencyRecord record, String requestFingerprint){
        if(!record.getRequestFingerprint().equals(requestFingerprint)){
            throw new IdempotencyKeyReuseException();
        }
    }
    @Override
    public void validateNotProcessing(IdempotencyRecord record){
        if(record.getStatus() == IdempotencyStatus.PROCESSING){
            throw new IdempotencyRequestInProgressException();
        }
    }
    /*
     * Mark a previously processing request as completed.
     *
     * This transition happens only after the protected business
     * operation has successfully finished.
     */
    @Override
    public void markCompleted(IdempotencyRecord record, UUID resourceId){
        /*
         * Change only the lifecycle state here.
         *
         * The actual business operation is owned by the corresponding
         * business service, such as WalletService.
         */
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResourceId(resourceId);
        /*
         * Persist the state transition so future retries can see
         * that the original request already completed.
         */
        idempotencyRecordRepository.save(record);
    }
}
