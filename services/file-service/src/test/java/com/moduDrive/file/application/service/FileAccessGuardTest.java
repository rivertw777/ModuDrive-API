package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFileSharePort;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FileAccessGuardTest {

    @Mock private FindFileSharePort findFileSharePort;
    @InjectMocks private FileAccessGuard fileAccessGuard;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    private void givenShare(UUID userId, Role role) {
        given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), userId))
                .willReturn(Optional.of(FileShare.withId(
                        new FileShareId(UUID.randomUUID()), new FileShareFileId(fileId),
                        new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(userId),
                        new FileShareRole(role))));
    }

    private void assertDenied(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
    }

    @Nested
    @DisplayName("호출자가 파일 소유자일 때")
    class WhenCallerIsOwner {

        @Test
        void passesOwnerCheckWithoutHittingShareTable() {
            assertThatCode(() -> fileAccessGuard.requireOwner(file, ownerId)).doesNotThrowAnyException();
        }

        @Test
        void passesEveryRoleCheck() {
            assertThatCode(() -> fileAccessGuard.requireRole(file, ownerId, Role.EDITOR)).doesNotThrowAnyException();
            assertThatCode(() -> fileAccessGuard.requireRole(file, ownerId, Role.VIEWER)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("호출자가 EDITOR로 공유받았을 때")
    class WhenCallerIsEditor {

        @Test
        void passesEditorAndViewerChecks() {
            UUID editorId = UUID.randomUUID();
            givenShare(editorId, Role.EDITOR);

            assertThatCode(() -> fileAccessGuard.requireRole(file, editorId, Role.EDITOR)).doesNotThrowAnyException();
        }

        @Test
        void isStillDeniedOwnerOnlyActions() {
            UUID editorId = UUID.randomUUID();

            assertDenied(catchThrowable(() -> fileAccessGuard.requireOwner(file, editorId)));
        }
    }

    @Nested
    @DisplayName("호출자가 VIEWER로 공유받았을 때")
    class WhenCallerIsViewer {

        @Test
        void passesViewerCheck() {
            UUID viewerId = UUID.randomUUID();
            givenShare(viewerId, Role.VIEWER);

            assertThatCode(() -> fileAccessGuard.requireRole(file, viewerId, Role.VIEWER)).doesNotThrowAnyException();
        }

        @Test
        void isDeniedEditorCheck() {
            UUID viewerId = UUID.randomUUID();
            givenShare(viewerId, Role.VIEWER);

            assertDenied(catchThrowable(() -> fileAccessGuard.requireRole(file, viewerId, Role.EDITOR)));
        }
    }

    @Nested
    @DisplayName("호출자가 공유받지 않은 제3자일 때")
    class WhenCallerIsStranger {

        @Test
        void isDeniedRoleCheck() {
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), strangerId))
                    .willReturn(Optional.empty());

            assertDenied(catchThrowable(() -> fileAccessGuard.requireRole(file, strangerId, Role.VIEWER)));
        }

        @Test
        void isDeniedOwnerCheck() {
            assertDenied(catchThrowable(() -> fileAccessGuard.requireOwner(file, strangerId)));
        }
    }

    @Nested
    @DisplayName("호출자 식별자가 없을 때")
    class WhenCallerIdIsNull {

        @Test
        void isDeniedOwnerCheck() {
            assertDenied(catchThrowable(() -> fileAccessGuard.requireOwner(file, null)));
        }
    }
}
