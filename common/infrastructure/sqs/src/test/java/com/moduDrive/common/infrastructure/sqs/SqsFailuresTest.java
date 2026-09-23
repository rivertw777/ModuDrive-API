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
    @DisplayName("전송 실패를 분류할 때")
    class WhenClassifyingSendFailures {

        @Test
        @DisplayName("SQS가 이 메시지 때문에 거절한 400은 영구로 본다")
        void treatsAMessageSpecific400AsPermanent() {
            assertThat(SqsFailures.isPermanentSendFailure(
                    new CompletionException(sqsError(400, "InvalidParameterValue")))).isTrue();
        }

        @Test
        @DisplayName("SQS까지 가지도 못한 직렬화 실패도 영구로 본다 — 릴레이가 배치를 멈추므로 뒤 행을 영원히 막는다")
        void treatsAnUnserializablePayloadAsPermanent() {
            assertThat(SqsFailures.isPermanentSendFailure(
                    new MessageConversionException("could not write JSON: no serializer"))).isTrue();
        }

        @Test
        @DisplayName("스로틀링·5xx·권한 거부·연결 실패는 재시도 쪽으로 둔다")
        void treatsOutagesAndThrottlingAsRetryable() {
            assertThat(SqsFailures.isPermanentSendFailure(sqsError(400, "ThrottlingException"))).isFalse();
            assertThat(SqsFailures.isPermanentSendFailure(sqsError(500, "InternalError"))).isFalse();
            assertThat(SqsFailures.isPermanentSendFailure(sqsError(403, "AccessDenied"))).isFalse();
            assertThat(SqsFailures.isPermanentSendFailure(SdkClientException.create("connection refused"))).isFalse();
        }

        @Test
        @DisplayName("SDK가 일시 장애에도 던지는 타입은 영구로 보지 않는다 — 컨슈머 기준을 그대로 쓰면 멀쩡한 행이 격리된다")
        void doesNotReuseTheListenersPermanentTypes() {
            assertThat(SqsFailures.isPermanentSendFailure(new IllegalArgumentException("null group id"))).isFalse();
            assertThat(SqsFailures.isPermanentSendFailure(new NullPointerException())).isFalse();
        }
    }

    @Nested
    @DisplayName("큐의 redrive 설정을 읽을 때")
    class WhenReadingTheRedrivePolicy {

        @Test
        @DisplayName("최대 수신 횟수와 DLQ 이름을 꺼낸다 (AWS의 문자열 숫자도)")
        void readsTheLimitAndDlqName() {
            var fromLocalStack = RedrivePolicy.parse(
                    "{\"deadLetterTargetArn\":\"arn:aws:sqs:ap-northeast-2:000000000000:member-signed-up-dlq\",\"maxReceiveCount\":4}");
            var fromAws = RedrivePolicy.parse(
                    "{\"deadLetterTargetArn\":\"arn:aws:sqs:ap-northeast-2:123:mail-dlq\",\"maxReceiveCount\":\"5\"}");

            assertThat(fromLocalStack).isEqualTo(new RedrivePolicy(4, "member-signed-up-dlq"));
            assertThat(fromAws).isEqualTo(new RedrivePolicy(5, "mail-dlq"));
            assertThat(RedrivePolicy.parse(null)).isEqualTo(RedrivePolicy.NONE);
        }
    }

    @Test
    @DisplayName("DLQ 사유에는 감싼 예외가 아니라 실제 원인을 적는다")
    void reasonNamesTheActualCause() {
        assertThat(DeadLetteringErrorHandler.reason(new RuntimeException("listener failed", new IllegalStateException("smtp down"))))
                .isEqualTo("java.lang.IllegalStateException: smtp down");
    }

    @Test
    @DisplayName("redrive 설정이 없으면 DLQ 이름은 <이름>-dlq 규칙으로 정한다")
    void namesTheDeadLetterQueue() {
        assertThat(RedrivePolicy.NONE.deadLetterQueueOr("member-signed-up")).isEqualTo("member-signed-up-dlq");
        assertThat(new RedrivePolicy(4, "custom-dlq").deadLetterQueueOr("member-signed-up")).isEqualTo("custom-dlq");
    }

    private static SqsException sqsError(int status, String code) {
        return (SqsException) SqsException.builder().statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build()).build();
    }
}
