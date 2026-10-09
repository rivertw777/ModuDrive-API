package com.moduDrive.file.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/** A committed block of one owner — see V8__content_addressed_blocks.sql. Written only through
 * the native statements in {@link SpringDataBlockRepository}; the entity exists so those rows can
 * be read and locked. */
@Getter
@NoArgsConstructor
@Table(name = "block")
@Entity
class BlockJpaEntity {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private int size;

    @Column(nullable = false)
    private int refCount;

    private LocalDateTime unreferencedAt;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    static class Key implements Serializable {

        @Column(nullable = false)
        private UUID ownerId;

        @Column(nullable = false, length = 64)
        private String hash;
    }
}
