package com.moduDrive.auth.adapter.out.persistence;

import com.moduDrive.auth.adapter.out.security.SecureTokens;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.common.core.annotation.PersistenceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One {@code known_device} row per device a member verified by email (spec 004 2-1), keyed by the
 * SHA-256 of the device id so a leaked table can't be turned back into a working cookie. A device is
 * known while it was used within the last 90 days; each login from it restarts the 90 days.
 */
@RequiredArgsConstructor
@PersistenceAdapter
class KnownDevicePersistenceAdapter implements KnownDevicePort {

    static final Duration TTL = Duration.ofDays(90);

    private final SpringDataKnownDeviceRepository repository;

    @Transactional(readOnly = true)
    @Override
    public boolean isKnown(MemberEmail memberEmail, DeviceId deviceId) {
        return repository.existsFresh(memberEmail.normalized(), SecureTokens.sha256Hex(deviceId.value()),
                Instant.now().minus(TTL));
    }

    @Transactional
    @Override
    public boolean refreshIfKnown(String memberId, MemberEmail memberEmail, DeviceId deviceId) {
        Instant now = Instant.now();
        return repository.touchIfFresh(UUID.fromString(memberId), SecureTokens.sha256Hex(deviceId.value()),
                memberEmail.normalized(), now, now.minus(TTL)) == 1;
    }

    @Transactional
    @Override
    public DeviceId remember(String memberId, MemberEmail memberEmail, DeviceId deviceId) {
        DeviceId device = deviceId != null ? deviceId : new DeviceId(SecureTokens.newToken());
        repository.upsert(UUID.fromString(memberId), SecureTokens.sha256Hex(device.value()),
                memberEmail.normalized(), Instant.now());
        return device;
    }
}
