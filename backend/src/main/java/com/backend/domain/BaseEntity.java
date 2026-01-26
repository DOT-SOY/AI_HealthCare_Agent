package com.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 소프트 삭제 */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /** 복구 */
    public void restore() {
        this.deletedAt = null;
    }

    /** 삭제 여부 */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
