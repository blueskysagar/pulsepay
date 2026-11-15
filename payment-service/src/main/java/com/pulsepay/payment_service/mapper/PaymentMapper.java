package com.pulsepay.payment_service.mapper;

import com.pulsepay.payment_service.dto.request.CreatePaymentRequest;
import com.pulsepay.payment_service.dto.response.CreatePaymentResponse;
import com.pulsepay.payment_service.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring") //Hey Mapstruct generate the implementation of payment mapper and make it as bean.
public interface PaymentMapper {
    Payment toEntity(CreatePaymentRequest request, String idempotencyKey, String requestFingerprint);
    CreatePaymentResponse toResponse(Payment payment);
}
