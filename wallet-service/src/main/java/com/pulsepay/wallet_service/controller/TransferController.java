package com.pulsepay.wallet_service.controller;

import com.pulsepay.wallet_service.dto.request.CreateTransferRequest;
import com.pulsepay.wallet_service.dto.response.CreateTransferResponse;
import com.pulsepay.wallet_service.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {
    private final TransferService transferService;
    /**
     * Creates a wallet-to-wallet transfer.
     *
     * WHY:
     * The controller's responsibility is HTTP handling only.
     *
     * It receives the request, obtains the authenticated account ID,
     * delegates the actual business operation to TransferService,
     * and returns the service result.
     */
    @PostMapping
    public ResponseEntity<CreateTransferResponse> createTransfer(@Valid @RequestBody CreateTransferRequest request, @AuthenticationPrincipal String accountId, @RequestHeader("Idempotency-Key") String idempotencyKey){
        /* * The JWT subject is currently stored as a String * inside the Authentication principal. * * Our domain/application layer uses UUID for account IDs, * so we convert it here at the controller boundary. */
        UUID authenticatedAccountId = UUID.fromString(accountId);

        CreateTransferResponse response = transferService.createTransfer(request,authenticatedAccountId,idempotencyKey);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);

    }





}
