package com.pulsepay.wallet_service.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass //"This class is NOT a database table.
// Its fields are inherited by child @Entity classes,
// so createdAt, updatedAt, etc. become columns in each child table."
@EntityListeners(AuditingEntityListener.class) //"Tell JPA to watch this entity's lifecycle
// so Spring Data JPA can automatically fill audit fields
// such as createdAt and updatedAt."
@Getter
@Setter

public abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


}
