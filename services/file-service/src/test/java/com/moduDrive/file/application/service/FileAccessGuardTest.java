package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Permission;
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
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FileAccessGuardTest {

    @Mock private FindFileSharePort findFileSharePort;
    @InjectMocks private FileAccessGuard fileAccessGuard;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    private File makeFile() {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
    }

    private final File file = makeFile();

    private void givenShare(UUID userId, Role role) {
        given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), userId))
                .willReturn(Optional.of(FileShare.withId(
                        new FileShareId(UUID.randomUUID()), new FileShareFileId(fileId),
                        new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(userId),
                        new FileShareRole(role))));
    }

    private void givenNoShare(UUID userId) {
        given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), userId))
                .willReturn(Optional.empty());
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
            then(findFileSharePort).shouldHaveNoInteractions();
        }

        @Test
        void isGrantedEveryPermissionWithoutHittingTheShareTable() {
            assertThatCode(() -> fileAccessGuard.requirePermission(file, ownerId, Permission.RENAME))
                    .doesNotThrowAnyException();
            then(findFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자가 EDITOR로 공유받았을 때")
    class WhenCallerIsEditor {

        private final UUID editorId = UUID.randomUUID();

        @Test
        void isAllowedRename() {
            givenShare(editorId, Role.EDITOR);

            assertThatCode(() -> fileAccessGuard.requirePermission(file, editorId, Permission.RENAME))
                    .doesNotThrowAnyException();
        }

        @Test
        void isStillDeniedOwnerOnlyActions() {
            assertDenied(catchThrowable(() -> fileAccessGuard.requireOwner(file, editorId)));
        }
    }

    @Nested
    @DisplayName("호출자가 VIEWER로 공유받았을 때")
    class WhenCallerIsViewer {

        private final UUID viewerId = UUID.randomUUID();

        @Test
        void isAllowedDownload() {
            givenShare(viewerId, Role.VIEWER);

            assertThatCode(() -> fileAccessGuard.requirePermission(file, viewerId, Permission.DOWNLOAD))
                    .doesNotThrowAnyException();
        }

        @Test
        void isDeniedRename() {
            givenShare(viewerId, Role.VIEWER);

            assertDenied(catchThrowable(() -> fileAccessGuard.requirePermission(file, viewerId, Permission.RENAME)));
        }
    }

    @Nested
    @DisplayName("공유받지 않은 로그인 사용자가 LINK 파일에 접근할 때")
    class WhenCallerIsLinkVisitor {

        /** These are the authenticated, fileId-only routes — they never see the link token, so
         * "signed in" must not be treated as "holds the link". A signed-in non-sharee gets nothing
         * here regardless of the file's LINK role; anonymous/token holders go through the public
         * routes, which do check the token. */
        @Test
        void isDeniedRegardlessOfTheLinkRole() {
            File linked = makeFile();
            linked.enableLinkSharing(UUID.randomUUID(), Role.EDITOR);
            givenNoShare(strangerId);

            assertDenied(catchThrowable(() -> fileAccessGuard.requirePermission(linked, strangerId, Permission.READ)));
        }

        @Test
        void anExplicitShareStillGrantsAccessOnALinkFile() {
            File linked = makeFile();
            linked.enableLinkSharing(UUID.randomUUID(), Role.EDITOR);
            givenShare(strangerId, Role.VIEWER);

            assertThatCode(() -> fileAccessGuard.requirePermission(linked, strangerId, Permission.DOWNLOAD))
                    .doesNotThrowAnyException();
        }

        @Test
        void isDeniedWhenTheVisitorIsAnonymousWithoutEvenQueryingTheShareTable() {
            File linked = makeFile();
            linked.enableLinkSharing(UUID.randomUUID(), Role.EDITOR);

            assertDenied(catchThrowable(() -> fileAccessGuard.requirePermission(linked, null, Permission.READ)));
            then(findFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자가 공유받지 않은 제3자일 때")
    class WhenCallerIsStranger {

        @Test
        void isDeniedEveryPermission() {
            givenNoShare(strangerId);

            assertDenied(catchThrowable(() -> fileAccessGuard.requirePermission(file, strangerId, Permission.READ)));
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
