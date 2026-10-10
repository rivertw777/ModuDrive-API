package com.moduDrive.file.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataBlockRepository extends JpaRepository<BlockJpaEntity, BlockJpaEntity.Key> {

    // Hash order, so two commits locking overlapping blocklists always queue in the same order
    // instead of deadlocking.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BlockJpaEntity b where b.id.ownerId = :ownerId and b.id.hash in :hashes order by b.id.hash")
    List<BlockJpaEntity> lockByOwnerIdAndHashes(@Param("ownerId") UUID ownerId,
                                                @Param("hashes") Collection<String> hashes);

    @Query("select b.id.hash from BlockJpaEntity b where b.id.ownerId = :ownerId and b.id.hash in :hashes")
    List<String> findHashesByOwnerIdAndHashes(@Param("ownerId") UUID ownerId,
                                              @Param("hashes") Collection<String> hashes);

    // Upsert, not insert: two commits of the same new block can both find it missing — the second
    // adds its references to the row the first created instead of failing on the primary key.
    @Modifying
    @Query(value = "insert into block (owner_id, hash, size, ref_count) values (:ownerId, :hash, :size, :count) "
            + "on conflict (owner_id, hash) do update "
            + "set ref_count = block.ref_count + excluded.ref_count, unreferenced_at = null",
            nativeQuery = true)
    void reference(@Param("ownerId") UUID ownerId, @Param("hash") String hash,
                   @Param("size") int size, @Param("count") int count);

    // The case reads the row's old ref_count, so it stamps unreferenced_at exactly when this
    // release is the one that takes the count to 0.
    @Modifying
    @Query(value = "update block set ref_count = ref_count - :count, "
            + "unreferenced_at = case when ref_count - :count <= 0 then :now else unreferenced_at end "
            + "where owner_id = :ownerId and hash = :hash",
            nativeQuery = true)
    int release(@Param("ownerId") UUID ownerId, @Param("hash") String hash,
                @Param("count") int count, @Param("now") LocalDateTime now);

    // SKIP LOCKED: a row a commit is holding is about to be referenced again, so it is left for a
    // later sweep instead of waited on.
    @Query(value = "select * from block where ref_count = 0 and unreferenced_at < :cutoff "
            + "order by unreferenced_at limit :limit for update skip locked",
            nativeQuery = true)
    List<BlockJpaEntity> lockUnreferencedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
