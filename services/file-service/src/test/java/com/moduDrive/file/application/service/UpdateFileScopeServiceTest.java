package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileScopeCommand;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareFileId;
import com.moduDrive.file.domain.model.FileShare.FileShareId;
import com.moduDrive.file.domain.model.FileShare.FileShareOwnerId;
import com.moduDrive.file.domain.model.FileShare.FileShareRole;
import com.moduDrive.file.domain.model.FileShare.FileShareSharedWithUserId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
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
class UpdateFileScopeServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private DeleteFileSharePort deleteFileSharePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private UpdateFileScopeService updateFileScopeService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private File makeFile() {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
    }

    private UpdateFileScopeCommand command(ShareScope scope) {
        return command(scope, Role.VIEWER);
    }

    private UpdateFileScopeCommand command(ShareScope scope, Role role) {
        return new UpdateFileScopeCommand(fileId, ownerId, scope, role);
    }

    @Nested
    @DisplayName("RESTRICTED 파일을 LINK로 전환할 때")
    class WhenSwitchingToLink {

        @Test
        void issuesLinkToken() {
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(makeFile()));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileScopeService.updateFileScope(command(ShareScope.LINK));

            assertThat(result.getAccessScope()).isEqualTo(ShareScope.LINK);
            assertThat(result.getLinkToken()).isNotNull();
            assertThat(result.getLinkRole()).isEqualTo(Role.VIEWER);
        }

        @Test
        void upgradesTheLinkRoleWithoutRotatingTheToken() {
            File alreadyLinked = makeFile();
            UUID existingToken = UUID.randomUUID();
            alreadyLinked.enableLinkSharing(existingToken, Role.VIEWER);
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(alreadyLinked));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileScopeService.updateFileScope(command(ShareScope.LINK, Role.EDITOR));

            assertThat(result.getLinkRole()).isEqualTo(Role.EDITOR);
            assertThat(result.getLinkToken()).isEqualTo(existingToken);
        }

        @Test
        void keepsExistingTokenWhenLinkIsReSelected() {
            File alreadyLinked = makeFile();
            UUID existingToken = UUID.randomUUID();
            alreadyLinked.enableLinkSharing(existingToken, Role.VIEWER);
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(alreadyLinked));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileScopeService.updateFileScope(command(ShareScope.LINK));

            assertThat(result.getLinkToken()).isEqualTo(existingToken);
        }
    }

    @Nested
    @DisplayName("LINK 파일을 RESTRICTED로 되돌릴 때")
    class WhenSwitchingToRestricted {

        @Test
        void clearsLinkToken() {
            File linked = makeFile();
            linked.enableLinkSharing(UUID.randomUUID(), Role.VIEWER);
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(linked));
            given(findFileSharePort.findByFileId(new FileId(fileId))).willReturn(List.of());
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            assertThat(result.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
            assertThat(result.getLinkToken()).isNull();
            assertThat(result.getLinkRole()).isNull();
        }

        @Test
        void revokesPendingGuestSharesButKeepsMemberAndClaimedShares() {
            File linked = makeFile();
            linked.enableLinkSharing(UUID.randomUUID(), Role.VIEWER);
            UUID pendingShareId = UUID.randomUUID();
            UUID memberShareId = UUID.randomUUID();
            UUID claimedShareId = UUID.randomUUID();
            FileShare pendingGuestShare = FileShare.withId(new FileShareId(pendingShareId),
                    new FileShareFileId(fileId), new FileShareOwnerId(ownerId), null,
                    new FileShareRole(Role.VIEWER), UUID.randomUUID(), "guest@example.com");
            FileShare memberShare = FileShare.withId(new FileShareId(memberShareId),
                    new FileShareFileId(fileId), new FileShareOwnerId(ownerId),
                    new FileShareSharedWithUserId(UUID.randomUUID()), new FileShareRole(Role.VIEWER));
            // A guest share that has since been claimed (see FileShare#claim): token cleared,
            // sharedWithUserId filled — must be treated exactly like memberShare, not deleted.
            FileShare claimedShare = FileShare.withId(new FileShareId(claimedShareId),
                    new FileShareFileId(fileId), new FileShareOwnerId(ownerId),
                    new FileShareSharedWithUserId(UUID.randomUUID()), new FileShareRole(Role.VIEWER),
                    null, null);
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(linked));
            given(findFileSharePort.findByFileId(new FileId(fileId)))
                    .willReturn(List.of(pendingGuestShare, memberShare, claimedShare));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(pendingShareId));
            then(deleteFileSharePort).shouldHaveNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("LINK 전환 요청의 역할이 유효하지 않을 때")
    class WhenLinkRoleIsInvalid {

        @Test
        void throwsInvalidLinkRoleWhenRoleIsMissing() {
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(makeFile()));

            Throwable thrown = catchThrowable(
                    () -> updateFileScopeService.updateFileScope(command(ShareScope.LINK, null)));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.INVALID_LINK_ROLE);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자가 파일 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(makeFile()));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(any(File.class), eq(ownerId));

            Throwable thrown = catchThrowable(() -> updateFileScopeService.updateFileScope(command(ShareScope.LINK)));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> updateFileScopeService.updateFileScope(command(ShareScope.LINK)));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }
}
