package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ArchiveEntryCollectorTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @InjectMocks private ArchiveEntryCollector archiveEntryCollector;

    private static final UUID NAMESPACE = UUID.randomUUID();

    private static File file(String path, String name, UUID versionId, FileStatus status) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(NAMESPACE), new FileName(name),
                new FilePath(path), new FileOwnerId(UUID.randomUUID()),
                versionId == null ? null : new FileCurrentVersionId(versionId), null, status, new FileIsDirectory(false));
    }

    private static File dir(String path, String name, FileStatus status) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(NAMESPACE), new FileName(name),
                new FilePath(path), new FileOwnerId(UUID.randomUUID()), null, null, status, new FileIsDirectory(true));
    }

    private static FileVersion version(UUID id, File file) {
        return FileVersion.withId(new FileVersionId(id), new FileVersionFileId(file.getId()),
                new FileVersionFileSize(10L), new FileVersionBlockCount(1), new FileVersionS3Path("s3/" + file.getName()));
    }

    @Nested
    @DisplayName("폴더를 고르면")
    class WhenRootIsDirectory {

        @Test
        void expandsItsLiveSubtreeUnderTheFolderName() {
            File photos = dir("/", "photos", FileStatus.UPLOADED);
            UUID aV = UUID.randomUUID();
            File a = file("/photos", "a.jpg", aV, FileStatus.UPLOADED);
            File emptySub = dir("/photos", "empty", FileStatus.UPLOADED);
            UUID bV = UUID.randomUUID();
            File b = file("/photos/2024", "b.jpg", bV, FileStatus.UPLOADED);
            File trashed = file("/photos", "gone.jpg", UUID.randomUUID(), FileStatus.TRASHED);
            File neverUploaded = file("/photos", "pending.jpg", null, FileStatus.PENDING);
            given(findFilePort.findByNamespaceIdAndPathStartingWith(new NamespaceId(NAMESPACE), "/photos"))
                    .willReturn(List.of(a, emptySub, b, trashed, neverUploaded));
            given(findFileVersionsPort.findAllByIds(any())).willReturn(List.of(version(aV, a), version(bV, b)));

            List<ArchiveEntry> entries = archiveEntryCollector.collect(List.of(photos));

            assertThat(entries).extracting(ArchiveEntry::path)
                    .containsExactly("photos/", "photos/a.jpg", "photos/empty/", "photos/2024/b.jpg");
            assertThat(entries.get(1).version().getS3Path()).isEqualTo("s3/a.jpg");
        }
    }

    @Nested
    @DisplayName("고른 항목끼리 이름이 겹치면")
    class WhenRootNamesCollide {

        @Test
        void numbersTheLaterOnesBeforeTheExtension() {
            UUID v1 = UUID.randomUUID();
            UUID v2 = UUID.randomUUID();
            File first = file("/a", "report.pdf", v1, FileStatus.UPLOADED);
            File second = file("/b", "report.pdf", v2, FileStatus.UPLOADED);
            File folder = dir("/c", "docs", FileStatus.UPLOADED);
            File otherFolder = dir("/d", "docs", FileStatus.UPLOADED);
            given(findFileVersionsPort.findAllByIds(any())).willReturn(List.of(version(v1, first), version(v2, second)));

            List<ArchiveEntry> entries = archiveEntryCollector.collect(List.of(first, second, folder, otherFolder));

            assertThat(entries).extracting(ArchiveEntry::path)
                    .containsExactly("report.pdf", "report (1).pdf", "docs/", "docs (1)/");
        }
    }

    @Nested
    @DisplayName("폴더와 그 안의 항목을 함께 고르면")
    class WhenPickIsNested {

        @Test
        void keepsOnlyTheFolderSoNothingIsZippedTwice() {
            File photos = dir("/", "photos", FileStatus.UPLOADED);
            UUID aV = UUID.randomUUID();
            File a = file("/photos/2024", "a.jpg", aV, FileStatus.UPLOADED);
            File sub = dir("/photos", "2024", FileStatus.UPLOADED);
            given(findFilePort.findByNamespaceIdAndPathStartingWith(new NamespaceId(NAMESPACE), "/photos"))
                    .willReturn(List.of(sub, a));
            given(findFileVersionsPort.findAllByIds(any())).willReturn(List.of(version(aV, a)));

            List<ArchiveEntry> entries = archiveEntryCollector.collect(List.of(a, sub, photos));

            assertThat(entries).extracting(ArchiveEntry::path)
                    .containsExactly("photos/", "photos/2024/", "photos/2024/a.jpg");
            then(findFilePort).shouldHaveNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("펼친 파일 수가 상한을 넘으면")
    class WhenTooManyFiles {

        @Test
        void stopsBeforeLoadingVersions() {
            File big = dir("/", "big", FileStatus.UPLOADED);
            List<File> many = IntStream.rangeClosed(0, ArchiveEntryCollector.MAX_FILES)
                    .mapToObj(i -> file("/big", i + ".txt", UUID.randomUUID(), FileStatus.UPLOADED))
                    .toList();
            given(findFilePort.findByNamespaceIdAndPathStartingWith(new NamespaceId(NAMESPACE), "/big")).willReturn(many);

            Throwable thrown = catchThrowable(() -> archiveEntryCollector.collect(List.of(big)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.ARCHIVE_TOO_LARGE);
            then(findFileVersionsPort).shouldHaveNoInteractions();
        }
    }
}
