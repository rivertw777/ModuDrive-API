package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import com.moduDrive.common.infrastructure.messaging.PermanentPublishException;
import io.awspring.cloud.sqs.operations.SendBatchOperationFailedException;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SqsMessagePublisherTest {

    private static final List<MessagePublisher.Outgoing> MESSAGES = List.of(
            new MessagePublisher.Outgoing("outbox-1", "a", Map.of()),
            new MessagePublisher.Outgoing("outbox-2", "b", Map.of()),
            new MessagePublisher.Outgoing("outbox-3", "c", Map.of()));

    private final SqsOperations sqsOperations = mock(SqsOperations.class);
    private final SqsMessagePublisher publisher = new SqsMessagePublisher(sqsOperations);

    @Nested
    @DisplayName("묶음 중 일부만 실패하면")
    class WhenSomeEntriesFail {

        @Test
        @DisplayName("실패한 메시지만 중복 제거 id로 돌려주고, 보낸 쪽 잘못은 재처리 불가로 나눈다")
        void returnsOnlyTheFailedEntriesClassified() {
            // given
            SendResult.Batch<Object> result = new SendResult.Batch<>(
                    List.of(sent("outbox-3")),
                    List.of(failed("outbox-1", true, "InvalidParameterValue"), failed("outbox-2", false, "InternalError")));
            given(sqsOperations.sendMany(anyString(), anyCollection()))
                    .willThrow(new SendBatchOperationFailedException("partial", "queue", result));

            // when
            Map<String, RuntimeException> failures = publisher.publishBatch("queue", MESSAGES);

            // then
            assertThat(failures).containsOnlyKeys("outbox-1", "outbox-2");
            assertThat(failures.get("outbox-1")).isInstanceOf(PermanentPublishException.class);
            assertThat(failures.get("outbox-2")).isNotInstanceOf(PermanentPublishException.class)
                    .hasMessageContaining("InternalError");
        }

        @Test
        @DisplayName("예외 없이 결과로 돌아와도(DO_NOT_THROW) 실패를 놓치지 않는다")
        void readsFailuresFromTheReturnedResultToo() {
            // given
            given(sqsOperations.sendMany(anyString(), anyCollection())).willReturn(new SendResult.Batch<>(
                    List.of(), List.of(failed("outbox-1", false, "InternalError"))));

            // when
            Map<String, RuntimeException> failures = publisher.publishBatch("queue", MESSAGES);

            // then
            assertThat(failures).containsOnlyKeys("outbox-1");
        }

        @Test
        @DisplayName("어느 행인지 알 수 없는 실패면 호출 전체를 실패시킨다 — 그 행이 SENT로 남으면 이벤트가 사라진다")
        void failsTheCallWhenAFailureCantBeMatchedToARow() {
            // given
            Message<Object> anonymous = MessageBuilder.<Object>withPayload("a").build();
            given(sqsOperations.sendMany(anyString(), anyCollection())).willReturn(new SendResult.Batch<>(
                    List.of(), List.of(new SendResult.Failed<>("boom", "queue", anonymous, Map.of()))));

            // when / then
            assertThatThrownBy(() -> publisher.publishBatch("queue", MESSAGES))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private static Message<Object> message(String deduplicationId) {
        return MessageBuilder.<Object>withPayload("x").setHeader(SqsAttributes.DEDUPLICATION_ID, deduplicationId).build();
    }

    private static SendResult<Object> sent(String deduplicationId) {
        return new SendResult<>(UUID.randomUUID(), "queue", message(deduplicationId), Map.of());
    }

    private static SendResult.Failed<Object> failed(String deduplicationId, boolean senderFault, String code) {
        return new SendResult.Failed<>("rejected", "queue", message(deduplicationId),
                Map.of("senderFault", senderFault, "code", code));
    }
}
