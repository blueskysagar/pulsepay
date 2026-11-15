package com.pulsepay.wallet_service.controller;

import com.pulsepay.wallet_service.dto.request.CreateTransferRequest;
import com.pulsepay.wallet_service.dto.response.CreateTransferResponse;
import com.pulsepay.wallet_service.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/transfers")
@RequiredArgsConstructor
public class InternalTransferController {
    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<CreateTransferResponse> executeTransfer( @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateTransferRequest request
    ) {

        CreateTransferResponse response =
                transferService.createInternalTransfer(request,idempotencyKey);

        return ResponseEntity.ok(response);
    }

}
