package com.pulsepay.wallet_service.repository;

import com.pulsepay.wallet_service.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {


}
