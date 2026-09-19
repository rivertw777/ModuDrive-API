package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.application.port.out.PublishMailEventPort;
import com.moduDrive.file.application.port.out.PublishNotificationEventPort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class ShareFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private SaveFileSharePort saveFileSharePort;
    @Mock private FindMemberByEmailPort findMemberByEmailPort;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private FindMemberByIdPort findMemberByIdPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @Mock private PublishMailEventPort publishMailEventPort;
    @Mock private PublishNotificationEventPort publishNotificationEventPort;
    @InjectMocks private ShareFileService shareFileService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID granteeId = UUID.randomUUID();
    private static final String EMAIL = "river@modudrive.com";
    private static final String OWNER_NAME = "홍길동";
    private static final String OWNER_EMAIL = "owner@modudrive.com";
    private static final String MESSAGE = "확인 부탁드려요";
    private final ShareFileCommand command = new ShareFileCommand(fileId, ownerId, EMAIL, Role.VIEWER, MESSAGE);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("소유자가 아직 공유하지 않은 이메일로 초대할 때")
    class WhenShareIsNew {

        @Test
        void savesFileShareAndPublishesMailAndNotification() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.of(granteeId));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(command.getFileId(), granteeId))
                    .willReturn(false);
            given(saveFileSharePort.saveFileShare(any(FileShare.class))).willAnswer(inv -> inv.getArgument(0));
            given(findMemberByIdPort.findMemberById(ownerId)).willReturn(new MemberSummary(OWNER_NAME, OWNER_EMAIL));

            Optional<FileShare> result = shareFileService.shareFile(command);

            assertThat(result).isPresent();
            assertThat(result.get().getSharedWithUserId()).isEqualTo(granteeId);
            assertThat(result.get().getRole()).isEqualTo(Role.VIEWER);
            then(publishMailEventPort).should().publishShareInviteRequested(
                    fileId, EMAIL, "report.pdf", false, "DOCUMENT", "VIEWER", OWNER_NAME, OWNER_EMAIL, MESSAGE, null);
            then(publishNotificationEventPort).should().publishFileShared(
                    fileId, granteeId, "report.pdf", "VIEWER", false, OWNER_NAME, OWNER_EMAIL);
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

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(findMemberByEmailPort).shouldHaveNoInteractions();
            then(saveFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("초대 대상 이메일의 회원이 없을 때")
    class WhenGranteeIsNotAMember {

        @Test
        void createsAPendingShareWithItsOwnTokenInsteadOfLinkSharingTheFile() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.empty());
            given(findFileSharePort.existsByFileIdAndGranteeEmail(command.getFileId(), EMAIL)).willReturn(false);
            given(saveFileSharePort.saveFileShare(any(FileShare.class))).willAnswer(inv -> inv.getArgument(0));
            given(findMemberByIdPort.findMemberById(ownerId)).willReturn(new MemberSummary(OWNER_NAME, OWNER_EMAIL));

            Optional<FileShare> result = shareFileService.shareFile(command);

            assertThat(result).isEmpty();
            // Unlike the old fallback, a guest invite must never flip the file itself to LINK.
            assertThat(file.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);

            ArgumentCaptor<FileShare> captor = ArgumentCaptor.forClass(FileShare.class);
            then(saveFileSharePort).should().saveFileShare(captor.capture());
            FileShare pending = captor.getValue();
            assertThat(pending.getSharedWithUserId()).isNull();
            assertThat(pending.getGranteeEmail()).isEqualTo(EMAIL);
            assertThat(pending.getToken()).isNotNull();
            // A guest has no account to notify in-app: only the mail, carrying its no-login token.
            then(publishMailEventPort).should().publishShareInviteRequested(
                    fileId, EMAIL, "report.pdf", false, "DOCUMENT", "VIEWER", OWNER_NAME, OWNER_EMAIL, MESSAGE, pending.getToken());
            then(publishNotificationEventPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("이미 같은 이메일로 게스트를 초대했을 때")
    class WhenGuestAlreadyInvited {

        @Test
        void throwsFileShareAlreadyExists() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.empty());
            given(findFileSharePort.existsByFileIdAndGranteeEmail(command.getFileId(), EMAIL)).willReturn(true);

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
            then(saveFileSharePort).shouldHaveNoInteractions();
            then(publishMailEventPort).shouldHaveNoInteractions();
            then(publishNotificationEventPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("자기 자신에게 공유하려 할 때")
    class WhenSharingToSelf {

        @Test
        void throwsFileShareSelfNotAllowed() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.of(ownerId));

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_SELF_NOT_ALLOWED);
            then(findFileSharePort).shouldHaveNoInteractions();
            then(saveFileSharePort).shouldHaveNoInteractions();
            then(publishMailEventPort).shouldHaveNoInteractions();
            then(publishNotificationEventPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("이미 공유된 파일인 경우")
    class WhenAlreadyShared {

        @Test
        void throwsFileShareAlreadyExists() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.of(granteeId));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(command.getFileId(), granteeId))
                    .willReturn(true);

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
            then(saveFileSharePort).shouldHaveNoInteractions();
            then(publishMailEventPort).shouldHaveNoInteractions();
            then(publishNotificationEventPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }
}
