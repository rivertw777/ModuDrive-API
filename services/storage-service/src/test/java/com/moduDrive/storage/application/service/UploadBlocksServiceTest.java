package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.UploadBlocksCommand;
import com.moduDrive.storage.application.port.in.command.UploadBlocksCommand.Block;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UploadBlocksServiceTest {

    private static final int BLOCK_SIZE = 8;
    private static final int LIMIT = 3;

    @Mock private StoreBlocksPort storeBlocksPort;
    @Mock private RecordUploadedBlockPort recordUploadedBlockPort;
    private UploadBlocksService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UploadBlocksService(storeBlocksPort, recordUploadedBlockPort, BLOCK_SIZE, LIMIT);
    }

    private static Block block(String content) {
        return new Block(Blocks.sha256Hex(content.getBytes()), content.getBytes());
    }

    private StorageExceptionCase rejection(List<Block> blocks) {
        Throwable thrown = catchThrowable(() -> service.uploadBlocks(new UploadBlocksCommand(userId, blocks)));
        then(storeBlocksPort).shouldHaveNoInteractions();
        return (StorageExceptionCase) ((BusinessException) thrown).getExceptionCase();
    }

    @Nested
    @DisplayName("블록이 모두 해시와 맞을 때")
    class WhenEveryHashMatches {

        @Test
        @DisplayName("블록마다 호출자 공간의 해시 키에 저장하고 올라왔다고 기록한다")
        void storesEachUnderTheCallersKeyAndRecordsIt() {
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true);
            Block one = block("1234");
            Block two = block("abcdef");

            service.uploadBlocks(new UploadBlocksCommand(userId, List.of(one, two)));

            then(storeBlocksPort).should().storeBlock(Blocks.key(userId, one.hash()), one.data());
            then(storeBlocksPort).should().storeBlock(Blocks.key(userId, two.hash()), two.data());
            then(recordUploadedBlockPort).should().recordUploaded(userId, one.hash(), 4);
            then(recordUploadedBlockPort).should().recordUploaded(userId, two.hash(), 6);
        }

        @Test
        @DisplayName("정리 대상 등록 → S3 저장 → 업로드 표시 순서로 한다")
        void schedulesBeforeStoringAndRecordsAfter() {
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true);
            Block one = block("1234");

            service.uploadBlocks(new UploadBlocksCommand(userId, List.of(one)));

            InOrder order = inOrder(recordUploadedBlockPort, storeBlocksPort);
            order.verify(recordUploadedBlockPort).scheduleSweep(userId, one.hash());
            order.verify(storeBlocksPort).storeBlock(Blocks.key(userId, one.hash()), one.data());
            order.verify(recordUploadedBlockPort).recordUploaded(userId, one.hash(), 4);
        }

        @Test
        @DisplayName("S3 저장이 실패하면 업로드 표시를 하지 않는다")
        void doesNotRecordWhenTheStoreFails() {
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true);
            Block one = block("1234");
            willThrow(new BusinessException(StorageExceptionCase.STORAGE_UNAVAILABLE))
                    .given(storeBlocksPort).storeBlock(any(), any());

            catchThrowable(() -> service.uploadBlocks(new UploadBlocksCommand(userId, List.of(one))));

            then(recordUploadedBlockPort).should().scheduleSweep(userId, one.hash());
            then(recordUploadedBlockPort).should(never()).recordUploaded(any(), any(), anyInt());
        }

        @Test
        @DisplayName("업로드 한도에 닿으면 그 블록부터 저장하지 않는다")
        void stopsAtTheUploadLimit() {
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true, false);
            Block one = block("1234");
            Block two = block("abcdef");

            Throwable thrown = catchThrowable(() -> service.uploadBlocks(new UploadBlocksCommand(userId, List.of(one, two))));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.UPLOAD_LIMIT_EXCEEDED);
            then(storeBlocksPort).should().storeBlock(Blocks.key(userId, one.hash()), one.data());
            then(storeBlocksPort).should(never()).storeBlock(Blocks.key(userId, two.hash()), two.data());
        }
    }

    @Nested
    @DisplayName("하나라도 올바르지 않을 때")
    class WhenAnyBlockIsInvalid {

        @Test
        @DisplayName("해시가 다른 블록이 섞여 있으면 앞의 맞는 블록도 저장하지 않는다")
        void storesNothingWhenOneHashMismatches() {
            assertThat(rejection(List.of(block("1234"), new Block(Blocks.sha256Hex("other".getBytes()), "1234".getBytes()))))
                    .isEqualTo(StorageExceptionCase.INVALID_BLOCK);
            then(recordUploadedBlockPort).should(never()).tryCountUpload(any(), anyInt());
        }

        @Test
        @DisplayName("빈 블록은 받지 않는다")
        void rejectsAnEmptyBlock() {
            assertThat(rejection(List.of(new Block(Blocks.sha256Hex(new byte[0]), new byte[0]))))
                    .isEqualTo(StorageExceptionCase.INVALID_BLOCK);
        }

        @Test
        @DisplayName("블록이 없으면 받지 않는다")
        void rejectsNoBlocks() {
            assertThat(rejection(List.of())).isEqualTo(StorageExceptionCase.INVALID_BLOCK);
        }

        @Test
        @DisplayName("블록 크기를 넘으면 받지 않는다")
        void rejectsAnOversizedBlock() {
            byte[] data = new byte[BLOCK_SIZE + 1];

            assertThat(rejection(List.of(new Block(Blocks.sha256Hex(data), data))))
                    .isEqualTo(StorageExceptionCase.BLOCK_TOO_LARGE);
        }

        @Test
        @DisplayName("한 요청에 64개를 넘으면 받지 않는다")
        void rejectsTooManyBlocks() {
            assertThat(rejection(Collections.nCopies(UploadBlocksService.MAX_BLOCKS_PER_REQUEST + 1, block("1"))))
                    .isEqualTo(StorageExceptionCase.BLOCK_BATCH_TOO_LARGE);
        }
    }
}
