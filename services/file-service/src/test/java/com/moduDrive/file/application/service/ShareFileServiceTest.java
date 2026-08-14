package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.event.FileShareInvitedEvent;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
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
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock private FileAccessGuard fileAccessGuard;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private ShareFileService shareFileService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID granteeId = UUID.randomUUID();
    private static final String EMAIL = "river@modudrive.com";
    private final ShareFileCommand command = new ShareFileCommand(fileId, ownerId, EMAIL, Role.VIEWER);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("소유자가 아직 공유하지 않은 이메일로 초대할 때")
    class WhenShareIsNew {

        @Test
        void savesFileShareAndPublishesInvitedEvent() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(granteeId);
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(command.getFileId(), granteeId))
                    .willReturn(false);
            given(saveFileSharePort.saveFileShare(any(FileShare.class))).willAnswer(inv -> inv.getArgument(0));

            FileShare result = shareFileService.shareFile(command);

            assertThat(result.getSharedWithUserId()).isEqualTo(granteeId);
            assertThat(result.getRole()).isEqualTo(Role.VIEWER);
            then(eventPublisher).should().publishEvent(
                    new FileShareInvitedEvent(fileId, ownerId, granteeId, EMAIL, "report.pdf", Role.VIEWER));
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
    class WhenShareTargetNotFound {

        @Test
        void propagatesShareTargetNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.SHARE_TARGET_NOT_FOUND))
                    .given(findMemberByEmailPort).findMemberIdByEmail(EMAIL);

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.SHARE_TARGET_NOT_FOUND);
            then(saveFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("이미 공유된 파일인 경우")
    class WhenAlreadyShared {

        @Test
        void throwsFileShareAlreadyExists() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(granteeId);
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(command.getFileId(), granteeId))
                    .willReturn(true);

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
            then(saveFileSharePort).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
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
