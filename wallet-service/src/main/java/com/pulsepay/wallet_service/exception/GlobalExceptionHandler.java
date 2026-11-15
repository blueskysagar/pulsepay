package com.pulsepay.wallet_service.exception;

import com.pulsepay.wallet_service.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice // this class is centralized place for handling all the exceptions thrown by my Rest Controller.
@RequiredArgsConstructor

public class GlobalExceptionHandler {
    private final PersistenceExceptionTranslator persistenceExceptionTranslator;
    @ExceptionHandler(UnsupportedCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedCurrencyException(UnsupportedCurrencyException exception, HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                "UNSUPPORTED_CURRENCY",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );


        return ResponseEntity
                .badRequest()
                .body(errorResponse);
    }
    @ExceptionHandler(WalletNotFoundException.class)
            public ResponseEntity<ErrorResponse> handleWalletNotFoundException(WalletNotFoundException exception, HttpServletRequest request){
        ErrorResponse errorResponse = new ErrorResponse(
                "WALLET_NOT_FOUND",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()

        );
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }
    @ExceptionHandler(DuplicateWalletException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateWalletException(DuplicateWalletException exception, HttpServletRequest request){
        ErrorResponse errorResponse =  new ErrorResponse(
                "WALLET_ALREADY_EXISTS",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException exception, HttpServletRequest request) {

        RuntimeException translatedException = persistenceExceptionTranslator.translate(exception);
        if (translatedException instanceof DuplicateWalletException duplicateException) {

            ErrorResponse duplicateWalletResponse = new ErrorResponse(
                    duplicateException.getErrorCode().name(),
                    duplicateException.getMessage(),
                    Instant.now(),
                    request.getRequestURI()
            );
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(duplicateWalletResponse);
        }
        /*
         * --------------------------------------------------------
         * IDEMPOTENCY REQUEST ALREADY CLAIMED
         * --------------------------------------------------------
         *
         * PostgreSQL reported:
         *
         * uk_idempotency_account_key_operation
         *
         * This happens when concurrent requests race to create
         * the same idempotency record.
         *
         * The persistence translator converts that database
         * failure into IdempotencyRequestAlreadyClaimedException.
         */
        if (translatedException
                instanceof IdempotencyRequestAlreadyClaimedException idempotencyException) {

            ErrorResponse idempotencyResponse =
                    new ErrorResponse(
                            "IDEMPOTENCY_REQUEST_ALREADY_CLAIMED",
                            idempotencyException.getMessage(),
                            Instant.now(),
                            request.getRequestURI()
                    );

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(idempotencyResponse);
        }

        /*
         * The translator only knows how to translate database constraints
         * that have an explicit business meaning in the Wallet Service.
         *
         * We must not expose raw database/Hibernate/PostgreSQL details
         * to the API client for unknown integrity violations.
         */
        ErrorResponse integrityViolationResponse = new ErrorResponse(
                "DATA_INTEGRITY_VIOLATION",
                "The request couldnot be completed.",
                Instant.now(),
                request.getRequestURI()

        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(integrityViolationResponse);


    }
    @ExceptionHandler(WalletNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotActive(
            WalletNotActiveException exception,
            HttpServletRequest request
    ) {

        /*
         * Convert the domain exception into our stable API error contract.
         *
         * The domain layer does not know anything about HTTP.
         * This web-layer handler is responsible for that translation.
         */
        ErrorResponse errorResponse = new ErrorResponse(
                "WALLET_NOT_ACTIVE",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {

        /*
         * An optimistic-locking failure means another transaction modified
         * the same wallet after we originally read it.
         *
         * This is a concurrency conflict, not an invalid request.
         *
         * We therefore return HTTP 409 Conflict instead of exposing the
         * underlying persistence exception to the client.
         */
        ErrorResponse errorResponse = new ErrorResponse(
                "WALLET_CONCURRENT_MODIFICATION",
                "The wallet was modified by another request. Please retry the operation.",
                Instant.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }
    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyReuse(
            IdempotencyKeyReuseException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                "IDEMPOTENCY_KEY_REUSE",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }


    @ExceptionHandler(IdempotencyRequestInProgressException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyRequestInProgress(
            IdempotencyRequestInProgressException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }


    @ExceptionHandler(IdempotencyRequestAlreadyClaimedException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyRequestAlreadyClaimed(
            IdempotencyRequestAlreadyClaimedException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                "IDEMPOTENCY_REQUEST_ALREADY_CLAIMED",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }
    // WHY:
// InvalidTransferException represents a transfer request that
// violates a business rule, such as:
// - source and destination are the same wallet
// - currencies do not match
//
// These are client/business validation errors, so we return HTTP 400.
    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransfer(
            InvalidTransferException exception,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INVALID_TRANSFER",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException exception,
            HttpServletRequest request
    ) {

        /*
         * WHY:
         *
         * The request is syntactically valid, but the wallet does not
         * currently have enough money to complete the transfer.
         *
         * This is a business/resource-state conflict, so we return
         * HTTP 409 Conflict.
         *
         * The domain exception does not know about HTTP.
         * This handler translates it into our API error contract.
         */
        ErrorResponse errorResponse = new ErrorResponse(
                "INSUFFICIENT_BALANCE",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }


}
