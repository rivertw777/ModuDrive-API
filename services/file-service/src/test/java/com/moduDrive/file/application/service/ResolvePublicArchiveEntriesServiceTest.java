package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ResolvePublicArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ResolvePublicArchiveEntriesServiceTest {

    @Mock private PublicFileResolver publicFileResolver;
    @Mock private ArchiveEntryCollector archiveEntryCollector;
    @InjectMocks private ResolvePublicArchiveEntriesService resolvePublicArchiveEntriesService;

    private final File folder = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
            new FileName("shared"), new FilePath("/"), new FileOwnerId(UUID.randomUUID()),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(true));

    @Nested
    @DisplayName("링크로 열리는 항목이면")
    class WhenReachable {

        @Test
        void collectsIt() {
            String id = folder.getId().toString();
            List<ArchiveEntry> laidOut = List.of(new ArchiveEntry("shared/", null));
            given(publicFileResolver.resolve(id, "k")).willReturn(folder);
            given(archiveEntryCollector.collect(List.of(folder))).willReturn(laidOut);

            List<ArchiveEntry> result = resolvePublicArchiveEntriesService.resolvePublicArchiveEntries(
                    new ResolvePublicArchiveEntriesCommand(List.of(id), "k"));

            assertThat(result).isEqualTo(laidOut);
        }
    }

    @Nested
    @DisplayName("링크로 열리지 않는 항목이 섞이면")
    class WhenUnreachable {

        @Test
        void failsWithNotFound() {
            given(publicFileResolver.resolve("nope", null))
                    .willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND));

            Throwable thrown = catchThrowable(() -> resolvePublicArchiveEntriesService.resolvePublicArchiveEntries(
                    new ResolvePublicArchiveEntriesCommand(List.of("nope"), null)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(archiveEntryCollector).shouldHaveNoInteractions();
        }
    }
}
