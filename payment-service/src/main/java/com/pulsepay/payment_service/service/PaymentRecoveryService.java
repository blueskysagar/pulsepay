package com.pulsepay.payment_service.service;

import com.pulsepay.payment_service.client.WalletClient;
import com.pulsepay.payment_service.dto.response.CreateTransferResponse;
import com.pulsepay.payment_service.entity.Payment;
import com.pulsepay.payment_service.enums.PaymentStatus;
import com.pulsepay.payment_service.exception.WalletCommunicationException;
import com.pulsepay.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryService {
    private final PaymentRepository paymentRepository;
    private final PaymentPersistenceService paymentPersistenceService;
    private final WalletClient walletClient;
    @Value("${pulsepay.payment.recovery.stale-after-seconds:60}")
    private long staleAfterSeconds;
    @Scheduled(
            fixedDelayString =
                    "${pulsepay.payment.recovery.interval-ms:30000}"
    )
    public void recoverStalePayments(){
        Instant cutoff = Instant.now()
                .minusSeconds(staleAfterSeconds
                ); //Only pick up the payments which is created 60 sec before.
        List<Payment> payments = paymentRepository.findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING,
                cutoff);
        for(Payment payment : payments){
            recover(payment);
        }
    }
    private void recover(Payment payment){
        String idempotencyKey = payment.getIdempotencyKey();
        try{
        CreateTransferResponse transferResponse =
                walletClient.executeInternalTransfer(
                        payment.getSourceWalletId(),
                        payment.getDestinationWalletId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        idempotencyKey
                );
        if("COMPLETED".equals(transferResponse.status())){
            payment.markCompleted();
        }
        else if("FAILED".equals(transferResponse.status())){
            payment.markFailed();
        }
        paymentPersistenceService.save(payment);
    } catch (WalletCommunicationException exception){
            //Keep payment processing the next scheduler recovery attempt will try again
            log.warn(
                    "Recovery could not obtain a reliable walletresponse for payment {}." + "Payment remains PROCESSING.",
                    payment.getId(),
                    exception
            );
        }
    }


}
