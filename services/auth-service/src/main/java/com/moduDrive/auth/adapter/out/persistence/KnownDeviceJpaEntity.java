package com.moduDrive.auth.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A device a member verified by email, by the SHA-256 of its id (spec 004 2-2). */
@Getter
@Entity
@Table(name = "known_device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class KnownDeviceJpaEntity {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    KnownDeviceJpaEntity(UUID memberId, String deviceHash, Instant createdAt, Instant lastUsedAt) {
        this.id = new Key(memberId, deviceHash);
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    @Embeddable
    record Key(@Column(name = "member_id") UUID memberId,
               @Column(name = "device_hash", length = 64) String deviceHash) implements Serializable {
    }
}
