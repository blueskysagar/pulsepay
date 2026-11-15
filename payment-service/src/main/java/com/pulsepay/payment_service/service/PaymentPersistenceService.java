package com.pulsepay.payment_service.service;

import com.pulsepay.payment_service.entity.Payment;
import com.pulsepay.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
//WE dont want payment transactions open for long,imagine 1000 payment transactions waiting for wallet response, so we commit here and in wallet one we dont put transactions as soon as it gets the response we commit.

@Service
@RequiredArgsConstructor
public class PaymentPersistenceService {
    private final PaymentRepository paymentRepository;
    @Transactional
    public Payment save(Payment payment){
        return paymentRepository.save(payment);
    }
}
