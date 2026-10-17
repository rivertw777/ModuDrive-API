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

    // A purge frees the files' current sizes (folders count nothing),
    // read from the rows themselves. One UPDATE per drive, and none when nothing had a size, so
    // purging an empty folder doesn't lock the drive's row.
    @Modifying(flushAutomatically = true)
    @Query(value = "update namespace n set used_bytes = n.used_bytes - s.total from "
            + "(select namespace_id, sum(file_size) as total from file where id = any(:fileIds) "
            + "and is_directory = false and file_size is not null group by namespace_id) s "
            + "where n.id = s.namespace_id",
            nativeQuery = true)
    int releaseUsedBytesOf(@Param("fileIds") UUID[] fileIds);
}
