package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Permission;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ListSharedWithMeServiceTest {

    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FindFilePort findFilePort;
    @InjectMocks private ListSharedWithMeService listSharedWithMeService;

    private final UUID sharedWithUserId = UUID.randomUUID();
    private final ListSharedWithMeCommand command = new ListSharedWithMeCommand(sharedWithUserId);

    private FileShare makeShare(UUID fileId) {
        return FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(fileId),
                new FileShareOwnerId(UUID.randomUUID()), new FileShareSharedWithUserId(sharedWithUserId),
                new FileSharePermission(Permission.READ));
    }

    private File makeFile(UUID fileId, FileStatus status) {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(UUID.randomUUID()), null, null, status, new FileIsDirectory(false));
    }

    @Nested
    @DisplayName("공유받은 파일이 있을 때")
    class WhenSharesExist {

        @Test
        void returnsSharedFiles() {
            UUID fileId = UUID.randomUUID();
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId)).willReturn(List.of(makeShare(fileId)));
            given(findFilePort.findById(new FileId(fileId)))
                    .willReturn(Optional.of(makeFile(fileId, FileStatus.UPLOADED)));

            List<File> result = listSharedWithMeService.listSharedWithMe(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(fileId);
        }

        @Test
        void excludesDeletedFiles() {
            UUID fileId = UUID.randomUUID();
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId)).willReturn(List.of(makeShare(fileId)));
            given(findFilePort.findById(new FileId(fileId)))
                    .willReturn(Optional.of(makeFile(fileId, FileStatus.DELETED)));

            List<File> result = listSharedWithMeService.listSharedWithMe(command);

            assertThat(result).isEmpty();
        }

        @Test
        void skipsSharesWhoseFileNoLongerExists() {
            UUID fileId = UUID.randomUUID();
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId)).willReturn(List.of(makeShare(fileId)));
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.empty());

            List<File> result = listSharedWithMeService.listSharedWithMe(command);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("공유받은 파일이 없을 때")
    class WhenNoSharesExist {

        @Test
        void returnsEmptyList() {
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId)).willReturn(List.of());

            List<File> result = listSharedWithMeService.listSharedWithMe(command);

            assertThat(result).isEmpty();
        }
    }
}
