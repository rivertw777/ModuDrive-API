package com.moduDrive.auth.adapter.out.persistence;

import com.moduDrive.auth.adapter.out.security.SecureTokens;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.MemberEmail;
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
    private static final MemberEmail EMAIL = new MemberEmail("River@ModuDrive.com");
    private static final MemberEmail OTHER_EMAIL = new MemberEmail("other@modudrive.com");

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
        givenDeviceUsed(memberId, "river@modudrive.com", deviceId, ago);
    }

    private void givenDeviceUsed(String memberId, String email, DeviceId deviceId, Duration ago) {
        Instant then = Instant.now().minus(ago);
        repository.saveAndFlush(new KnownDeviceJpaEntity(
                UUID.fromString(memberId), SecureTokens.sha256Hex(deviceId.value()), email, then, then));
    }

    @Nested
    @DisplayName("기기를 기억할 때")
    class WhenRemembering {

        @Test
        @DisplayName("기기 ID가 없으면 새 무작위 ID를 만들고, 테이블에는 해시만 둔다")
        void mintsAnIdAndStoresOnlyItsHash() {
            DeviceId deviceId = adapter.remember(MEMBER_ID, EMAIL, null);

            assertThat(deviceId.value()).hasSize(43);
            KnownDeviceJpaEntity row = onlyRow();
            assertThat(row.getId().memberId()).hasToString(MEMBER_ID);
            assertThat(row.getId().deviceHash()).matches("[0-9a-f]{64}").isNotEqualTo(deviceId.value());
            assertThat(row.getLastUsedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
            assertThat(row.getEmail()).isEqualTo("river@modudrive.com");
        }

        @Test
        void keepsTheGivenId() {
            assertThat(adapter.remember(MEMBER_ID, EMAIL, new DeviceId("device-id"))).isEqualTo(new DeviceId("device-id"));
        }

        @Test
        @DisplayName("이미 있는 기기면 행을 늘리지 않고 90일을 다시 건다")
        void restartsAnExistingDevice() {
            DeviceId deviceId = new DeviceId("device-id");
            givenDeviceUsed(MEMBER_ID, deviceId, Duration.ofDays(100));

            adapter.remember(MEMBER_ID, EMAIL, deviceId);

            assertThat(onlyRow().getLastUsedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
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

            assertThat(adapter.refreshIfKnown(MEMBER_ID, EMAIL, deviceId)).isTrue();

            assertThat(onlyRow().getLastUsedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("90일 넘게 안 쓴 기기는 모르는 기기다")
        void expiredDeviceIsFalse() {
            DeviceId deviceId = new DeviceId("device-id");
            givenDeviceUsed(MEMBER_ID, deviceId, Duration.ofDays(91));

            assertThat(adapter.refreshIfKnown(MEMBER_ID, EMAIL, deviceId)).isFalse();
        }

        @Test
        @DisplayName("다른 회원이 이 기기를 인증했어도 이 회원에게는 모르는 기기다")
        void isPerMember() {
            DeviceId deviceId = adapter.remember(OTHER_MEMBER_ID, OTHER_EMAIL, null);

            assertThat(adapter.refreshIfKnown(MEMBER_ID, EMAIL, deviceId)).isFalse();
        }

        @Test
        @DisplayName("이메일이 없던 예전 행이면 이메일을 채운다")
        void fillsTheEmailOfAnOldRow() {
            DeviceId deviceId = new DeviceId("device-id");
            givenDeviceUsed(MEMBER_ID, null, deviceId, Duration.ofDays(1));

            assertThat(adapter.refreshIfKnown(MEMBER_ID, EMAIL, deviceId)).isTrue();

            assertThat(onlyRow().getEmail()).isEqualTo("river@modudrive.com");
        }

        @Test
        void unknownDeviceIsFalse() {
            assertThat(adapter.refreshIfKnown(MEMBER_ID, EMAIL, new DeviceId("never-seen"))).isFalse();
        }
    }

    @Nested
    @DisplayName("비밀번호 확인 전에 이메일로 아는 기기인지 볼 때")
    class WhenCheckingByEmail {

        @Test
        @DisplayName("그 이메일로 90일 안에 인증한 기기면 true (대소문자 무관)")
        void knownDeviceOfTheEmail() {
            DeviceId deviceId = new DeviceId("device-id");
            givenDeviceUsed(MEMBER_ID, deviceId, Duration.ofDays(89));

            assertThat(adapter.isKnown(EMAIL, deviceId)).isTrue();
        }

        @Test
        @DisplayName("90일이 지났거나, 다른 이메일의 기기거나, 이메일이 없던 예전 행이면 false")
        void otherwiseFalse() {
            DeviceId expired = new DeviceId("expired");
            DeviceId others = new DeviceId("others");
            DeviceId old = new DeviceId("old");
            givenDeviceUsed(MEMBER_ID, expired, Duration.ofDays(91));
            givenDeviceUsed(OTHER_MEMBER_ID, "other@modudrive.com", others, Duration.ofDays(1));
            givenDeviceUsed(MEMBER_ID, null, old, Duration.ofDays(1));

            assertThat(adapter.isKnown(EMAIL, expired)).isFalse();
            assertThat(adapter.isKnown(EMAIL, others)).isFalse();
            assertThat(adapter.isKnown(EMAIL, old)).isFalse();
            assertThat(adapter.isKnown(EMAIL, new DeviceId("never-seen"))).isFalse();
        }
    }
}
