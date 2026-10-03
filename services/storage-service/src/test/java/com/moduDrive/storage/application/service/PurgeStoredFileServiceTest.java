package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand.StoredVersion;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PurgeStoredFileServiceTest {

    @Mock private DeleteBlocksPort deleteBlocksPort;
    @InjectMocks private PurgeStoredFileService purgeStoredFileService;

    private final UUID fileId = UUID.randomUUID();

    @Nested
    @DisplayName("파일에 버전이 여러 개 있을 때")
    class WhenFileHasMultipleVersions {

        @Test
        @DisplayName("모든 버전의 블록을 지운다")
        void deletesBlocksForEveryVersion() {
            purgeStoredFileService.purgeStoredFile(new PurgeStoredFileCommand(fileId, List.of(
                    new StoredVersion("path/v1", 3),
                    new StoredVersion("path/v2", 5))));

            then(deleteBlocksPort).should().deleteBlocks("path/v1", 3);
            then(deleteBlocksPort).should().deleteBlocks("path/v2", 5);
        }
    }

    @Nested
    @DisplayName("버전이 없을 때")
    class WhenNoVersionsExist {

        @Test
        @DisplayName("아무것도 지우지 않는다")
        void doesNothing() {
            purgeStoredFileService.purgeStoredFile(new PurgeStoredFileCommand(fileId, List.of()));

            then(deleteBlocksPort).shouldHaveNoInteractions();
        }
    }
}
