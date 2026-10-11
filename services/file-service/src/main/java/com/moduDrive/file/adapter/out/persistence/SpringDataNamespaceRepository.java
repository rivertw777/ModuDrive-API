package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataNamespaceRepository extends JpaRepository<NamespaceJpaEntity, UUID> {

    Optional<NamespaceJpaEntity> findByUserId(UUID userId);

    // Check and add in one statement: the row lock makes a second commit into the same drive wait,
    // and Postgres re-checks the WHERE against the first one's result before adding (READ COMMITTED
    // re-evaluates an updated row) — no read-then-write gap. Shrinking always goes through.
    // flushAutomatically: the commit's file and version writes go out first, so this row is the
    // last lock the transaction takes.
    @Modifying(flushAutomatically = true)
    @Query("update NamespaceJpaEntity n set n.usedBytes = n.usedBytes + :delta " +
            "where n.id = :id and (:delta <= 0 or n.usedBytes + :delta <= n.quotaBytes)")
    int addUsedBytes(@Param("id") UUID id, @Param("delta") long delta);

    // A purge frees what the file counted: its current size, unless it's a folder or has none (a
    // PENDING row an old upload flow left). Read from the file row itself, which a purge doesn't clear.
    @Modifying(flushAutomatically = true)
    @Query(value = "update namespace n set used_bytes = n.used_bytes - f.file_size from file f " +
            "where f.id = :fileId and n.id = f.namespace_id and f.is_directory = false and f.file_size is not null",
            nativeQuery = true)
    int releaseUsedBytesOf(@Param("fileId") UUID fileId);
}
