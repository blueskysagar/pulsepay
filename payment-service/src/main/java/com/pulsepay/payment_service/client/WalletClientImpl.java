package com.pulsepay.payment_service.client;

import com.pulsepay.payment_service.dto.request.CreateTransferRequest;
import com.pulsepay.payment_service.dto.response.CreateTransferResponse;
import com.pulsepay.payment_service.exception.WalletBusinessException;
import com.pulsepay.payment_service.exception.WalletCommunicationException;
import com.pulsepay.payment_service.security.internal.InternalServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletClientImpl implements WalletClient{
    private final RestClient walletRestClient;
    private final InternalServiceProperties internalServiceProperties;
    @Override
    public CreateTransferResponse executeTransfer(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String accessToken
    ){
        CreateTransferRequest request = new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                amount,
                currency
        );
        try {
            return walletRestClient
                    .post()
                    .uri("/api/v1/transfers")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request) //Convert Java object to json
                    .retrieve()//Send Http request and get the response
                    .onStatus(
                            status ->
                                    status.is4xxClientError(),
                            (request1, response) -> {
                                throw new WalletBusinessException("Wallet rejected the transfer");
                            }
                    )
                    .body(CreateTransferResponse.class); //convert json response back to CreateTransferResponse object
        } catch (WalletBusinessException exception){
            //This is confirmed business failure exception
            throw exception;

        } catch (Exception exception){


        throw new WalletCommunicationException(
                "Unable to obtain reliable response from Wallet Service",
                exception
        );
    }
    }
    @Override
    public CreateTransferResponse executeInternalTransfer(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ){
        CreateTransferRequest request = new CreateTransferRequest(
                sourceWalletId,
                destinationWalletId,
                amount,
                currency
        );
        try {
            return walletRestClient
                    .post()
                    .uri("/api/v1/internal/transfers")
                    .header(
                            "X-Internal-Service-Token",
                            internalServiceProperties.serviceToken()
                    )
                    .header(
                            "Idempotency-Key",
                            idempotencyKey
                    )
                    .body(request)
                    .retrieve()
                    //On status is restclient methods, condtions, errorhandler
                    .onStatus(
                            status -> status.is4xxClientError(),
                            (request1, response) -> {
                                throw new WalletBusinessException(
                                        "Wallet Rejected the transfer"
                                );
                            }
                    )
                    .body(CreateTransferResponse.class
                    );
        } catch (WalletBusinessException exception) {
            throw exception;


        }catch (Exception exception){
            throw new WalletCommunicationException(
                    "Unable to obtain reliable response from wallet service",
                    exception
            );
        }

    }
    }
