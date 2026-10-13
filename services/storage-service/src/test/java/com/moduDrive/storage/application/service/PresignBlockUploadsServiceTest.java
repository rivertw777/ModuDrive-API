package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.PresignBlockUploadsCommand;
import com.moduDrive.storage.application.port.in.command.PresignBlockUploadsCommand.Block;
import com.moduDrive.storage.application.port.out.PresignBlockUploadPort;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PresignBlockUploadsServiceTest {

    private static final int BLOCK_SIZE = 8;
    private static final int LIMIT = 3;
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Mock private PresignBlockUploadPort presignBlockUploadPort;
    @Mock private RecordUploadedBlockPort recordUploadedBlockPort;
    private PresignBlockUploadsService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PresignBlockUploadsService(presignBlockUploadPort, recordUploadedBlockPort, BLOCK_SIZE, LIMIT);
    }

    private StorageExceptionCase rejection(List<Block> blocks) {
        Throwable thrown = catchThrowable(() -> service.presignBlockUploads(new PresignBlockUploadsCommand(userId, blocks)));
        then(presignBlockUploadPort).shouldHaveNoInteractions();
        then(recordUploadedBlockPort).shouldHaveNoInteractions();
        return (StorageExceptionCase) ((BusinessException) thrown).getExceptionCase();
    }

    @Nested
    @DisplayName("블록이 모두 올바를 때")
    class WhenEveryBlockIsValid {

        @Test
        @DisplayName("블록마다 호출자 공간의 해시 키로 서명한 URL을 요청 순서대로 돌려준다")
        void signsEachUnderTheCallersKeyInOrder() {
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true);
            given(presignBlockUploadPort.presignUpload(Blocks.key(userId, HASH_A), 4, HASH_A)).willReturn("url-a");
            given(presignBlockUploadPort.presignUpload(Blocks.key(userId, HASH_B), 8, HASH_B)).willReturn("url-b");

            List<String> urls = service.presignBlockUploads(new PresignBlockUploadsCommand(userId,
                    List.of(new Block(HASH_A, 4), new Block(HASH_B, 8))));

            assertThat(urls).containsExactly("url-a", "url-b");
        }

        @Test
        @DisplayName("정리 대상 등록 → URL 서명 순서로 한다")
        void schedulesBeforeSigning() {
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true);

            service.presignBlockUploads(new PresignBlockUploadsCommand(userId, List.of(new Block(HASH_A, 4))));

            InOrder order = inOrder(recordUploadedBlockPort, presignBlockUploadPort);
            order.verify(recordUploadedBlockPort).scheduleSweep(userId, HASH_A);
            order.verify(presignBlockUploadPort).presignUpload(Blocks.key(userId, HASH_A), 4, HASH_A);
        }

        @Test
        @DisplayName("업로드 한도에 닿으면 그 블록부터 서명하지 않는다")
        void stopsAtTheUploadLimit() {
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true, false);

            Throwable thrown = catchThrowable(() -> service.presignBlockUploads(new PresignBlockUploadsCommand(userId,
                    List.of(new Block(HASH_A, 4), new Block(HASH_B, 4)))));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.UPLOAD_LIMIT_EXCEEDED);
            then(presignBlockUploadPort).should().presignUpload(Blocks.key(userId, HASH_A), 4, HASH_A);
            then(presignBlockUploadPort).should(never()).presignUpload(Blocks.key(userId, HASH_B), 4, HASH_B);
            then(recordUploadedBlockPort).should(never()).scheduleSweep(userId, HASH_B);
        }
    }

    @Nested
    @DisplayName("하나라도 올바르지 않을 때 — 아무것도 서명하지 않는다")
    class WhenAnyBlockIsInvalid {

        @Test
        @DisplayName("해시 형식이 아니면 (경로 문자로 다른 키를 노리는 경우 포함)")
        void rejectsAMalformedHash() {
            assertThat(rejection(List.of(new Block(HASH_A, 4), new Block("../" + HASH_B.substring(3), 4))))
                    .isEqualTo(StorageExceptionCase.INVALID_BLOCK);
        }

        @Test
        @DisplayName("빈 블록이면")
        void rejectsAnEmptyBlock() {
            assertThat(rejection(List.of(new Block(HASH_A, 0)))).isEqualTo(StorageExceptionCase.INVALID_BLOCK);
        }

        @Test
        @DisplayName("블록 크기를 넘으면")
        void rejectsAnOversizedBlock() {
            assertThat(rejection(List.of(new Block(HASH_A, BLOCK_SIZE + 1)))).isEqualTo(StorageExceptionCase.BLOCK_TOO_LARGE);
        }

        @Test
        @DisplayName("블록이 없거나 64개를 넘으면")
        void rejectsNoneOrTooMany() {
            assertThat(rejection(List.of())).isEqualTo(StorageExceptionCase.INVALID_BLOCK);
            assertThat(rejection(Collections.nCopies(PresignBlockUploadsService.MAX_BLOCKS_PER_REQUEST + 1, new Block(HASH_A, 4))))
                    .isEqualTo(StorageExceptionCase.INVALID_BLOCK);
            then(recordUploadedBlockPort).should(never()).tryCountUpload(any(), anyInt());
        }
    }
}
