package com.pulsepay.wallet_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class PersistenceExceptionTranslator {
    /*
     * PostgreSQL uses this constraint to guarantee that an account
     * cannot have more than one wallet for the same currency.
     *
     * This name must match the constraint defined by our Flyway
     * migration.
     */
    private static final String ACCOUNT_CURRENCY_CONSTRAINT = "uk_wallet_account_currency";
    private static final String IDEMPOTENCY_ACCOUNT_KEY_OPERATION_CONSTRAINT = "uk_idempotency_account_key_operation";
    /*
     * Translate low-level database integrity failures into
     * application-specific exceptions that the rest of the
     * application can understand.
     */
    public RuntimeException translate(DataIntegrityViolationException exception){
        /*
         * DataIntegrityViolationException is a Spring-level exception
         * that may wrap the actual database exception.
         *
         * getMostSpecificCause() gives us the deepest underlying cause,
         * which is where PostgreSQL provides the constraint information.
         */
        String message = exception.getMostSpecificCause().getMessage();

        /*
         * If PostgreSQL reports our account/currency uniqueness
         * constraint, the actual business meaning is:
         *
         * "This account already owns a wallet for this currency."
         *
         * Convert the database failure into an application-level
         * DuplicateWalletException.
         */
        if(message !=null && message.contains(ACCOUNT_CURRENCY_CONSTRAINT)){
            return new DuplicateWalletException();
        }
        /*
         * We do not recognize this particular integrity violation.
         *
         * Returning the original exception allows the global handler
         * to treat it as an unexpected/unknown persistence failure
         * without incorrectly calling it a duplicate wallet.
         */
        /*
         * Concurrent idempotency claim.
         */
        if(message !=null && message.contains(IDEMPOTENCY_ACCOUNT_KEY_OPERATION_CONSTRAINT)){
            return new IdempotencyRequestAlreadyClaimedException();
        }
        return exception;
    }


}
