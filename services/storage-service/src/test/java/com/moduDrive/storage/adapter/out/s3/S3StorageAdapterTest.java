package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.domain.model.Blocks;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

    private static final String KEY = "blocks/owner/aaa";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final Instant DECIDED_AT = Instant.parse("2026-10-08T12:00:00.500Z");

    @Mock
    private S3Client s3Client;

    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .recordException(new S3Unavailable())
            .slidingWindowSize(2).minimumNumberOfCalls(2)
            .build());
    private final BulkheadRegistry bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
            .maxConcurrentCalls(S3StorageAdapter.LOOKUP_PARALLELISM).maxWaitDuration(Duration.ZERO).build());
    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setBucket("test-bucket");
        adapter = new S3StorageAdapter(s3Client, properties, circuitBreakers, bulkheads);
    }

    @Nested
    @DisplayName("블록을 조회할 때")
    class WhenRetrieving {

        @Test
        @DisplayName("S3에 있는 바이트를 그대로, 키 순서대로 돌려준다")
        void returnsTheStoredBytesInKeyOrder() {
            stubGet(Map.of("blocks/o/a", "hello ".getBytes(), "blocks/o/b", "world".getBytes()));

            assertThat(adapter.retrieveBlocks(List.of("blocks/o/a", "blocks/o/b", "blocks/o/a")))
                    .containsExactly("hello ".getBytes(), "world".getBytes(), "hello ".getBytes());
        }

        @Test
        @DisplayName("같은 바이트를 스트림으로도 쓴다")
        void streamsTheSameBytes() {
            stubGet(Map.of("blocks/o/a", "hello ".getBytes(), "blocks/o/b", "world".getBytes()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            adapter.streamBlocks(List.of("blocks/o/a", "blocks/o/b"), out);

            assertThat(out.toByteArray()).isEqualTo("hello world".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("commit 전에 올라온 블록을 확인할 때 (spec 001 5)")
    class WhenFindingUploaded {

        private void headReturns(String hash, Instant lastModified, String checksum, long size) {
            given(s3Client.headObject(argThat((HeadObjectRequest r) -> r != null && r.key().equals(Blocks.key(OWNER, hash)))))
                    .willReturn(HeadObjectResponse.builder()
                            .lastModified(lastModified).checksumSHA256(checksum).contentLength(size).build());
        }

        @Test
        @DisplayName("체크섬이 해시와 같으면 그 크기로 답한다")
        void answersAFreshIntactBlockWithItsSize() {
            headReturns(HASH_A, Instant.now().minusSeconds(60), Blocks.sha256Base64(HASH_A), 4);
            headReturns(HASH_B, Instant.now().minusSeconds(60), Blocks.sha256Base64(HASH_B), 6);

            assertThat(adapter.findUploaded(OWNER, List.of(HASH_A, HASH_B))).containsOnly(
                    Map.entry(HASH_A, 4), Map.entry(HASH_B, 6));
            then(s3Client).should(org.mockito.Mockito.times(2)).headObject(argThat((HeadObjectRequest r) ->
                    r.bucket().equals("test-bucket") && r.checksumMode() == ChecksumMode.ENABLED));
        }

        @Test
        @DisplayName("없는 블록(404)은 빼고 답한다")
        void leavesOutAMissingBlock() {
            headReturns(HASH_A, Instant.now(), Blocks.sha256Base64(HASH_A), 4);
            given(s3Client.headObject(argThat((HeadObjectRequest r) -> r != null && r.key().equals(Blocks.key(OWNER, HASH_B)))))
                    .willThrow(NoSuchKeyException.builder().statusCode(404).build());

            assertThat(adapter.findUploaded(OWNER, List.of(HASH_A, HASH_B))).containsOnlyKeys(HASH_A);
        }

        @Test
        @DisplayName("체크섬 없이 올라왔거나 다른 체크섬이면 뺀다 — URL 서명에만 기대지 않는다")
        void leavesOutABlockWithoutTheMatchingChecksum() {
            headReturns(HASH_A, Instant.now(), null, 4);
            headReturns(HASH_B, Instant.now(), Blocks.sha256Base64(HASH_A), 4);

            assertThat(adapter.findUploaded(OWNER, List.of(HASH_A, HASH_B))).isEmpty();
        }

        @Test
        @DisplayName("빈 목록이면 S3에 묻지 않는다")
        void asksNothingForNoHashes() {
            assertThat(adapter.findUploaded(OWNER, List.of())).isEmpty();
            then(s3Client).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("HEAD 하나라도 실패하면 조회 전체를 STORAGE_ERROR로 끝낸다")
        void failsTheWholeLookupOnAnyFailure() {
            headReturns(HASH_A, Instant.now(), Blocks.sha256Base64(HASH_A), 4);
            given(s3Client.headObject(argThat((HeadObjectRequest r) -> r != null && r.key().equals(Blocks.key(OWNER, HASH_B)))))
                    .willThrow(S3Exception.builder().statusCode(403).build());

            Throwable thrown = catchThrowable(() -> adapter.findUploaded(OWNER, List.of(HASH_A, HASH_B)));

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
            given(s3Client.headObject(any(HeadObjectRequest.class))).willThrow(SdkClientException.create("timeout"));
            for (int i = 0; i < 2; i++) {
                assertThat(((BusinessException) catchThrowable(() -> adapter.findUploaded(OWNER, List.of(HASH_A))))
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
        @DisplayName("블록 확인 자리가 가득 차면 기다리지 않고 STORAGE_UNAVAILABLE")
        void refusesWhenTheBulkheadIsFull() {
            fill(bulkheads.bulkhead("s3Lookup"));

            Throwable thrown = catchThrowable(() -> adapter.findUploaded(OWNER, List.of(HASH_A)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.STORAGE_UNAVAILABLE);
            then(s3Client).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("블록 확인 자리가 가득 차도 다운로드는 자기 자리로 S3를 부른다")
        void keepsReadsApartFromLookups() {
            stubGet(Map.of(KEY, "a".getBytes()));
            fill(bulkheads.bulkhead("s3Lookup"));

            assertThat(adapter.retrieveBlocks(List.of(KEY))).containsExactly("a".getBytes());
        }

        private void fill(io.github.resilience4j.bulkhead.Bulkhead bulkhead) {
            while (bulkhead.tryAcquirePermission()) {
                // take every seat
            }
        }
    }

    private void stubGet(Map<String, byte[]> bucket) {
        willAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bucket.get(request.key()));
        }).given(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }
}
