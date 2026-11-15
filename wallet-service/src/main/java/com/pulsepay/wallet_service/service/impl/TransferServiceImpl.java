package com.pulsepay.wallet_service.service.impl;

import com.pulsepay.wallet_service.dto.event.TransferCompletedEvent;
import com.pulsepay.wallet_service.dto.request.CreateTransferRequest;
import com.pulsepay.wallet_service.dto.response.CreateTransferResponse;
import com.pulsepay.wallet_service.entity.IdempotencyRecord;
import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.entity.Transfer;
import com.pulsepay.wallet_service.entity.Wallet;
import com.pulsepay.wallet_service.enums.IdempotencyOperation;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import com.pulsepay.wallet_service.enums.WalletStatus;
import com.pulsepay.wallet_service.exception.InvalidTransferException;
import com.pulsepay.wallet_service.exception.WalletNotActiveException;
import com.pulsepay.wallet_service.exception.WalletNotFoundException;
import com.pulsepay.wallet_service.idempotency.RequestFingerPrintGenerator;
import com.pulsepay.wallet_service.mapper.TransferMapper;
import com.pulsepay.wallet_service.repository.OutboxEventRepository;
import com.pulsepay.wallet_service.repository.TransferRepository;
import com.pulsepay.wallet_service.repository.WalletRepository;
import com.pulsepay.wallet_service.service.IdempotencyService;
import com.pulsepay.wallet_service.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {


    private final TransferRepository transferRepository;
    private final WalletRepository walletRepository;
    private final TransferMapper transferMapper;
    private final IdempotencyService idempotencyService;
    private final RequestFingerPrintGenerator requestFingerPrintGenerator;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    @Override
    @Transactional
    public CreateTransferResponse createTransfer(
            CreateTransferRequest request,
            UUID accountId,
            String idempotencyKey
    ) {

        // ============================================================
        // STEP 1 — GENERATE REQUEST FINGERPRINT
        // ============================================================

        /*
         * WHY:
         *
         * The idempotency key alone is not enough.
         *
         * We also remember WHAT request that key was originally
         * used for.
         *
         * Example:
         *
         * KEY = ABC123
         *
         * source      = wallet-1
         * destination = wallet-2
         * amount      = 50
         *
         * If somebody later reuses ABC123 for amount = 500,
         * the fingerprint will be different and we reject it.
         */
        String requestFingerprint =
                requestFingerPrintGenerator.generateTransferFingerprint(
                        request.sourceWalletId(),
                        request.destinationWalletId(),
                        request.amount()
                );

        // ============================================================
        // STEP 2 — CHECK WHETHER THIS REQUEST ALREADY EXISTS
        // ============================================================

        /*
         * WHY:
         *
         * This is the first idempotency decision.
         *
         * If the key already exists, we DO NOT execute the transfer
         * again.
         *
         * We either:
         *
         * - reject the request because the key was reused differently
         * - reject because the original request is still processing
         * - replay the already completed transfer
         */
        Optional<IdempotencyRecord> existingRecord =
                idempotencyService.findExisting(
                        accountId,
                        idempotencyKey,
                        IdempotencyOperation.TRANSFER
                );

        // ============================================================
        // STEP 3 — HANDLE EXISTING IDEMPOTENCY RECORD
        // ============================================================

        if (existingRecord.isPresent()) {

            IdempotencyRecord record = existingRecord.get();

            /*
             * First make sure the same idempotency key is being used
             * with the exact same logical request.
             */
            idempotencyService.validateRequestFingerprint(
                    record,
                    requestFingerprint
            );

            /*
             * If the original request is still executing,
             * don't execute another transfer simultaneously.
             */
            idempotencyService.validateNotProcessing(record);

            /*
             * A COMPLETED transfer must have the Transfer ID stored
             * as resourceId.
             */
            if (record.getResourceId() == null) {
                throw new IllegalStateException(
                        "Completed idempotency record has no resource ID."
                );
            }

            /*
             * IMPORTANT:
             *
             * resourceId here is the TRANSFER ID.
             *
             * It is NOT the wallet ID.
             *
             * We use it to retrieve the original completed transfer.
             */
            Transfer existingTransfer =
                    transferRepository.findById(record.getResourceId())
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Transfer referenced by idempotency record was not found."
                                    )
                            );

            /*
             * REPLAY:
             *
             * We return the original transfer.
             *
             * We DO NOT:
             *
             * sourceWallet.debit(...)
             * destinationWallet.credit(...)
             *
             * again.
             *
             * This is the heart of idempotency.
             */
            return transferMapper.toCreateTransferResponse(
                    existingTransfer
            );
        }

        // ============================================================
        // STEP 4 — CLAIM THE IDEMPOTENCY KEY
        // ============================================================

        /*
         * WHY:
         *
         * No existing record means this is the first request
         * using this key for this operation/account.
         *
         * We create a PROCESSING record BEFORE moving money.
         *
         * This gives us a durable claim on the request.
         *
         * The database unique constraint protects us if two
         * requests arrive concurrently with the same key.
         */
        IdempotencyRecord idempotencyRecord =
                idempotencyService.createProcessingRecord(
                        accountId,
                        idempotencyKey,
                        IdempotencyOperation.TRANSFER,
                        requestFingerprint
                );

        // ============================================================
        // STEP 5 — FIND SOURCE WALLET
        // ============================================================

        /*
         * The source wallet MUST belong to the authenticated account.
         *
         * This prevents:
         *
         * User A → attempting to debit User B's wallet.
         */
        Wallet sourceWallet =
                walletRepository
                        .findByIdAndAccountId(
                                request.sourceWalletId(),
                                accountId
                        )
                        .orElseThrow(() ->
                                new WalletNotFoundException(
                                        request.sourceWalletId()
                                )
                        );

        // ============================================================
        // STEP 6 — FIND DESTINATION WALLET
        // ============================================================

        /*
         * Destination ownership is intentionally NOT checked.
         *
         * A user must be allowed to send money to another user's wallet.
         */
        Wallet destinationWallet =
                walletRepository
                        .findById(request.destinationWalletId())
                        .orElseThrow(() ->
                                new WalletNotFoundException(
                                        request.destinationWalletId()
                                )
                        );

        // ============================================================
        // STEP 7 — PREVENT SELF TRANSFER
        // ============================================================

        if (sourceWallet.getId().equals(destinationWallet.getId())) {

            throw new InvalidTransferException(
                    "Source wallet and destination wallet cannot be the same"
            );
        }

        // ============================================================
        // STEP 8 — VALIDATE CURRENCY
        // ============================================================

        /*
         * No FX conversion is supported yet.
         *
         * Therefore both wallets must use the same currency.
         */
        if (!sourceWallet.getCurrency()
                .equals(destinationWallet.getCurrency())) {

            throw new InvalidTransferException(
                    "Source and destination wallets must use the same currency"
            );
        }

        // ============================================================
        // STEP 9 — CREATE TRANSFER ENTITY
        // ============================================================

        /*
         * Currency comes from the trusted source wallet.
         *
         * It does NOT come from the client.
         */
        Transfer transfer =
                transferMapper.toEntity(
                        request,
                        sourceWallet.getCurrency()
                );
        // Check wallet status BEFORE changing any balance.
//
// WHY:
// We must reject the transfer before debiting the source.
// Otherwise a suspended destination could result in us
// checking/debiting the source first and returning the wrong
// business error.

        if (sourceWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException(
                    sourceWallet.getStatus()
            );
        }

        if (destinationWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException(
                    destinationWallet.getStatus()
            );
        }


        // ============================================================
        // STEP 10 — DEBIT SOURCE
        // ============================================================

        /*
         * Wallet owns the balance business rules.
         *
         * debit() checks:
         *
         * - amount > 0
         * - wallet ACTIVE
         * - sufficient balance
         * - balance cannot become negative
         */
        sourceWallet.debit(request.amount());

        // ============================================================
        // STEP 11 — CREDIT DESTINATION
        // ============================================================

        /*
         * Wallet also owns the credit rules.
         */
        destinationWallet.credit(request.amount());

        // ============================================================
        // STEP 12 — MARK TRANSFER COMPLETED
        // ============================================================

        /*
         * Both wallet operations succeeded.
         *
         * Therefore the transfer can move from:
         *
         * PENDING → COMPLETED
         */
        transfer.markCompleted();

        // ============================================================
        // STEP 13 — PERSIST TRANSFER
        // ============================================================

        /*
         * Save the transfer FIRST.
         *
         * We need its ID as the resourceId of the idempotency record.
         */
        Transfer savedTransfer =
                transferRepository.save(transfer);
        /* * OUTBOX EVENT * * WHY: * * The transfer and the event must be stored inside * the SAME database transaction. * * If the transfer commits successfully, the outbox event * must also be committed. * * If anything fails, both are rolled back. */
        // Create the event that will eventually be published to Kafka.
        TransferCompletedEvent event = new TransferCompletedEvent(
                savedTransfer.getId(),
                savedTransfer.getSourceWalletId(),
                savedTransfer.getDestinationWalletId(),
                sourceWallet.getAccountId(),
                destinationWallet.getAccountId(),

                savedTransfer.getAmount(),
                savedTransfer.getCurrency()
        );
        /* * Convert the event into JSON because the outbox table * stores the event payload as JSONB. */
        String payload;
        try{
            payload = objectMapper.writeValueAsString(event);

        }catch (Exception ex){
            /* * WHY: * * If we cannot create the event payload, we must NOT * complete the transfer transaction. * * Throwing the exception causes @Transactional to roll back. */
            throw new IllegalStateException("Failed to serialize transfer completed event",ex);
        }
        /* * Create the durable outbox message. * * PENDING means: * * "The business transaction succeeded, but this event * has not yet been delivered to Kafka." */
        OutboxEvent outboxEvent =
                OutboxEvent.builder()
                        .aggregateType("TRANSFER")
                                .aggregateId(savedTransfer.getId())
                                        .eventType("TRANSFER_COMPLETED")
                                                .payload(payload)
                                                        .status(OutboxEventStatus.PENDING)
                                                                .build();
//        * * Store the event in PostgreSQL. * * This happens inside the same @Transactional method * as the transfer. */
        outboxEventRepository.save(outboxEvent);



        // ============================================================
        // STEP 14 — MARK IDEMPOTENCY REQUEST COMPLETED
        // ============================================================

        /*
         * Now we remember:
         *
         * idempotency key
         *        ↓
         * original transfer ID
         *
         * Future retries can therefore return this exact transfer
         * without moving money again.
         */
        idempotencyService.markCompleted(
                idempotencyRecord,
                savedTransfer.getId()
        );

        // ============================================================
        // STEP 15 — RETURN RESPONSE
        // ============================================================

        return transferMapper.toCreateTransferResponse(
                savedTransfer
        );
    }
    @Override
    @Transactional
    public CreateTransferResponse createInternalTransfer(
            CreateTransferRequest request,
            String idempotencyKey
    ){
        Wallet sourceWallet =
                walletRepository.findById(request.sourceWalletId())
                        .orElseThrow(() ->
                                new WalletNotFoundException(
                                        request.sourceWalletId()
                                )
                        );

        UUID accountId = sourceWallet.getAccountId();

        return createTransfer(
                request,
                accountId,
                idempotencyKey
        );
    }
}
