package com.pulsepay.identity_service.service.impl;

import com.pulsepay.identity_service.dto.request.RegisterAccountRequest;
import com.pulsepay.identity_service.dto.response.RegisterAccountResponse;
import com.pulsepay.identity_service.entity.IdentityAccount;
import com.pulsepay.identity_service.enums.AccountStatus;
import com.pulsepay.identity_service.exception.DuplicateAccountException;
import com.pulsepay.identity_service.mapper.IdentityAccountMapper;
import com.pulsepay.identity_service.normalization.EmailNormalizer;
import com.pulsepay.identity_service.normalization.PhoneNumberNormalizer;
import com.pulsepay.identity_service.repository.IdentityAccountRepository;
import com.pulsepay.identity_service.service.IdentityAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // Create beans of this class and the class contains business logic
@RequiredArgsConstructor
public class IdentityAccountServiceImpl implements IdentityAccountService {
    private final IdentityAccountRepository identityAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final EmailNormalizer emailNormalizer;
    private final IdentityAccountMapper identityAccountMapper;

    @Override
    @Transactional
    public RegisterAccountResponse register(RegisterAccountRequest request){
        String normalizedEmail = emailNormalizer.normalize(request.getEmail());
        String normalizedPhone = phoneNumberNormalizer.normalize(request.getPhone());
         if(identityAccountRepository.existsByEmail(normalizedEmail))
         {
             throw new DuplicateAccountException("An account already exists with this email.");
         }
         if(identityAccountRepository.existsByPhone(normalizedPhone))
         {
             throw new DuplicateAccountException("An account already exists with this phone number.");
         }
         //We hash the password so that raw password never reach to an entity
        String passwordHash = passwordEncoder.encode(request.getPassword());
         //Map the requested fields it is safe to map
         IdentityAccount identityAccount = identityAccountMapper.toEntity(request);
         //Now things like email, phone password which were hashed, normalized have to be set this way
        identityAccount.setEmail(normalizedEmail);
        identityAccount.setPhone(normalizedPhone);
        identityAccount.setPasswordHash(passwordHash);
        //set initial business state
        identityAccount.setStatus(AccountStatus.ACTIVE);

        //This means it hands entity to JPA persistence layer, anything in persistence context hibernate can now trace.
        // Normally what happens is when we do save everthing goes into persitence contex which is part of hibernate, hibernate waits if someone does any changes track that when its finalized it flush and sync the latest update to postgresql by inserting
        // Save and flush here it goes to persistence layer and we ask hibernate to straight away do flush and insert into postgresql, we dont want to wait for being set values.
        IdentityAccount savedAccount = identityAccountRepository.saveAndFlush(identityAccount);
        return identityAccountMapper.toResponse(savedAccount);




    }


}
