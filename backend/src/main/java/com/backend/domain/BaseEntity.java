package com.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;

/** created_at, updated_at, deleted_at + 소프트 삭제 (deleted_at 사용 엔티티용) */
@MappedSuperclass
@Getter
public abstract class BaseEntity extends AuditEntity {

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
