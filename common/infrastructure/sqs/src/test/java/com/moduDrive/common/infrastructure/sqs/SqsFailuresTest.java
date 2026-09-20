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
    @DisplayName("FIFO 메시지 그룹 id를 만들 때")
    class WhenBuildingTheGroupId {

        @Test
        @DisplayName("128자를 넘는 키는 같은 키면 같은 값이 나오게 줄인다")
        void shortensKeysPastSqsLimitStably() {
            String longEmail = "a".repeat(200) + "@example.com";

            assertThat(SqsMessagePublisher.groupId(longEmail)).hasSizeLessThanOrEqualTo(128)
                    .isEqualTo(SqsMessagePublisher.groupId(longEmail));
            assertThat(SqsMessagePublisher.groupId("river@modudrive.com")).isEqualTo("river@modudrive.com");
            assertThat(SqsMessagePublisher.groupId(null)).isNotBlank();
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
            var fromElasticMq = DeadLetteringErrorHandler.RedrivePolicy.parse(
                    "{\"deadLetterTargetArn\":\"arn:aws:sqs:elasticmq:000000000000:member-signed-up-dlq.fifo\",\"maxReceiveCount\":4}");
            var fromAws = DeadLetteringErrorHandler.RedrivePolicy.parse(
                    "{\"deadLetterTargetArn\":\"arn:aws:sqs:ap-northeast-2:123:mail-dlq.fifo\",\"maxReceiveCount\":\"5\"}");

            assertThat(fromElasticMq).isEqualTo(new DeadLetteringErrorHandler.RedrivePolicy(4, "member-signed-up-dlq.fifo"));
            assertThat(fromAws).isEqualTo(new DeadLetteringErrorHandler.RedrivePolicy(5, "mail-dlq.fifo"));
            assertThat(DeadLetteringErrorHandler.RedrivePolicy.parse(null)).isEqualTo(DeadLetteringErrorHandler.RedrivePolicy.NONE);
        }
    }

    @Test
    @DisplayName("DLQ 사유에는 감싼 예외가 아니라 실제 원인을 적는다")
    void reasonNamesTheActualCause() {
        assertThat(DeadLetteringErrorHandler.reason(new RuntimeException("listener failed", new IllegalStateException("smtp down"))))
                .isEqualTo("java.lang.IllegalStateException: smtp down");
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
