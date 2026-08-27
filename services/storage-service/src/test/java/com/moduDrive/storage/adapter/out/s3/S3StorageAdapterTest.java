package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.core.ResponseBytes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

    @Mock
    private S3Client s3Client;

    private final Map<String, byte[]> fakeBucket = new HashMap<>();
    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setBucket("test-bucket");
        properties.setEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        adapter = new S3StorageAdapter(s3Client, properties);
    }

    @Nested
    @DisplayName("블록을 저장하고 다시 조회할 때")
    class WhenStoringThenRetrieving {

        @Test
        void roundTripsToOriginalBytes() throws IOException {
            stubS3();
            byte[] original = "hello modudrive block".getBytes(StandardCharsets.UTF_8);

            adapter.storeBlocks("path/to/file", List.of(original));
            List<byte[]> retrieved = adapter.retrieveBlocks("path/to/file", 1);

            assertThat(retrieved).containsExactly(original);
        }

        @Test
        void encryptsIdenticalBlocksDifferently() throws IOException {
            stubPut();
            byte[] block = new byte[32]; // identical zero-filled plaintext blocks

            adapter.storeBlocks("path/to/file", List.of(block, block));

            byte[] stored0 = fakeBucket.get("path/to/file/block_0");
            byte[] stored1 = fakeBucket.get("path/to/file/block_1");
            assertThat(stored0).isNotEqualTo(stored1); // random IV, not ECB
        }
    }

    @Nested
    @DisplayName("암호화된 블록이 다른 위치로 복사되었을 때")
    class WhenABlockIsCopiedToAnotherLocation {

        @Test
        void refusesToDecryptAtTheWrongLocation() throws IOException {
            stubS3();
            byte[] original = "victim file contents".getBytes(StandardCharsets.UTF_8);
            adapter.storeBlocks("victim/path", List.of(original));
            // Same ciphertext (and tag), planted at a different object key — as if an attacker
            // with bucket write access copied another file's block into this file's location.
            fakeBucket.put("attacker/path/block_0", fakeBucket.get("victim/path/block_0"));

            Throwable thrown = catchThrowable(() -> adapter.retrieveBlocks("attacker/path", 1));

            assertThat(thrown).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("blockCount가 상한을 초과할 때")
    class WhenBlockCountExceedsCap {

        @Test
        void throwsBeforeAllocatingAnyArray() {
            Throwable thrown = catchThrowable(() -> adapter.retrieveBlocks("path/to/file", 100_001));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.TOO_MANY_BLOCKS);
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
