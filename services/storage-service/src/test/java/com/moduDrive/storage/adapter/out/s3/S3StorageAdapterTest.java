package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.exception.StorageExceptionCase;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

    private static final String KEY = "blocks/owner/aaa";
    private static final Instant DECIDED_AT = Instant.parse("2026-10-08T12:00:00.500Z");

    @Mock
    private S3Client s3Client;

    private final Map<String, byte[]> fakeBucket = new HashMap<>();
    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .recordException(new S3Unavailable())
            .slidingWindowSize(2).minimumNumberOfCalls(2)
            .build());
    private final BulkheadRegistry bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
            .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setBucket("test-bucket");
        properties.setEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        adapter = new S3StorageAdapter(s3Client, properties, circuitBreakers, bulkheads);
    }

    @Nested
    @DisplayName("블록을 저장하고 다시 조회할 때")
    class WhenStoringThenRetrieving {

        @Test
        void roundTripsToOriginalBytesInKeyOrder() throws IOException {
            stubS3();
            adapter.storeBlock("blocks/o/b", "world".getBytes());
            adapter.storeBlock("blocks/o/a", "hello ".getBytes());

            assertThat(adapter.retrieveBlocks(List.of("blocks/o/a", "blocks/o/b", "blocks/o/a")))
                    .containsExactly("hello ".getBytes(), "world".getBytes(), "hello ".getBytes());
        }

        @Test
        void streamsTheSameBytes() throws IOException {
            stubS3();
            adapter.storeBlock("blocks/o/a", "hello ".getBytes());
            adapter.storeBlock("blocks/o/b", "world".getBytes());
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            adapter.streamBlocks(List.of("blocks/o/a", "blocks/o/b"), out);

            assertThat(out.toByteArray()).isEqualTo("hello world".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void encryptsTheSameBlockDifferentlyEachTime() throws IOException {
            stubPut();
            adapter.storeBlock(KEY, new byte[32]);
            byte[] first = fakeBucket.get(KEY);
            adapter.storeBlock(KEY, new byte[32]);

            assertThat(fakeBucket.get(KEY)).isNotEqualTo(first); // random IV, not ECB
        }
    }

    @Nested
    @DisplayName("암호화된 블록이 다른 위치로 복사되었을 때")
    class WhenABlockIsCopiedToAnotherLocation {

        @Test
        void refusesToDecryptAtTheWrongLocation() throws IOException {
            stubS3();
            adapter.storeBlock("blocks/victim/h", "victim file contents".getBytes(StandardCharsets.UTF_8));
            // Same ciphertext (and tag), planted at another owner's key — as if someone with bucket
            // write access copied a block across.
            fakeBucket.put("blocks/attacker/h", fakeBucket.get("blocks/victim/h"));

            Throwable thrown = catchThrowable(() -> adapter.retrieveBlocks(List.of("blocks/attacker/h")));

            assertThat(thrown).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("블록 저장이 실패했을 때")
    class WhenStoringFails {

        @Test
        void throwsStorageError() {
            willThrow(SdkClientException.create("network blip"))
                    .given(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

            Throwable thrown = catchThrowable(() -> adapter.storeBlock(KEY, "x".getBytes()));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.STORAGE_ERROR);
        }
    }

    @Nested
    @DisplayName("블록을 지울 때")
    class WhenDeleting {

        private void headReturns(Instant lastModified, String eTag) {
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willReturn(HeadObjectResponse.builder().lastModified(lastModified).eTag(eTag).build());
        }

        @Test
        @DisplayName("결정 전에 쓰인 블록은 HEAD 때 본 ETag를 조건으로 지운다")
        void deletesABlockWrittenBeforeTheDecisionConditionally() {
            headReturns(DECIDED_AT.minusSeconds(3600), "\"etag-1\"");

            adapter.deleteUnlessRewritten(KEY, DECIDED_AT);

            then(s3Client).should().deleteObject(argThat((DeleteObjectRequest r) ->
                    r.key().equals(KEY) && "\"etag-1\"".equals(r.ifMatch())));
        }

        @Test
        @DisplayName("결정과 같은 초에 쓰인 블록은 결정 뒤로 보고 남긴다")
        void keepsABlockWrittenInTheSameSecond() {
            headReturns(Instant.parse("2026-10-08T12:00:00Z"), "\"e\"");

            adapter.deleteUnlessRewritten(KEY, DECIDED_AT);

            then(s3Client).should(never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("결정 뒤에 다시 올라온 블록은 남긴다")
        void keepsABlockRewrittenAfterTheDecision() {
            headReturns(DECIDED_AT.plusSeconds(60), "\"e\"");

            adapter.deleteUnlessRewritten(KEY, DECIDED_AT);

            then(s3Client).should(never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("이미 없는 블록이면 아무것도 하지 않는다")
        void ignoresAMissingBlock() {
            given(s3Client.headObject(any(HeadObjectRequest.class))).willThrow(NoSuchKeyException.builder().statusCode(404).build());

            adapter.deleteUnlessRewritten(KEY, DECIDED_AT);

            then(s3Client).should(never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("HEAD와 DELETE 사이에 덮어써져 조건이 맞지 않으면(412) 남긴다")
        void keepsABlockOverwrittenBetweenHeadAndDelete() {
            headReturns(DECIDED_AT.minusSeconds(3600), "\"old\"");
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willThrow(S3Exception.builder().statusCode(412).build());

            adapter.deleteUnlessRewritten(KEY, DECIDED_AT);
        }

        @Test
        @DisplayName("그 밖의 삭제 실패는 다시 시도할 수 있게 던진다")
        void throwsOtherFailures() {
            headReturns(DECIDED_AT.minusSeconds(3600), "\"e\"");
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willThrow(S3Exception.builder().statusCode(500).build());

            Throwable thrown = catchThrowable(() -> adapter.deleteUnlessRewritten(KEY, DECIDED_AT));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.STORAGE_ERROR);
        }
    }

    @Nested
    @DisplayName("블록 수가 상한을 넘을 때")
    class WhenBlockCountExceedsCap {

        private final List<String> tooMany = Collections.nCopies(100_001, KEY);

        @Test
        void retrieveThrowsBeforeFetchingAnything() {
            Throwable thrown = catchThrowable(() -> adapter.retrieveBlocks(tooMany));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.TOO_MANY_BLOCKS);
            then(s3Client).shouldHaveNoInteractions();
        }

        @Test
        void streamThrowsBeforeFetchingAnything() {
            Throwable thrown = catchThrowable(() -> adapter.streamBlocks(tooMany, new ByteArrayOutputStream()));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.TOO_MANY_BLOCKS);
            then(s3Client).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("S3가 응답하지 않을 때 (spec 006 2-4)")
    class WhenS3IsDown {

        @Test
        @DisplayName("실패가 쌓여 서킷이 열리면 S3를 부르지 않고 바로 STORAGE_UNAVAILABLE")
        void opensTheCircuitAndAnswersAtOnce() {
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willThrow(SdkClientException.create("timeout"));
            for (int i = 0; i < 2; i++) {
                assertThat(((BusinessException) catchThrowable(() -> adapter.storeBlock(KEY, new byte[1])))
                        .getExceptionCase()).isEqualTo(StorageExceptionCase.STORAGE_ERROR);
            }

            Throwable thrown = catchThrowable(() -> adapter.retrieveBlocks(List.of(KEY)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.STORAGE_UNAVAILABLE);
            then(s3Client).should(never()).getObjectAsBytes(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("없는 객체(404)는 S3 장애로 세지 않는다")
        void doesNotCountANotFound() {
            given(s3Client.headObject(any(HeadObjectRequest.class))).willThrow(NoSuchKeyException.builder().statusCode(404).build());

            for (int i = 0; i < 3; i++) {
                adapter.deleteUnlessRewritten(KEY, DECIDED_AT);
            }

            assertThat(circuitBreakers.circuitBreaker("s3CircuitBreaker").getMetrics().getNumberOfFailedCalls()).isZero();
        }

        @Test
        @DisplayName("업로드 자리가 가득 차면 기다리지 않고 STORAGE_UNAVAILABLE")
        void refusesWhenTheBulkheadIsFull() {
            bulkheads.bulkhead("s3Write").tryAcquirePermission();

            Throwable thrown = catchThrowable(() -> adapter.storeBlock(KEY, new byte[1]));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.STORAGE_UNAVAILABLE);
            then(s3Client).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("업로드 자리가 가득 차도 다운로드는 자기 자리로 S3를 부른다")
        void keepsReadsApartFromWrites() throws IOException {
            stubS3();
            adapter.storeBlock(KEY, "a".getBytes());
            bulkheads.bulkhead("s3Write").tryAcquirePermission();

            assertThat(adapter.retrieveBlocks(List.of(KEY))).containsExactly("a".getBytes());
        }
    }

    private void stubS3() throws IOException {
        stubPut();
        willAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), fakeBucket.get(request.key()));
        }).given(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    private void stubPut() throws IOException {
        willAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            fakeBucket.put(request.key(), body.contentStreamProvider().newStream().readAllBytes());
            return PutObjectResponse.builder().build();
        }).given(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
