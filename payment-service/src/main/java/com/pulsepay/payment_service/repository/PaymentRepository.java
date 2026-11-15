package com.pulsepay.payment_service.repository;

import com.pulsepay.payment_service.entity.Payment;
import com.pulsepay.payment_service.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByStatusAndUpdatedAtBefore(
            PaymentStatus status,
            Instant cutoff
    );
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
