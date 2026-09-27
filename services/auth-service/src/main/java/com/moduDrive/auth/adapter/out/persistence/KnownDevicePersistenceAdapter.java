package com.moduDrive.auth.adapter.out.persistence;

import com.moduDrive.auth.adapter.out.security.SecureTokens;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.common.core.annotation.PersistenceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One {@code known_device} row per device a member verified by email (spec 004 2-2), keyed by the
 * SHA-256 of the device id so a leaked table can't be turned back into a working cookie. A device is
 * known while it was used within the last 90 days; each login from it restarts the 90 days.
 */
@RequiredArgsConstructor
@PersistenceAdapter
class KnownDevicePersistenceAdapter implements KnownDevicePort {

    static final Duration TTL = Duration.ofDays(90);

    private final SpringDataKnownDeviceRepository repository;

    @Transactional
    @Override
    public boolean refreshIfKnown(String memberId, DeviceId deviceId) {
        Instant now = Instant.now();
        return repository.touchIfFresh(UUID.fromString(memberId), SecureTokens.sha256Hex(deviceId.value()),
                now, now.minus(TTL)) == 1;
    }

    @Transactional
    @Override
    public DeviceId remember(String memberId, DeviceId deviceId) {
        DeviceId device = deviceId != null ? deviceId : new DeviceId(SecureTokens.newToken());
        UUID member = UUID.fromString(memberId);
        Instant now = Instant.now();
        repository.upsert(member, SecureTokens.sha256Hex(device.value()), now);
        // ponytail: expired rows are only swept here, per member, when they verify another device —
        // a member who never comes back keeps theirs. Harmless (refreshIfKnown ignores them); add a
        // scheduled global purge if the table's size ever matters.
        repository.deleteExpired(member, now.minus(TTL));
        return device;
    }

    @Transactional
    @Override
    public void forgetAll(String memberId) {
        repository.deleteByMember(UUID.fromString(memberId));
    }
}
