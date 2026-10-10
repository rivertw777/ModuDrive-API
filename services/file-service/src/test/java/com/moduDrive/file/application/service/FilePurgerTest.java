package com.moduDrive.file.application.service;

import com.moduDrive.file.fixture.FileVersionTestFixture;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class FilePurgerTest {

    @Mock private SaveFilePort saveFilePort;
    @Mock private DirectoryCascader directoryCascader;
    @Mock private ReleaseBlocksPort releaseBlocksPort;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @Mock private FindFilePort findFilePort;
    @InjectMocks private FilePurger filePurger;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    private File makeFile(FileIsDirectory isDirectory) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1"),
                new FileOwnerId(ownerId), null, null, FileStatus.TRASHED, isDirectory);
    }

    @Nested
    @DisplayName("루트가 파일일 때")
    class WhenRootIsAFile {

        @Test
        @DisplayName("버전 행을 지우기 전에 읽어 블록 참조를 풀고, 그다음 행을 tombstone으로 만든다")
        void releasesBlocksOfVersionsReadBeforeTombstoning() {
            File file = makeFile(new FileIsDirectory(false));
            FileId fileId = new FileId(file.getId());
            List<FileVersion> versions = List.of(FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 10L));
            given(findFileVersionsPort.findAllByFileId(fileId)).willReturn(versions);
            given(findFilePort.lockById(fileId)).willReturn(Optional.of(file));

            filePurger.purgeRoot(file, callerId);

            // purgeFile deletes the version rows — reading them afterwards would find nothing.
            InOrder order = inOrder(findFileVersionsPort, releaseBlocksPort, saveFilePort);
            order.verify(findFileVersionsPort).findAllByFileId(fileId);
            order.verify(releaseBlocksPort).releaseBlocks(versions);
            order.verify(saveFilePort).purgeFile(fileId, callerId);
            then(directoryCascader).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("루트가 디렉토리일 때")
    class WhenRootIsADirectory {

        @Test
        void cascadesPurgeInsteadOfPurgingItsOwnBlocks() {
            File directory = makeFile(new FileIsDirectory(true));
            given(findFilePort.lockById(new FileId(directory.getId()))).willReturn(Optional.of(directory));

            filePurger.purgeRoot(directory, callerId);

            then(directoryCascader).should().purge(any(), eq(directory.fullPath()), any(), eq(callerId));
            then(releaseBlocksPort).shouldHaveNoInteractions();
            then(saveFilePort).should().purgeFile(new FileId(directory.getId()), callerId);
        }
    }

    @Nested
    @DisplayName("찾은 뒤 다시 읽어 보니 휴지통에 없을 때")
    class WhenTheRootChangedSinceItWasFound {

        @Test
        @DisplayName("그사이 복원된 파일은 아무것도 지우지 않는다")
        void skipsARootRestoredSince() {
            File found = makeFile(new FileIsDirectory(false));
            File restored = File.withId(new FileId(found.getId()), new FileNamespaceId(found.getNamespaceId()),
                    new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId), null, null,
                    FileStatus.UPLOADED, new FileIsDirectory(false));
            given(findFilePort.lockById(new FileId(found.getId()))).willReturn(Optional.of(restored));

            filePurger.purgeRoot(found, callerId);

            then(releaseBlocksPort).shouldHaveNoInteractions();
            then(saveFilePort).shouldHaveNoInteractions();
            then(directoryCascader).shouldHaveNoInteractions();
        }
    }
}
