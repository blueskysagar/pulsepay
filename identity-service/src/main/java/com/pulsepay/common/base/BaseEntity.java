package com.pulsepay.common.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass // dont create table of this class its field gets copied into whichever table it extends.
@EntityListeners(AuditingEntityListener.class) // Watches this entity and autofill time stamp fields whenever row is saved or updated.
public abstract class BaseEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    protected Instant createdAt;
    @LastModifiedDate
    @Column(nullable = false)
    protected Instant updatedAt;

}
