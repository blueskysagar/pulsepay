package com.pulsepay.wallet_service.service.impl;

import com.pulsepay.wallet_service.dto.request.CreateWalletRequest;
import com.pulsepay.wallet_service.dto.response.CreateWalletResponse;
import com.pulsepay.wallet_service.dto.response.WalletPageResponse;
import com.pulsepay.wallet_service.entity.IdempotencyRecord;
import com.pulsepay.wallet_service.entity.Wallet;
import com.pulsepay.wallet_service.enums.IdempotencyOperation;
import com.pulsepay.wallet_service.enums.WalletStatus;
import com.pulsepay.wallet_service.exception.DuplicateWalletException;
import com.pulsepay.wallet_service.exception.UnsupportedCurrencyException;
import com.pulsepay.wallet_service.exception.WalletNotFoundException;
import com.pulsepay.wallet_service.idempotency.RequestFingerPrintGenerator;
import com.pulsepay.wallet_service.mapper.WalletMapper;
import com.pulsepay.wallet_service.normalization.CurrencyNormalizer;
import com.pulsepay.wallet_service.repository.WalletRepository;
import com.pulsepay.wallet_service.service.IdempotencyService;
import com.pulsepay.wallet_service.service.WalletService;
import com.pulsepay.wallet_service.service.cache.WalletCacheService;
import com.pulsepay.wallet_service.validation.CurrencyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    private final WalletCacheService walletCacheService;
    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final CurrencyNormalizer currencyNormalizer;
    private final CurrencyValidator currencyValidator;
    private final IdempotencyService idempotencyService;
    private final RequestFingerPrintGenerator requestFingerPrintGenerator;
    @Override
    @Transactional
    public CreateWalletResponse createWallet(UUID accountId, CreateWalletRequest request) {
        String currency = currencyNormalizer.normalize(request.getCurrency());
        if(!currencyValidator.isSupported(currency)){
            throw new UnsupportedCurrencyException("Unsupported currency: " + currency);
        }
        if (walletRepository.existsByAccountIdAndCurrency(accountId, currency)) {
            throw new DuplicateWalletException(accountId, currency);
        }
                 // Convert the API request into our persistence entity.
// MapStruct handles the DTO -> entity field mapping.
        Wallet wallet = walletMapper.toEntity(request);
        wallet.setCurrency(currency);
        // These values are NOT taken from the client.
// The server controls ownership and the wallet's initial state.
        wallet.setAccountId(accountId);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setStatus(WalletStatus.ACTIVE);
        /*
         * Persist the wallet after all server-controlled fields have been
         * established.
         *
         * The database unique constraint on (account_id, currency) remains
         * the final protection against duplicate wallets under concurrent requests.
         */
        Wallet savedWallet = walletRepository.save(wallet);
        //Converting persisted entity into API response DTO
        return walletMapper.toCreateWalletResponse(savedWallet);


    }
    @Override
    @Transactional(readOnly = true)
    public CreateWalletResponse getWallet(UUID walletId, UUID accountId){
        //Check redis first
        CreateWalletResponse cachedWallet = walletCacheService.get(accountId,walletId);
        //Cache hit return immediately
        if(cachedWallet != null){
            return cachedWallet;

        }
        // 3. Cache MISS → PostgreSQL remains the source of truth.
        Wallet wallet = walletRepository.findByIdAndAccountId(walletId,accountId)
                .orElseThrow(() ->
                        new WalletNotFoundException(walletId)
                );
        //Convert database entity to API DTO
        CreateWalletResponse response =
                walletMapper.toCreateWalletResponse(wallet);
        //Store fresh DTO in redis for future reads
        walletCacheService.put(
                accountId,
                walletId,
                response
        );
        return response;
    }
    @Override
    @Transactional(readOnly = true)
    public WalletPageResponse getWallets(UUID accountId, Pageable pageable){
        Page<Wallet> wallets = walletRepository.findAllByAccountId(accountId, pageable);
        Page<CreateWalletResponse> walletResponses = wallets.map(walletMapper::toCreateWalletResponse);
        /*
         * Convert Spring Data's Page abstraction into our own
         * API response contract.
         *
         * This prevents Spring Data implementation details from
         * becoming part of our public API.
         */
        return new WalletPageResponse(
                walletResponses.getContent(),
                walletResponses.getNumber(),
                walletResponses.getSize(),
                walletResponses.getTotalElements(),
                walletResponses.getTotalPages()
        );


    }
    @Transactional
    @Override
    public CreateWalletResponse updateWalletStatus(UUID walletId, UUID accountId, WalletStatus newStatus){
        /*
     * Query by both wallet ID and account ID.
     *
     * The account ID comes from the authenticated security context,
     * not from arbitrary client input.
                *
                * This prevents Account A from modifying Account B's wallet.
                */
        Wallet wallet = walletRepository.findByIdAndAccountId(walletId,accountId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        /*
         * Delegate the lifecycle invariant to the Wallet domain object.
         *
         * The service orchestrates the use case; it does not duplicate
         * the rules for which status transitions are legal.
         */
        wallet.changeStatus(newStatus);
        /*
         * Because the method runs inside a transaction, Hibernate tracks
         * the managed Wallet entity and detects the status change.
         *
         * The database UPDATE is synchronized when the transaction flushes.
         */
        Wallet savedWallet = walletRepository.save(wallet);
        walletCacheService.evict(accountId, walletId);
        return walletMapper.toCreateWalletResponse(savedWallet);


    }
    @Transactional
    @Override
    public CreateWalletResponse creditWallet(UUID walletId, UUID accountId, BigDecimal amount, String idempotencyKey){
        String requestFingerprint = requestFingerPrintGenerator.generateCreditFingerprint(walletId, amount);
        Optional<IdempotencyRecord> existingRecord =
                idempotencyService.findExisting(
                        accountId,
                        idempotencyKey,
                        IdempotencyOperation.CREDIT
                );
        if(existingRecord.isPresent()){
            IdempotencyRecord record = existingRecord.get();
            idempotencyService.validateRequestFingerprint(record, requestFingerprint);
            idempotencyService.validateNotProcessing(record);
            /*
             * At this point the record is expected to be COMPLETED.
             *
             * A completed idempotency record must contain the
             * resource affected by the original operation.
             *
             * For CREDIT_WALLET, resourceId represents the wallet.
             */


            /*
             * At this point the record should represent a
             * successfully completed request.
             *
             * A COMPLETED record must have a resourceId.
             *
             * For CREDIT_WALLET:
             *
             * resourceId = wallet ID
             */
            if (record.getResourceId() == null) {

                throw new IllegalStateException(
                        "Completed idempotency record has no resource ID."
                );
            }


            /*
             * ----------------------------------------------------
             * REPLAY THE PREVIOUS RESULT
             * ----------------------------------------------------
             *
             * The original request already credited the wallet.
             *
             * Therefore:
             *
             * DO NOT call wallet.credit() again.
             *
             * Instead, retrieve the wallet associated with the
             * original successful operation.
             */
            Wallet wallet =
                    walletRepository
                            .findByIdAndAccountId(
                                    record.getResourceId(),
                                    accountId
                            )
                            .orElseThrow(() ->
                                    new WalletNotFoundException(
                                            record.getResourceId()
                                    )
                            );


            /*
             * Return the current representation of the wallet.
             *
             * We are replaying the logical result, not performing
             * the financial operation again.
             */
            return walletMapper.toCreateWalletResponse(wallet);
        }
        // ------------------------------------------------------------
        // 4. No existing record.
        //
        // Claim the idempotency key BEFORE performing the business
        // operation.
        // ------------------------------------------------------------
        IdempotencyRecord idempotencyRecord =
                idempotencyService.createProcessingRecord(
                        accountId,
                        idempotencyKey,
                        IdempotencyOperation.CREDIT,
                        requestFingerprint
                );


        /*
         * Query by both wallet ID and account ID.
         *
         * We never trust a client-provided account ID to determine ownership.
         * The account ID must ultimately come from the authenticated caller.
         */
        Wallet wallet = walletRepository.findByIdAndAccountId(walletId, accountId).orElseThrow(()  ->
                new WalletNotFoundException(walletId)
        );
        /*
         * Delegate the monetary invariant to the Wallet domain object.
         *
         * The service coordinates the use case; Wallet controls how its
         * own balance is allowed to change.
         */
        wallet.credit(amount);
        /*
         * Hibernate tracks the managed Wallet entity inside the transaction.
         *
         * The @Version field allows Hibernate to detect whether another
         * transaction changed this wallet after we originally read it.
         */
        Wallet savedWallet = walletRepository.save(wallet);
        // ------------------------------------------------------------
        // 7. Mark the idempotency request as successfully completed.
        //
        // Store the wallet ID so a retry knows which resource was
        // affected by the original request.
        // ------------------------------------------------------------
        idempotencyService.markCompleted(
                idempotencyRecord,
                savedWallet.getId()
        );
        walletCacheService.evict(accountId, walletId);
        return walletMapper.toCreateWalletResponse(savedWallet);


    }







}
