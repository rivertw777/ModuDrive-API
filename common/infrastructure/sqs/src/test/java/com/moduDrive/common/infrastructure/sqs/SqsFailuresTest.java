package com.moduDrive.common.infrastructure.sqs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MessageConversionException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class SqsFailuresTest {

    @Nested
    @DisplayName("컨슈머 실패를 분류할 때")
    class WhenClassifyingConsumerFailures {

        @Test
        @DisplayName("다시 해도 같은 결과인 실패는 원인 체인 안쪽에 있어도, 하위 타입이어도 영구로 본다")
        void treatsPermanentTypesAnywhereInTheChainAsPermanent() {
            assertThat(SqsFailures.isPermanentConsumerFailure(
                    new RuntimeException("listener failed", new IllegalArgumentException("bad role")))).isTrue();
            assertThat(SqsFailures.isPermanentConsumerFailure(new NumberFormatException("x"))).isTrue();
            assertThat(SqsFailures.isPermanentConsumerFailure(new MessageConversionException("not json"))).isTrue();
        }

        @Test
        @DisplayName("일시 장애나 모르는 예외는 재시도 쪽으로 둔다")
        void treatsTransientAndUnknownFailuresAsRetryable() {
            assertThat(SqsFailures.isPermanentConsumerFailure(new IllegalStateException("smtp down"))).isFalse();
            assertThat(SqsFailures.isPermanentConsumerFailure(new RuntimeException("?"))).isFalse();
        }
    }

    @Nested
    @DisplayName("전송 실패를 분류할 때")
    class WhenClassifyingSendFailures {

        @Test
        @DisplayName("SQS가 이 메시지 때문에 거절한 400은 영구로 본다")
        void treatsAMessageSpecific400AsPermanent() {
            assertThat(SqsFailures.isPermanentSendFailure(
                    new CompletionException(sqsError(400, "InvalidParameterValue")))).isTrue();
        }

        @Test
        @DisplayName("스로틀링·5xx·권한 거부·연결 실패는 재시도 쪽으로 둔다")
        void treatsOutagesAndThrottlingAsRetryable() {
            assertThat(SqsFailures.isPermanentSendFailure(sqsError(400, "ThrottlingException"))).isFalse();
            assertThat(SqsFailures.isPermanentSendFailure(sqsError(500, "InternalError"))).isFalse();
            assertThat(SqsFailures.isPermanentSendFailure(sqsError(403, "AccessDenied"))).isFalse();
            assertThat(SqsFailures.isPermanentSendFailure(SdkClientException.create("connection refused"))).isFalse();
        }
    }

    @Test
    @DisplayName("DLQ 이름은 <이름>-dlq.fifo 규칙을 따른다")
    void namesTheDeadLetterQueue() {
        assertThat(DeadLetteringErrorHandler.deadLetterQueueName("member-signed-up.fifo"))
                .isEqualTo("member-signed-up-dlq.fifo");
    }

    private static SqsException sqsError(int status, String code) {
        return (SqsException) SqsException.builder().statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build()).build();
    }
}
