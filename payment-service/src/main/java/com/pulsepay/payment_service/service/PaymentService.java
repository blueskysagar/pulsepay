package com.pulsepay.payment_service.service;

import com.pulsepay.payment_service.dto.request.CreatePaymentRequest;
import com.pulsepay.payment_service.dto.response.CreatePaymentResponse;

import java.util.UUID;

public interface PaymentService {
    CreatePaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey);
    CreatePaymentResponse getPayment(UUID paymentId);

}
