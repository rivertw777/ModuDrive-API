package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ListSharedWithMeServiceTest {

    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FindFilePort findFilePort;
    @Mock private FindMemberByIdPort findMemberByIdPort;
    @Mock private FileFavoritePort fileFavoritePort;
    @InjectMocks private ListSharedWithMeService listSharedWithMeService;

    @BeforeEach
    void noFavoritesByDefault() {
        lenient().when(fileFavoritePort.favoriteFileIds(any())).thenReturn(Set.of());
    }

    private final UUID sharedWithUserId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private static final LocalDateTime SHARED_AT = LocalDateTime.of(2026, 9, 1, 9, 0);
    private final ListSharedWithMeCommand command = new ListSharedWithMeCommand(sharedWithUserId);

    private FileShare makeShare(UUID fileId, Role role) {
        return FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(fileId),
                new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(sharedWithUserId),
                new FileShareRole(role), SHARED_AT);
    }

    private File makeFile(UUID fileId, FileStatus status) {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(ownerId), null, null, status, new FileIsDirectory(false));
    }

    @Nested
    @DisplayName("공유받은 파일이 있을 때")
    class WhenSharesExist {

        @Test
        void returnsSharedFilesWithSharerAndRole() {
            UUID fileId = UUID.randomUUID();
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId))
                    .willReturn(List.of(makeShare(fileId, Role.EDITOR)));
            given(findFilePort.findById(new FileId(fileId)))
                    .willReturn(Optional.of(makeFile(fileId, FileStatus.UPLOADED)));
            given(findMemberByIdPort.findMemberById(ownerId))
                    .willReturn(new MemberSummary("홍길동", "owner@modudrive.com"));

            List<FileView> result = listSharedWithMeService.listSharedWithMe(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).file().getId()).isEqualTo(fileId);
            assertThat(result.get(0).callerRole()).isEqualTo(Role.EDITOR);
            assertThat(result.get(0).sharedByName()).isEqualTo("홍길동");
            assertThat(result.get(0).sharedByEmail()).isEqualTo("owner@modudrive.com");
            assertThat(result.get(0).sharedAt()).isEqualTo(SHARED_AT);
        }

        @Test
        @DisplayName("공유한 사람 조회가 실패하면 unknown으로 표시하고 목록은 유지한다")
        void degradesSharerToUnknownWhenLookupFails() {
            UUID fileId = UUID.randomUUID();
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId))
                    .willReturn(List.of(makeShare(fileId, Role.VIEWER)));
            given(findFilePort.findById(new FileId(fileId)))
                    .willReturn(Optional.of(makeFile(fileId, FileStatus.UPLOADED)));
            willThrow(new BusinessException(FileExceptionCase.SHARE_TARGET_NOT_FOUND))
                    .given(findMemberByIdPort).findMemberById(ownerId);

            List<FileView> result = listSharedWithMeService.listSharedWithMe(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).sharedByName()).isNull();
            assertThat(result.get(0).sharedByEmail()).isNull();
        }

        @Test
        void excludesDeletedFiles() {
            UUID fileId = UUID.randomUUID();
            lenient().when(findMemberByIdPort.findMemberById(any()))
                    .thenReturn(new MemberSummary("홍길동", "owner@modudrive.com"));
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId))
                    .willReturn(List.of(makeShare(fileId, Role.VIEWER)));
            given(findFilePort.findById(new FileId(fileId)))
                    .willReturn(Optional.of(makeFile(fileId, FileStatus.DELETED)));

            assertThat(listSharedWithMeService.listSharedWithMe(command)).isEmpty();
        }

        @Test
        void skipsSharesWhoseFileNoLongerExists() {
            UUID fileId = UUID.randomUUID();
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId))
                    .willReturn(List.of(makeShare(fileId, Role.VIEWER)));
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.empty());

            assertThat(listSharedWithMeService.listSharedWithMe(command)).isEmpty();
        }
    }

    @Nested
    @DisplayName("공유받은 파일이 없을 때")
    class WhenNoSharesExist {

        @Test
        void returnsEmptyList() {
            given(findFileSharePort.findBySharedWithUserId(sharedWithUserId)).willReturn(List.of());

            assertThat(listSharedWithMeService.listSharedWithMe(command)).isEmpty();
        }
    }
}
