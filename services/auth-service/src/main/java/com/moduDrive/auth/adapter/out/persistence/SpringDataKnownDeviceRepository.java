package com.moduDrive.auth.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

interface SpringDataKnownDeviceRepository extends JpaRepository<KnownDeviceJpaEntity, KnownDeviceJpaEntity.Key> {

    /** Whether this email verified this device within the TTL — asked before the password is checked,
     * so it goes by the email rather than the member. */
    @Query("""
            select count(d) > 0 from KnownDeviceJpaEntity d
            where d.email = :email and d.id.deviceHash = :deviceHash and d.lastUsedAt > :cutoff""")
    boolean existsFresh(String email, String deviceHash, Instant cutoff);

    /** Moves last_used_at to {@code now} only while the device is still fresh — "is it known" and
     * "extend it" in one statement. Returns the rows touched: 1 if known, 0 if not or expired.
     * Also records the email, filling rows from before it was kept. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update KnownDeviceJpaEntity d set d.lastUsedAt = :now, d.email = :email
            where d.id.memberId = :memberId and d.id.deviceHash = :deviceHash and d.lastUsedAt > :cutoff""")
    int touchIfFresh(UUID memberId, String deviceHash, String email, Instant now, Instant cutoff);

    /** Insert, or restart the 90 days of a device verified before (possibly expired) — one statement,
     * so two tabs verifying the same device at once can't trip the primary key. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into known_device (member_id, device_hash, email, created_at, last_used_at)
            values (:memberId, :deviceHash, :email, :now, :now)
            on conflict (member_id, device_hash) do update
            set last_used_at = excluded.last_used_at, email = excluded.email""",
            nativeQuery = true)
    void upsert(UUID memberId, String deviceHash, String email, Instant now);
}
