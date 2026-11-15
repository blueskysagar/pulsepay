package com.pulsepay.wallet_service.idempotency;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class RequestFingerPrintGenerator {
    /*
     * Generate a deterministic fingerprint for a wallet credit request.
     *
     * The fingerprint represents the business input that must remain
     * identical when an idempotency key is reused.
     */
    public String generateCreditFingerprint(UUID walletId, BigDecimal amount){
        /*
         * BigDecimal can have multiple textual representations for the
         * same monetary value.
         *
         * Example:
         *
         * 100
         * 100.0
         * 100.00
         *
         * We normalize the representation before hashing so that
         * equivalent monetary values produce the same fingerprint.
         */
        String canonicalAmount = amount.stripTrailingZeros().toPlainString();
        /*
         * Build a deterministic representation of the business request.
         *
         * The field names are included intentionally so that the
         * representation is explicit and unambiguous.
         */
        String canonicalRequest = "walletId=" + walletId + "&amount=" + canonicalAmount;
        /*
         * SHA-256 produces a fixed 256-bit digest.
         *
         * We store the result as hexadecimal, which produces
         * exactly 64 characters.
         */
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[]hash = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)); //We convert canonicalRequest into bytes thats also UTF_8 otherwise same string would  generate different hashes which we dont want.
            return HexFormat.of().formatHex(hash);


        } catch (NoSuchAlgorithmException exception){
            /*
             * SHA-256 is required by the Java platform.
             *
             * Therefore this should never happen in a normal JVM.
             */
            throw new IllegalStateException("SHA-256 algorithm is not available. ", exception);
        }

    }
    public String generateTransferFingerprint(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount
    ){
        /*
         * WHY:
         *
         * BigDecimal can represent the same monetary value in different
         * textual forms:
         *
         * 30
         * 30.0
         * 30.00
         *
         * We normalize it before hashing so equivalent monetary values
         * produce the same fingerprint.
         */
        String canonicalAmount = amount.stripTrailingZeros().toPlainString();
        /*
         * WHY:
         *
         * A transfer is identified by THREE business inputs:
         *
         * source wallet
         * destination wallet
         * amount
         *
         * All three must be part of the fingerprint.
         *
         * Otherwise:
         *
         * A → B $30
         *
         * and
         *
         * A → C $30
         *
         * could incorrectly reuse the same idempotency key.
         */
        String canonicalRequest =
                "sourceWalletId=" + sourceWalletId
                        + "&destinationWalletId=" + destinationWalletId
                        + "&amount=" + canonicalAmount;
        /*
         * SHA-256 gives us a deterministic 256-bit fingerprint.
         *
         * Hex encoding produces 64 characters, matching our
         * request_fingerprint VARCHAR(64) column.
         */
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            /*
             * Convert the canonical request into UTF-8 bytes.
             *
             * Using an explicit character encoding guarantees that
             * the same canonical request produces the same bytes
             * before hashing.
             */
            byte[] hash =
                    digest.digest(
                            canonicalRequest.getBytes(StandardCharsets.UTF_8)
                    );
            return HexFormat.of().formatHex(hash);


        }catch (NoSuchAlgorithmException exception) {

            /*
             * SHA-256 is required by the Java platform, so this should
             * never happen during normal application execution.
             */
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available.",
                    exception
            );
        }

    }
}
