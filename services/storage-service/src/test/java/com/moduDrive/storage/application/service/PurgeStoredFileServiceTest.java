package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort.VersionLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PurgeStoredFileServiceTest {

    @Mock private GetFileVersionPort getFileVersionPort;
    @Mock private DeleteBlocksPort deleteBlocksPort;
    @InjectMocks private PurgeStoredFileService purgeStoredFileService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final PurgeStoredFileCommand command = new PurgeStoredFileCommand(fileId, userId);

    @Nested
    @DisplayName("파일에 버전이 여러 개 있을 때")
    class WhenFileHasMultipleVersions {

        @Test
        void deletesBlocksForEveryVersion() {
            given(getFileVersionPort.getAllVersions(fileId, userId)).willReturn(List.of(
                    new VersionLocation("path/v1", 3),
                    new VersionLocation("path/v2", 5)));

            purgeStoredFileService.purgeStoredFile(command);

            then(deleteBlocksPort).should().deleteBlocks("path/v1", 3);
            then(deleteBlocksPort).should().deleteBlocks("path/v2", 5);
        }
    }

    @Nested
    @DisplayName("버전이 없을 때")
    class WhenNoVersionsExist {

        @Test
        void doesNothing() {
            given(getFileVersionPort.getAllVersions(fileId, userId)).willReturn(List.of());

            purgeStoredFileService.purgeStoredFile(command);

            then(deleteBlocksPort).shouldHaveNoInteractions();
        }
    }
}
