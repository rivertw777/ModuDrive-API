package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.UploadBlockCommand;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UploadBlockServiceTest {

    private static final int BLOCK_SIZE = 8;
    private static final int LIMIT = 3;

    @Mock private StoreBlocksPort storeBlocksPort;
    @Mock private RecordUploadedBlockPort recordUploadedBlockPort;
    private UploadBlockService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UploadBlockService(storeBlocksPort, recordUploadedBlockPort, BLOCK_SIZE, LIMIT);
    }

    private StorageExceptionCase rejection(byte[] data, String hash) {
        Throwable thrown = catchThrowable(() -> service.uploadBlock(new UploadBlockCommand(userId, hash, data)));
        then(storeBlocksPort).shouldHaveNoInteractions();
        then(recordUploadedBlockPort).shouldHaveNoInteractions();
        return (StorageExceptionCase) ((BusinessException) thrown).getExceptionCase();
    }

    @Nested
    @DisplayName("바이트가 해시와 맞을 때")
    class WhenTheHashMatches {

        @Test
        @DisplayName("호출자 공간의 해시 키에 저장하고 올라왔다고 기록한다")
        void storesUnderTheCallersKeyAndRecordsIt() {
            byte[] data = "1234".getBytes();
            String hash = Blocks.sha256Hex(data);
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(true);

            service.uploadBlock(new UploadBlockCommand(userId, hash, data));

            then(storeBlocksPort).should().storeBlock(Blocks.key(userId, hash), data);
            then(recordUploadedBlockPort).should().recordUploaded(userId, hash, 4);
        }

        @Test
        @DisplayName("업로드 한도를 넘으면 저장하지 않는다")
        void rejectsPastTheUploadLimit() {
            byte[] data = "1234".getBytes();
            String hash = Blocks.sha256Hex(data);
            given(recordUploadedBlockPort.tryCountUpload(userId, LIMIT)).willReturn(false);

            Throwable thrown = catchThrowable(() -> service.uploadBlock(new UploadBlockCommand(userId, hash, data)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.UPLOAD_LIMIT_EXCEEDED);
            then(storeBlocksPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("블록이 올바르지 않을 때")
    class WhenTheBlockIsInvalid {

        @Test
        @DisplayName("바이트가 해시와 다르면 저장하지 않는다")
        void rejectsAHashMismatch() {
            assertThat(rejection("1234".getBytes(), Blocks.sha256Hex("other".getBytes())))
                    .isEqualTo(StorageExceptionCase.INVALID_BLOCK);
        }

        @Test
        @DisplayName("빈 블록은 받지 않는다")
        void rejectsAnEmptyBlock() {
            assertThat(rejection(new byte[0], Blocks.sha256Hex(new byte[0])))
                    .isEqualTo(StorageExceptionCase.INVALID_BLOCK);
        }

        @Test
        @DisplayName("블록 크기를 넘으면 받지 않는다")
        void rejectsAnOversizedBlock() {
            byte[] data = new byte[BLOCK_SIZE + 1];

            assertThat(rejection(data, Blocks.sha256Hex(data))).isEqualTo(StorageExceptionCase.BLOCK_TOO_LARGE);
        }
    }
}
