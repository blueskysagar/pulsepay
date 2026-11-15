package com.pulsepay.payment_service.service.impl;

import com.pulsepay.payment_service.client.WalletClient;
import com.pulsepay.payment_service.dto.request.CreatePaymentRequest;
import com.pulsepay.payment_service.dto.response.CreatePaymentResponse;
import com.pulsepay.payment_service.dto.response.CreateTransferResponse;
import com.pulsepay.payment_service.entity.Payment;
import com.pulsepay.payment_service.exception.IdempotencyKeyReuseException;
import com.pulsepay.payment_service.exception.PaymentNotFoundException;
import com.pulsepay.payment_service.exception.WalletBusinessException;
import com.pulsepay.payment_service.exception.WalletCommunicationException;
import com.pulsepay.payment_service.mapper.PaymentMapper;
import com.pulsepay.payment_service.repository.PaymentRepository;
import com.pulsepay.payment_service.security.token.CurrentAccessToken;
import com.pulsepay.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final WalletClient walletClient;
    private final CurrentAccessToken currentAccessToken;

    @Override
    //We do not want to wait for long time for wallet response so we dont put @Transaction here because payment transaction have to be open for long period of time, thats not good.
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        String requestFingerprint = buildFingerprint(request);
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if(existingPayment.isPresent()){
            Payment payment = existingPayment.get();
            if(!payment.getRequestFingerprint().equals(requestFingerprint)){
                throw new IdempotencyKeyReuseException(
                        "Idempotency key was already used with a different request"
                );

            }
            return paymentMapper.toResponse(payment);
        }

        Payment payment = paymentMapper.toEntity(request, idempotencyKey, requestFingerprint);
        payment.markProcessing();

        Payment savedPayment = paymentRepository.save(payment);
        //This is where money movement is requested
        try {
            CreateTransferResponse transferResponse =
                    walletClient.executeTransfer(
                            savedPayment.getSourceWalletId(),
                            savedPayment.getDestinationWalletId(),
                            savedPayment.getAmount(),
                            savedPayment.getCurrency(),
                            idempotencyKey,
                            currentAccessToken.get()

                    );
            //Wallet tell us whether the transfer completed
            if ("COMPLETED".equals(transferResponse.status())) {
                savedPayment.markCompleted();
            } else {
                savedPayment.markFailed();
            }
        } catch (WalletBusinessException exception) {

            savedPayment.markFailed();

            log.warn(
                    "Wallet rejected payment {}. idempotencyKey={}",
                    savedPayment.getId(),
                    idempotencyKey,
                    exception
            );

        }
        catch (WalletCommunicationException exception){
            //We do not know whether wallet proceed the transfer.Keep payment in processsing.
            log.warn(
                    "Wallet communication failed for payment {}. " +
                            "Payment remains processing for recovery. " +
                            "idempotencyKey={}",
                    savedPayment.getId(),
                    idempotencyKey,
                    exception

            );
        }
        Payment updatedPayment = paymentRepository.save(savedPayment);
        return paymentMapper.toResponse(updatedPayment);


    }
    @Override
    public CreatePaymentResponse getPayment(UUID paymentId){
        Payment payment = paymentRepository.findById(paymentId).
                orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found: " + paymentId
                        )
                );
        return paymentMapper.toResponse(payment);
    }
    private String buildFingerprint(CreatePaymentRequest request){
        return request.sourceWalletId()
                + "|"
                + request.destinationWalletId()
                + "|"
                + request.amount()
                + "|"
                + request.currency();
    }
}
