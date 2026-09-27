package com.moduDrive.auth.adapter.out.persistence;

import com.moduDrive.auth.adapter.out.security.SecureTokens;
import com.moduDrive.auth.domain.vo.DeviceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@Import(KnownDevicePersistenceAdapter.class)
class KnownDevicePersistenceAdapterTest {

    private static final String MEMBER_ID = UUID.randomUUID().toString();
    private static final String OTHER_MEMBER_ID = UUID.randomUUID().toString();

    @Autowired
    private KnownDevicePersistenceAdapter adapter;
    @Autowired
    private SpringDataKnownDeviceRepository repository;

    private KnownDeviceJpaEntity onlyRow() {
        assertThat(repository.findAll()).hasSize(1);
        return repository.findAll().getFirst();
    }

    /** A row for {@code deviceId} last used {@code ago}. */
    private void givenDeviceUsed(String memberId, DeviceId deviceId, Duration ago) {
        Instant then = Instant.now().minus(ago);
        repository.saveAndFlush(new KnownDeviceJpaEntity(
                UUID.fromString(memberId), SecureTokens.sha256Hex(deviceId.value()), then, then));
    }

    @Nested
    @DisplayName("기기를 기억할 때")
    class WhenRemembering {

        @Test
        @DisplayName("기기 ID가 없으면 새 무작위 ID를 만들고, 테이블에는 해시만 둔다")
        void mintsAnIdAndStoresOnlyItsHash() {
            DeviceId deviceId = adapter.remember(MEMBER_ID, null);

            assertThat(deviceId.value()).hasSize(43);
            KnownDeviceJpaEntity row = onlyRow();
            assertThat(row.getId().memberId()).hasToString(MEMBER_ID);
            assertThat(row.getId().deviceHash()).matches("[0-9a-f]{64}").isNotEqualTo(deviceId.value());
            assertThat(row.getLastUsedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        }

        @Test
        void keepsTheGivenId() {
            assertThat(adapter.remember(MEMBER_ID, new DeviceId("device-id"))).isEqualTo(new DeviceId("device-id"));
        }

        @Test
        @DisplayName("이미 있는 기기면 행을 늘리지 않고 90일을 다시 건다")
        void restartsAnExistingDevice() {
            DeviceId deviceId = new DeviceId("device-id");
            givenDeviceUsed(MEMBER_ID, deviceId, Duration.ofDays(100));

            adapter.remember(MEMBER_ID, deviceId);

            assertThat(onlyRow().getLastUsedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("그 회원의 만료된 다른 기기는 지운다")
        void sweepsTheMembersExpiredDevices() {
            givenDeviceUsed(MEMBER_ID, new DeviceId("old-device"), Duration.ofDays(91));

            adapter.remember(MEMBER_ID, new DeviceId("new-device"));

            assertThat(onlyRow().getId().deviceHash()).isEqualTo(SecureTokens.sha256Hex("new-device"));
        }
    }

    @Nested
    @DisplayName("아는 기기인지 확인할 때")
    class WhenChecking {

        @Test
        @DisplayName("90일 안에 쓴 기기면 true이고 90일을 다시 건다")
        void refreshesAKnownDevice() {
            DeviceId deviceId = new DeviceId("device-id");
            givenDeviceUsed(MEMBER_ID, deviceId, Duration.ofDays(89));

            assertThat(adapter.refreshIfKnown(MEMBER_ID, deviceId)).isTrue();

            assertThat(onlyRow().getLastUsedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("90일 넘게 안 쓴 기기는 모르는 기기다")
        void expiredDeviceIsFalse() {
            DeviceId deviceId = new DeviceId("device-id");
            givenDeviceUsed(MEMBER_ID, deviceId, Duration.ofDays(91));

            assertThat(adapter.refreshIfKnown(MEMBER_ID, deviceId)).isFalse();
        }

        @Test
        @DisplayName("다른 회원이 이 기기를 인증했어도 이 회원에게는 모르는 기기다")
        void isPerMember() {
            DeviceId deviceId = adapter.remember(OTHER_MEMBER_ID, null);

            assertThat(adapter.refreshIfKnown(MEMBER_ID, deviceId)).isFalse();
        }

        @Test
        void unknownDeviceIsFalse() {
            assertThat(adapter.refreshIfKnown(MEMBER_ID, new DeviceId("never-seen"))).isFalse();
        }
    }

    @Nested
    @DisplayName("회원의 기기를 모두 잊을 때")
    class WhenForgettingAll {

        @Test
        @DisplayName("그 회원의 기기만 모두 모르는 기기가 된다")
        void forgetsEveryDeviceOfThatMemberOnly() {
            DeviceId first = new DeviceId("first-device");
            DeviceId second = new DeviceId("second-device");
            givenDeviceUsed(MEMBER_ID, first, Duration.ofDays(1));
            givenDeviceUsed(MEMBER_ID, second, Duration.ofDays(1));
            givenDeviceUsed(OTHER_MEMBER_ID, first, Duration.ofDays(1));

            adapter.forgetAll(MEMBER_ID);

            assertThat(adapter.refreshIfKnown(MEMBER_ID, first)).isFalse();
            assertThat(adapter.refreshIfKnown(MEMBER_ID, second)).isFalse();
            assertThat(adapter.refreshIfKnown(OTHER_MEMBER_ID, first)).isTrue();
        }
    }
}
