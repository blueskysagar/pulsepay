package com.pulsepay.payment_service.controller;

import com.pulsepay.payment_service.dto.request.CreatePaymentRequest;
import com.pulsepay.payment_service.dto.response.CreatePaymentResponse;
import com.pulsepay.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    @PostMapping

    public ResponseEntity<CreatePaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencykey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        CreatePaymentResponse response= paymentService.createPayment(request,idempotencykey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @GetMapping("/{paymentId}")
    public ResponseEntity<CreatePaymentResponse> getPayment(@PathVariable UUID paymentId){
        return ResponseEntity.ok(
                paymentService.getPayment(paymentId)
        );
    }
}
