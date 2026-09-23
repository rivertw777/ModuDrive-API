package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ResolveArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ResolveArchiveEntriesServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @Mock private ArchiveEntryCollector archiveEntryCollector;
    @InjectMocks private ResolveArchiveEntriesService resolveArchiveEntriesService;

    private final UUID callerId = UUID.randomUUID();
    private final File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
            new FileName("a.txt"), new FilePath("/"), new FileOwnerId(UUID.randomUUID()),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("고른 항목을 모두 받을 수 있을 때")
    class WhenAllDownloadable {

        @Test
        void collectsEachPickedItemOnce() {
            List<ArchiveEntry> laidOut = List.of(new ArchiveEntry("a.txt", null));
            given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.of(file));
            given(archiveEntryCollector.collect(List.of(file))).willReturn(laidOut);

            List<ArchiveEntry> result = resolveArchiveEntriesService.resolveArchiveEntries(
                    new ResolveArchiveEntriesCommand(List.of(file.getId(), file.getId()), callerId));

            assertThat(result).isEqualTo(laidOut);
            then(fileAccessGuard).should().requirePermission(file, callerId, Permission.DOWNLOAD);
        }
    }

    @Nested
    @DisplayName("고른 항목 중 하나라도 권한이 없을 때")
    class WhenOneIsDenied {

        @Test
        void failsTheWholeZip() {
            given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requirePermission(file, callerId, Permission.DOWNLOAD);

            Throwable thrown = catchThrowable(() -> resolveArchiveEntriesService.resolveArchiveEntries(
                    new ResolveArchiveEntriesCommand(List.of(file.getId()), callerId)));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            then(archiveEntryCollector).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("고른 항목이 없는 파일일 때")
    class WhenMissing {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> resolveArchiveEntriesService.resolveArchiveEntries(
                    new ResolveArchiveEntriesCommand(List.of(file.getId()), callerId)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }
}
