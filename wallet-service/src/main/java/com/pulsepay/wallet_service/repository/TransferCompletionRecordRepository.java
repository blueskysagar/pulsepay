package com.pulsepay.wallet_service.repository;

import com.pulsepay.wallet_service.entity.TransferCompletionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferCompletionRecordRepository extends JpaRepository<TransferCompletionRecord, UUID> {

}
