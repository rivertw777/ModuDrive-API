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
            assertThat(PermanentFailures.isPermanentForConsumer(
                    new RuntimeException("listener failed", new IllegalArgumentException("bad role")))).isTrue();
            assertThat(PermanentFailures.isPermanentForConsumer(new NumberFormatException("x"))).isTrue();
            assertThat(PermanentFailures.isPermanentForConsumer(new MessageConversionException("not json"))).isTrue();
        }

        @Test
        @DisplayName("일시 장애나 모르는 예외는 재시도 쪽으로 둔다")
        void treatsTransientAndUnknownFailuresAsRetryable() {
            assertThat(PermanentFailures.isPermanentForConsumer(new IllegalStateException("smtp down"))).isFalse();
            assertThat(PermanentFailures.isPermanentForConsumer(new RuntimeException("?"))).isFalse();
        }
    }

    @Nested
    @DisplayName("퍼블리셔 실패를 분류할 때")
    class WhenClassifyingPublisherFailures {

        @Test
        @DisplayName("직렬화가 안 되는 페이로드는 원인 체인 안쪽에 있어도 영구로 본다")
        void treatsConversionFailuresAsPermanent() {
            assertThat(PermanentFailures.isPermanentForPublisher(
                    new RuntimeException("send failed", new MessageConversionException("could not write JSON")))).isTrue();
        }

        @Test
        @DisplayName("컨슈머 쪽에서만 영구인 타입은 전송 경로에서 재시도 쪽으로 둔다 — SDK가 일시 장애에도 던지는 타입이라 멀쩡한 행이 격리되면 안 된다")
        void keepsListenerOnlyTypesRetryableOnTheSendPath() {
            assertThat(PermanentFailures.isPermanentForPublisher(new IllegalArgumentException("bad role"))).isFalse();
            assertThat(PermanentFailures.isPermanentForPublisher(new NullPointerException())).isFalse();
            assertThat(PermanentFailures.isPermanentForPublisher(new IllegalStateException("sqs down"))).isFalse();
        }
    }
}
