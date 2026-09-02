package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FilePurgerTest {

    @Mock private SaveFilePort saveFilePort;
    @Mock private DirectoryCascader directoryCascader;
    @Mock private PurgeStorageBlocksPort purgeStorageBlocksPort;
    @InjectMocks private FilePurger filePurger;

    private final UUID ownerId = UUID.randomUUID();

    private File makeFile(FileIsDirectory isDirectory) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1"),
                new FileOwnerId(ownerId), null, null, FileStatus.DELETED, isDirectory);
    }

    @Nested
    @DisplayName("루트가 파일일 때")
    class WhenRootIsAFile {

        @Test
        void purgesItsBlocksThenDeletesTheRow() {
            File file = makeFile(new FileIsDirectory(false));

            filePurger.purgeRoot(file);

            then(purgeStorageBlocksPort).should().purgeBlocks(new FileId(file.getId()), ownerId);
            then(directoryCascader).shouldHaveNoInteractions();
            then(saveFilePort).should().deleteFile(new FileId(file.getId()));
        }
    }

    @Nested
    @DisplayName("루트가 디렉토리일 때")
    class WhenRootIsADirectory {

        @Test
        void cascadesPurgeInsteadOfPurgingItsOwnBlocks() {
            File directory = makeFile(new FileIsDirectory(true));

            filePurger.purgeRoot(directory);

            then(directoryCascader).should().purge(any(), eq(directory.fullPath()), any());
            then(purgeStorageBlocksPort).shouldHaveNoInteractions();
            then(saveFilePort).should().deleteFile(new FileId(directory.getId()));
        }
    }
}
