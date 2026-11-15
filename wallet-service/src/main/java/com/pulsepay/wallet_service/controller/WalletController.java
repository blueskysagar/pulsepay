package com.pulsepay.wallet_service.controller;

import com.pulsepay.wallet_service.dto.request.CreateWalletRequest;
import com.pulsepay.wallet_service.dto.request.CreditWalletRequest;
import com.pulsepay.wallet_service.dto.request.UpdateWalletStatusRequest;
import com.pulsepay.wallet_service.dto.response.CreateWalletResponse;
import com.pulsepay.wallet_service.dto.response.WalletPageResponse;
import com.pulsepay.wallet_service.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;
    /*
     * Creates a new wallet for the authenticated account.
     *
     * The account ID is obtained from the authenticated security context
     * rather than being trusted from the request body.
     */
    @PostMapping
    public ResponseEntity<CreateWalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request, @AuthenticationPrincipal String accountId) {
        /*
         * accountId is currently a String because our
         * JwtAuthenticationFilter creates the Authentication
         * object using the JWT subject as a String.
         *
         * We convert it to UUID because UUID is our domain
         * representation for IdentityAccount IDs.
         */
        UUID authenticatedAccountId = UUID.fromString(accountId);
        CreateWalletResponse response = walletService.createWallet(authenticatedAccountId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);

    }
    @GetMapping("/{walletId}")
    public ResponseEntity<CreateWalletResponse> getWallet(@PathVariable UUID walletId, @AuthenticationPrincipal String accountId){
        /*
         * The authenticated account ID comes from Spring Security,
         * not from the HTTP request.
         */
        UUID authenticatedAccountId =
                UUID.fromString(accountId);
        CreateWalletResponse response = walletService.getWallet(walletId,authenticatedAccountId);
        return ResponseEntity
                .ok(response);
    }
    @GetMapping
    public ResponseEntity<WalletPageResponse> getWallets( @AuthenticationPrincipal String accountId,
                                                         @PageableDefault(
                                                                         size=20,
                                                                         sort = "createdAt",
                                                                         direction = Sort.Direction.DESC
                                                                 )Pageable pageable){
        UUID authenticatedAccountId =
                UUID.fromString(accountId);

        WalletPageResponse response = walletService.getWallets(authenticatedAccountId, pageable);
        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{walletId}/status")
    public ResponseEntity<CreateWalletResponse> updateWalletStatus(@PathVariable UUID walletId,@AuthenticationPrincipal String accountId, @Valid @RequestBody UpdateWalletStatusRequest request){
        UUID authenticatedAccountId =
                UUID.fromString(accountId);
        CreateWalletResponse response = walletService.updateWalletStatus(
                walletId,
                authenticatedAccountId,
                request.getStatus()
        );
        return ResponseEntity.ok(response);
    }
    /*
     * The amount represents money being added to the wallet.
     *
     * BigDecimal is mandatory for monetary values because double/float
     * use binary floating-point representation and can introduce
     * precision errors.
     */
    @PostMapping("/{walletId}/credit")
    public ResponseEntity<CreateWalletResponse> creditWallet(@PathVariable UUID walletId,  @AuthenticationPrincipal String accountId, @RequestHeader("Idempotency-key") String idempotencyKey, @Valid @RequestBody CreditWalletRequest request){
        /*
         * Convert the authenticated JWT subject into
         * our domain UUID representation.
         */
        UUID authenticatedAccountId =
                UUID.fromString(accountId);
        /*
         * Delegate the actual financial operation to the service.
         *
         * The authenticated account ID is supplied by the security layer,
         * not by the client request body.
         */
        CreateWalletResponse response = walletService.creditWallet(walletId, authenticatedAccountId,request.getAmount(),idempotencyKey);
//        *
//     * The wallet already exists, so this operation updates its state.
//     * Therefore we return HTTP 200 OK with the updated representation.
//                */
        return ResponseEntity.ok(response);
    }

}
