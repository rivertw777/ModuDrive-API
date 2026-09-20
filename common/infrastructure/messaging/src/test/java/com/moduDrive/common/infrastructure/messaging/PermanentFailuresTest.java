package com.moduDrive.common.infrastructure.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MessageConversionException;

import static org.assertj.core.api.Assertions.assertThat;

class PermanentFailuresTest {

    @Nested
    @DisplayName("컨슈머 실패를 분류할 때")
    class WhenClassifyingConsumerFailures {

        @Test
        @DisplayName("다시 해도 같은 결과인 실패는 원인 체인 안쪽에 있어도, 하위 타입이어도 영구로 본다")
        void treatsPermanentTypesAnywhereInTheChainAsPermanent() {
            assertThat(PermanentFailures.isPermanent(
                    new RuntimeException("listener failed", new IllegalArgumentException("bad role")))).isTrue();
            assertThat(PermanentFailures.isPermanent(new NumberFormatException("x"))).isTrue();
            assertThat(PermanentFailures.isPermanent(new MessageConversionException("not json"))).isTrue();
        }

        @Test
        @DisplayName("일시 장애나 모르는 예외는 재시도 쪽으로 둔다")
        void treatsTransientAndUnknownFailuresAsRetryable() {
            assertThat(PermanentFailures.isPermanent(new IllegalStateException("smtp down"))).isFalse();
            assertThat(PermanentFailures.isPermanent(new RuntimeException("?"))).isFalse();
        }
    }
}
