package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ListFileSharesServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FindMemberByIdPort findMemberByIdPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private ListFileSharesService listFileSharesService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final ListFileSharesCommand command = new ListFileSharesCommand(fileId, ownerId);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("소유자가 공유 목록을 조회할 때")
    class WhenOwnerLists {

        @Test
        void returnsFileWithItsSharesAndMemberSummaries() {
            UUID sharedWithUserId = UUID.randomUUID();
            FileShare share = FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(fileId),
                    new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(sharedWithUserId),
                    new FileShareRole(Role.EDITOR));
            MemberSummary summary = new MemberSummary("river", "river@modudrive.com");
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of(share));
            given(findMemberByIdPort.findMemberById(sharedWithUserId)).willReturn(summary);

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.file().getOwnerId()).isEqualTo(ownerId);
            assertThat(result.shares()).containsExactly(share);
            assertThat(result.memberSummaries()).containsEntry(sharedWithUserId, summary);
        }

        @Test
        void returnsEmptyShareListWhenNobodyWasInvited() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of());

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.shares()).isEmpty();
            assertThat(result.memberSummaries()).isEmpty();
        }

        @Test
        void degradesToUnknownWhenMemberLookupFails() {
            UUID sharedWithUserId = UUID.randomUUID();
            FileShare share = FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(fileId),
                    new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(sharedWithUserId),
                    new FileShareRole(Role.VIEWER));
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of(share));
            willThrow(new BusinessException(FileExceptionCase.SHARE_TARGET_NOT_FOUND))
                    .given(findMemberByIdPort).findMemberById(sharedWithUserId);

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.shares()).containsExactly(share);
            assertThat(result.memberSummaries().get(sharedWithUserId))
                    .isEqualTo(new MemberSummary(null, null));
        }
    }

    @Nested
    @DisplayName("호출자가 파일 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(any(File.class), eq(ownerId));

            Throwable thrown = catchThrowable(() -> listFileSharesService.listFileShares(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(findFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> listFileSharesService.listFileShares(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }
}
