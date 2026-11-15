package com.pulsepay.identity_service.entity;

import com.pulsepay.common.base.BaseEntity;
import com.pulsepay.identity_service.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name="identity_account",
       uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_identity_account_email",
                columnNames = "email"
        ),
               @UniqueConstraint(
                       name = "uk_identity_account_phone",
                       columnNames = "phone"
               )
       }
)
public class IdentityAccount extends BaseEntity {
    @Id
    @UuidGenerator //we want spring jpa to generate it not us.
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(nullable = false, length=100)
    private String name;
    @Column(nullable = false, length=255)
    private String email;
    @Column(nullable = false, length=30)
    private String phone;
    @Column(
            name="password_hash",
            nullable = false,
            length=255
    )
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length=30)
    private AccountStatus status;

}
