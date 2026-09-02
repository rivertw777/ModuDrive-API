package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FileAccessGuardTest {

    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FindFilePort findFilePort;
    @InjectMocks private FileAccessGuard fileAccessGuard;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID namespaceId = UUID.randomUUID();

    /** A file two directories deep: /shared/sub/report.pdf */
    private File file(UUID id, String path) {
        return File.withId(new FileId(id), new FileNamespaceId(namespaceId),
                new FileName("report.pdf"), new FilePath(path), new FileOwnerId(ownerId),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
    }

    private File directory(UUID id, String path, String name) {
        return File.withId(new FileId(id), new FileNamespaceId(namespaceId),
                new FileName(name), new FilePath(path), new FileOwnerId(ownerId),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
    }

    private File linkDirectory(UUID id, String path, String name) {
        File dir = directory(id, path, name);
        dir.enableLinkSharing(UUID.randomUUID(), Role.VIEWER);
        return dir;
    }

    private FileShare grant(UUID targetFileId, UUID grantee, Role role) {
        return FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(targetFileId),
                new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(grantee), new FileShareRole(role));
    }

    @Nested
    @DisplayName("소유자는")
    class Owner {

        @Test
        void alwaysPasses() {
            File f = file(UUID.randomUUID(), "/");

            assertThatCode(() -> fileAccessGuard.requirePermission(f, ownerId, Permission.RENAME))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("파일에 직접 공유가 있을 때")
    class DirectShare {

        @Test
        void viewerCanReadButNotRename() {
            UUID fileId = UUID.randomUUID();
            File f = file(fileId, "/");
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.of(grant(fileId, callerId, Role.VIEWER)));

            assertThatCode(() -> fileAccessGuard.requirePermission(f, callerId, Permission.READ))
                    .doesNotThrowAnyException();

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(f, callerId, Permission.RENAME));
            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("상위 디렉토리에서 상속될 때")
    class InheritedFromAncestor {

        private final UUID fileId = UUID.randomUUID();
        private final UUID sharedDirId = UUID.randomUUID();
        private final UUID subDirId = UUID.randomUUID();
        private final File f = file(fileId, "/shared/sub");

        private void ancestorsExist() {
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "shared"))
                    .willReturn(Optional.of(directory(sharedDirId, "/", "shared")));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/shared", "sub"))
                    .willReturn(Optional.of(directory(subDirId, "/shared", "sub")));
        }

        @Test
        void grantOnAncestorDirectoryReachesTheFile() {
            ancestorsExist();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .willReturn(Optional.of(grant(sharedDirId, callerId, Role.VIEWER)));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(subDirId), callerId))
                    .willReturn(Optional.empty());

            assertThatCode(() -> fileAccessGuard.requirePermission(f, callerId, Permission.READ))
                    .doesNotThrowAnyException();
        }

        @Test
        void takesTheMostGenerousRoleAcrossDirectAndInheritedGrants() {
            ancestorsExist();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.of(grant(fileId, callerId, Role.VIEWER)));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .willReturn(Optional.of(grant(sharedDirId, callerId, Role.EDITOR)));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(subDirId), callerId))
                    .willReturn(Optional.empty());

            assertThatCode(() -> fileAccessGuard.requirePermission(f, callerId, Permission.RENAME))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("조상 폴더가 LINK scope여도 인증 라우트로는 상속되지 않는다 (설계 결정 #3)")
        void ancestorLinkScopeIsNotInheritedOnAuthenticatedRoutes() {
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "shared"))
                    .willReturn(Optional.of(linkDirectory(sharedDirId, "/", "shared")));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/shared", "sub"))
                    .willReturn(Optional.of(directory(subDirId, "/shared", "sub")));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(subDirId), callerId))
                    .willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(f, callerId, Permission.READ));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("휴지통에 들어간 하위 파일은 조상 grant가 있어도 접근 거부")
        void deniesATrashedDescendantEvenWithAnInheritedGrant() {
            // DELETED is checked before any grant lookup: a still-live ancestor share must not
            // keep a soft-deleted descendant reachable, so resolveRole is never consulted here.
            File trashed = File.withId(new FileId(fileId), new FileNamespaceId(namespaceId),
                    new FileName("report.pdf"), new FilePath("/shared/sub"), new FileOwnerId(ownerId),
                    null, null, FileStatus.DELETED, new FileIsDirectory(false));

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(trashed, callerId, Permission.DOWNLOAD));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        @Test
        void deniesWhenNoGrantAnywhereOnThePath() {
            ancestorsExist();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(subDirId), callerId))
                    .willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(f, callerId, Permission.READ));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("익명 호출자(callerId=null)는")
    class AnonymousCaller {

        @Test
        void alwaysDenied() {
            File f = file(UUID.randomUUID(), "/");

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(f, null, Permission.READ));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("ancestorDirectories 는")
    class AncestorDirectories {

        @Test
        void isEmptyForARootLevelFile() {
            assertThat(fileAccessGuard.ancestorDirectories(file(UUID.randomUUID(), "/"))).isEmpty();
        }

        @Test
        void returnsEachDirectoryOnThePathRootMostFirst() {
            File f = file(UUID.randomUUID(), "/shared/sub");
            File shared = directory(UUID.randomUUID(), "/", "shared");
            File sub = directory(UUID.randomUUID(), "/shared", "sub");
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "shared"))
                    .willReturn(Optional.of(shared));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/shared", "sub"))
                    .willReturn(Optional.of(sub));

            assertThat(fileAccessGuard.ancestorDirectories(f)).containsExactly(shared, sub);
        }
    }
}
