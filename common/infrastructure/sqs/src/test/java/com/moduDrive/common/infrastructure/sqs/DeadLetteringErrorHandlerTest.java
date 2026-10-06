package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.RetryLaterException;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("SQS 소비 실패 처리는")
@SuppressWarnings("unchecked")
class DeadLetteringErrorHandlerTest {

    private static final int MAX_RECEIVE_COUNT = 4;

    @Mock private SqsAsyncClient sqsAsyncClient;
    @Mock private AsyncErrorHandler<Object> retry;
    @Mock private Visibility visibility;

    private DeadLetteringErrorHandler handler;

    @BeforeEach
    void setUp() {
        given(sqsAsyncClient.getQueueAttributes(any(Consumer.class))).willReturn(CompletableFuture.completedFuture(
                GetQueueAttributesResponse.builder().attributes(Map.of(QueueAttributeName.REDRIVE_POLICY,
                        "{\"maxReceiveCount\":\"" + MAX_RECEIVE_COUNT + "\",\"deadLetterTargetArn\":\"arn:aws:sqs:ap-northeast-2:0:q-dlq\"}"))
                        .build()));
        handler = new DeadLetteringErrorHandler(sqsAsyncClient, retry);
    }

    private Message<Object> message(int receiveCount) {
        return MessageBuilder.<Object>withPayload("{}")
                .setHeader(SqsHeaders.SQS_QUEUE_URL_HEADER, "http://sqs/q")
                .setHeader(SqsHeaders.SQS_QUEUE_NAME_HEADER, "q")
                .setHeader(SqsHeaders.SQS_SOURCE_DATA_HEADER,
                        software.amazon.awssdk.services.sqs.model.Message.builder().messageId("m-1").body("{}").build())
                .setHeader(MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT, String.valueOf(receiveCount))
                .setHeader(SqsHeaders.SQS_VISIBILITY_TIMEOUT_HEADER, visibility)
                .build();
    }

    private void givenDeadLetterQueueAcceptsIt() {
        given(sqsAsyncClient.getQueueUrl(any(Consumer.class))).willReturn(CompletableFuture.completedFuture(
                GetQueueUrlResponse.builder().queueUrl("http://sqs/q-dlq").build()));
        given(sqsAsyncClient.sendMessage(any(Consumer.class))).willReturn(
                CompletableFuture.completedFuture(SendMessageResponse.builder().build()));
    }

    /** The request the handler sent to the DLQ: where it went and the reason it carried. */
    private SendMessageRequest sentToDeadLetterQueue() {
        ArgumentCaptor<Consumer<GetQueueUrlRequest.Builder>> urlRequest = ArgumentCaptor.forClass(Consumer.class);
        then(sqsAsyncClient).should().getQueueUrl(urlRequest.capture());
        GetQueueUrlRequest.Builder url = GetQueueUrlRequest.builder();
        urlRequest.getValue().accept(url);
        assertThat(url.build().queueName()).isEqualTo("q-dlq");

        ArgumentCaptor<Consumer<SendMessageRequest.Builder>> sendRequest = ArgumentCaptor.forClass(Consumer.class);
        then(sqsAsyncClient).should().sendMessage(sendRequest.capture());
        SendMessageRequest.Builder send = SendMessageRequest.builder();
        sendRequest.getValue().accept(send);
        return send.build();
    }

    private static String reason(SendMessageRequest request) {
        return request.messageAttributes().get(DeadLetteringErrorHandler.REASON_ATTRIBUTE).stringValue();
    }

    @Nested
    @DisplayName("나중에 다시 하라는 실패(RetryLater)면")
    class WhenToldToRetryLater {

        @Test
        @DisplayName("감싸져 있어도 찾아내 그 시간만큼 가시성을 늘리고, 확인 응답하지 않게 실패로 끝낸다")
        void delaysTheRedeliveryByTheNamedDelay() {
            given(visibility.changeToAsync(anyInt())).willReturn(CompletableFuture.completedFuture(null));
            var failure = new RuntimeException("listener failed",
                    new RetryLaterException(Duration.ofSeconds(60), new RuntimeException("timed out")));

            CompletableFuture<Void> result = handler.handle(message(1), failure);

            then(visibility).should().changeToAsync(60);
            then(retry).shouldHaveNoInteractions();
            assertThat(result).isCompletedExceptionally();
        }

        @Test
        @DisplayName("마지막 수신이면 더 미루지 않고 DLQ로 옮긴다")
        void movesItToTheDlqOnTheLastReceive() {
            givenDeadLetterQueueAcceptsIt();
            var failure = new RetryLaterException(Duration.ofSeconds(60), new RuntimeException("timed out"));

            CompletableFuture<Void> result = handler.handle(message(MAX_RECEIVE_COUNT), failure);

            assertThat(result).isCompleted();
            assertThat(reason(sentToDeadLetterQueue())).startsWith("Retries exhausted after 4 attempts");
            then(visibility).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("일반 실패면")
    class WhenItFailsPlainly {

        @Test
        @DisplayName("재시도 횟수가 남았으면 기본 백오프에 맡긴다")
        void backsOff() {
            var failure = new CompletionException(new RuntimeException("5xx"));
            Message<Object> message = message(1);
            given(retry.handle(message, failure)).willReturn(CompletableFuture.failedFuture(failure));

            CompletableFuture<Void> result = handler.handle(message, failure);

            assertThat(result).isCompletedExceptionally();
            then(retry).should().handle(message, failure);
            then(sqsAsyncClient).should(never()).sendMessage(any(Consumer.class));
            then(visibility).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("영구 실패면 남은 횟수와 상관없이 바로 DLQ로 옮긴다")
        void movesAPermanentFailureNow() {
            givenDeadLetterQueueAcceptsIt();

            CompletableFuture<Void> result = handler.handle(message(1), new IllegalArgumentException("bad value"));

            assertThat(result).isCompleted();
            assertThat(reason(sentToDeadLetterQueue())).isEqualTo("java.lang.IllegalArgumentException: bad value");
            then(retry).shouldHaveNoInteractions();
        }
    }
}
