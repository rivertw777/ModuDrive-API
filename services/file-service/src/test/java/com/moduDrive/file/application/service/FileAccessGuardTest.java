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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FileAccessGuardTest {

    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FindFilePort findFilePort;
    @InjectMocks private FileAccessGuard fileAccessGuard;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID namespaceId = UUID.randomUUID();

    /** A file two directories deep: /shared/sub/report.pdf */
    /**
     * The guard asks for the whole path at once — every folder above the file in one call, every
     * grant on them in another. These answers rebuild both from the per-slot / per-file stubs the
     * tests below set up, so each test still reads as "this folder exists, this grant exists" and
     * the priority rules are what's under test. The batch queries themselves are tested on Postgres
     * (FilePersistenceAdapterTest).
     */
    @BeforeEach
    void answerBatchLookupsFromPerItemStubs() {
        lenient().when(findFilePort.findActiveAncestorDirectories(any(), any())).thenAnswer(inv -> {
            NamespaceId ns = inv.getArgument(0);
            String path = inv.getArgument(1);
            List<File> ancestors = new ArrayList<>();
            if (path == null || "/".equals(path)) {
                return ancestors;
            }
            String walked = "/";
            for (String name : path.substring(1).split("/")) {
                Optional<File> found = findFilePort.findActiveByNamespaceIdAndPathAndName(ns, walked, name);
                if (found != null) {
                    found.filter(File::isDirectory).ifPresent(ancestors::add);
                }
                walked = "/".equals(walked) ? "/" + name : walked + "/" + name;
            }
            return ancestors;
        });
        lenient().when(findFileSharePort.findBySharedWithUserIdAndFileIdIn(any(), any())).thenAnswer(inv -> {
            UUID userId = inv.getArgument(0);
            Collection<FileId> fileIds = inv.getArgument(1);
            List<FileShare> grants = new ArrayList<>();
            for (FileId id : fileIds) {
                Optional<FileShare> found = findFileSharePort.findByFileIdAndSharedWithUserId(id, userId);
                if (found != null) {
                    found.ifPresent(grants::add);
                }
            }
            return grants;
        });
    }

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
        dir.enableLinkSharing();
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
    @DisplayName("effectiveRole 은")
    class EffectiveRole {

        @Test
        @DisplayName("소유자에겐 null (OWNER 롤이 따로 없다)")
        void nullForOwner() {
            File f = file(UUID.randomUUID(), "/");

            assertThat(fileAccessGuard.effectiveRole(f, ownerId)).isNull();
        }

        @Test
        @DisplayName("직접 grant가 있으면 그 role을 돌려준다")
        void returnsTheCallersDirectGrant() {
            UUID fileId = UUID.randomUUID();
            File f = file(fileId, "/");
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.of(grant(fileId, callerId, Role.EDITOR)));

            assertThat(fileAccessGuard.effectiveRole(f, callerId)).isEqualTo(Role.EDITOR);
        }

        @Test
        @DisplayName("grant가 전혀 없으면 null")
        void nullWhenNoGrantAnywhere() {
            UUID fileId = UUID.randomUUID();
            File f = file(fileId, "/");
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());

            assertThat(fileAccessGuard.effectiveRole(f, callerId)).isNull();
        }
    }

    @Nested
    @DisplayName("requireOwner 는")
    class RequireOwner {

        @Test
        void passesForTheOwner() {
            File f = file(UUID.randomUUID(), "/");

            assertThatCode(() -> fileAccessGuard.requireOwner(f, ownerId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("소유자가 아니면 grant가 있어도 거부한다 (owner-only 액션)")
        void deniesANonOwnerEvenWithAGrant() {
            File f = file(UUID.randomUUID(), "/");

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requireOwner(f, callerId));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("거부 응답은 파일/폴더 여부를 함께 담는다 — 이미 찾은 대상이라 존재 자체를 새로 드러내는 건 아니다")
        void deniedResponseCarriesIsDirectory() {
            File dir = directory(UUID.randomUUID(), "/", "새 폴더");

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requireOwner(dir, callerId));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getData())
                    .isEqualTo(java.util.Map.of("isDirectory", true));
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
        @DisplayName("파일 자신에 직접 grant가 있으면, 조상이 더 관대해도 직접 grant가 이긴다")
        void aDirectGrantOverridesAMoreGenerousInheritedOne() {
            // lenient(): these ancestor stubs must never actually be consulted — the direct grant
            // short-circuits before ancestorDirectories() is even called. A more generous ancestor
            // (EDITOR) is wired in specifically so this test would fail on the old
            // fold-all-ancestors-together behavior (which would have let RENAME through).
            lenient().when(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "shared"))
                    .thenReturn(Optional.of(directory(sharedDirId, "/", "shared")));
            lenient().when(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/shared", "sub"))
                    .thenReturn(Optional.of(directory(subDirId, "/shared", "sub")));
            lenient().when(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .thenReturn(Optional.of(grant(sharedDirId, callerId, Role.EDITOR)));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.of(grant(fileId, callerId, Role.VIEWER)));

            assertThatCode(() -> fileAccessGuard.requirePermission(f, callerId, Permission.READ))
                    .doesNotThrowAnyException();
            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(f, callerId, Permission.RENAME));
            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("직접 grant가 없으면 가장 가까운 조상의 role이 적용된다 (더 관대해도 먼 조상은 지지 않는다)")
        void takesTheNearestAncestorsRoleEvenWhenAFartherOneIsMoreGenerous() {
            ancestorsExist();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            // Never actually consulted — foldAncestors returns on the nearest hit (subDirId)
            // before reaching this farther, more generous one. Wired in specifically so this test
            // would fail on the old most-generous-wins behavior.
            lenient().when(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .thenReturn(Optional.of(grant(sharedDirId, callerId, Role.EDITOR)));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(subDirId), callerId))
                    .willReturn(Optional.of(grant(subDirId, callerId, Role.VIEWER)));

            // Pins the resolved role at exactly VIEWER (the nearer ancestor's), not just "not
            // EDITOR" — READ must still pass or a foldAncestors that returned null would also
            // make the RENAME assertion below pass vacuously.
            assertThatCode(() -> fileAccessGuard.requirePermission(f, callerId, Permission.READ))
                    .doesNotThrowAnyException();
            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(f, callerId, Permission.RENAME));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("조상 폴더가 LINK scope면 인증 라우트에서도 뷰어로 상속된다 (issue #303)")
        void ancestorLinkScopeIsInheritedAsViewerOnAuthenticatedRoutes() {
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

            assertThatCode(() -> fileAccessGuard.requirePermission(f, callerId, Permission.READ))
                    .doesNotThrowAnyException();
            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(f, callerId, Permission.RENAME));
            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("파일 자신이 LINK scope면 인증 라우트에서도 뷰어로 접근된다 (issue #303)")
        void ownLinkScopeGrantsViewerOnAuthenticatedRoutes() {
            File linked = file(fileId, "/shared/sub");
            linked.enableLinkSharing();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "shared"))
                    .willReturn(Optional.of(directory(sharedDirId, "/", "shared")));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/shared", "sub"))
                    .willReturn(Optional.of(directory(subDirId, "/shared", "sub")));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(subDirId), callerId))
                    .willReturn(Optional.empty());

            assertThatCode(() -> fileAccessGuard.requirePermission(linked, callerId, Permission.READ))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("조상이 LINK scope여도, 어딘가에 이름 붙은 grant가 있으면 그게 이긴다")
        void aNamedGrantOutranksAnAncestorsLinkScope() {
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "shared"))
                    .willReturn(Optional.of(linkDirectory(sharedDirId, "/", "shared")));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/shared", "sub"))
                    .willReturn(Optional.of(directory(subDirId, "/shared", "sub")));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            // subDir's own named grant is nearer than the LINK-scoped grandparent, so foldAncestors
            // returns on this hit without ever consulting sharedDir — RENAME must pass. A LINK
            // fallback capped at VIEWER would wrongly deny this.
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(subDirId), callerId))
                    .willReturn(Optional.of(grant(subDirId, callerId, Role.EDITOR)));

            assertThatCode(() -> fileAccessGuard.requirePermission(f, callerId, Permission.RENAME))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("휴지통에 들어간 하위 파일은 조상 grant가 있어도 접근 거부")
        void deniesATrashedDescendantEvenWithAnInheritedGrant() {
            // isRemoved() is checked before any grant lookup: a still-live ancestor share must not
            // keep a soft-deleted descendant reachable, so access() is never consulted here.
            File trashed = File.withId(new FileId(fileId), new FileNamespaceId(namespaceId),
                    new FileName("report.pdf"), new FilePath("/shared/sub"), new FileOwnerId(ownerId),
                    null, null, FileStatus.TRASHED, new FileIsDirectory(false));

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(trashed, callerId, Permission.DOWNLOAD));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("퍼지된(tombstone) 하위 파일도 조상 grant가 있어도 접근 거부")
        void deniesAPurgedDescendantEvenWithAnInheritedGrant() {
            File purged = File.withId(new FileId(fileId), new FileNamespaceId(namespaceId),
                    new FileName("report.pdf"), new FilePath("/shared/sub"), new FileOwnerId(ownerId),
                    null, null, FileStatus.DELETED, new FileIsDirectory(false));

            Throwable thrown = catchThrowable(() -> fileAccessGuard.requirePermission(purged, callerId, Permission.DOWNLOAD));

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
    @DisplayName("access().role() 은 (공유 폴더 자식 목록이 물려받을 role)")
    class InheritableRole {

        private final UUID grandparentId = UUID.randomUUID();
        private final UUID directoryId = UUID.randomUUID();
        // /grandparent/directory — directory 자신도 grant를 가질 수 있고, 그 위 grandparent도
        // 별도로 가질 수 있다. directory 입장에선 자신이 곧 "가장 가까운 조상"이라, 관대함과
        // 무관하게 grandparent보다 항상 이긴다 (access()의 direct-wins와 동일한 패턴).
        private final File directory = directory(directoryId, "/grandparent", "directory");

        // lenient(): unused in directorysOwnGrantWinsOverAMoreGenerousGrandparentGrant, where
        // directory's own grant short-circuits before ancestorDirectories() is even called.
        private void grandparentExists() {
            lenient().when(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "grandparent"))
                    .thenReturn(Optional.of(directory(grandparentId, "/", "grandparent")));
        }

        @Test
        @DisplayName("directory 자신의 grant가 grandparent보다 약해도, 더 가까운 자신의 grant가 이긴다")
        void directorysOwnGrantWinsOverAMoreGenerousGrandparentGrant() {
            grandparentExists();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(directoryId), callerId))
                    .willReturn(Optional.of(grant(directoryId, callerId, Role.VIEWER)));
            lenient().when(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(grandparentId), callerId))
                    .thenReturn(Optional.of(grant(grandparentId, callerId, Role.EDITOR)));

            assertThat(fileAccessGuard.access(directory, callerId).role()).isEqualTo(Role.VIEWER);
        }

        @Test
        @DisplayName("directory 자신에 grant가 없으면 grandparent의 grant로 폴백한다")
        void fallsBackToTheGrandparentsGrantWhenDirectoryHasNoneOfItsOwn() {
            grandparentExists();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(directoryId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(grandparentId), callerId))
                    .willReturn(Optional.of(grant(grandparentId, callerId, Role.EDITOR)));

            assertThat(fileAccessGuard.access(directory, callerId).role()).isEqualTo(Role.EDITOR);
        }

        @Test
        void nullForAnonymousCaller() {
            assertThat(fileAccessGuard.access(directory, null).role()).isNull();
        }
    }

    @Nested
    @DisplayName("linkRoleFallback 은 (익명 라우트가 직접 쓰는, 로그인 여부 무관한 LINK 판정)")
    class LinkRoleFallback {

        @Test
        @DisplayName("파일 자신이 LINK scope면 그 role을 돌려준다")
        void ownScopeWins() {
            File linked = file(UUID.randomUUID(), "/");
            linked.enableLinkSharing();

            assertThat(fileAccessGuard.linkRoleFallback(linked, List.of())).isEqualTo(Role.VIEWER);
        }

        @Test
        @DisplayName("자신은 LINK가 아니어도 조상 중 하나가 LINK면 그 role을 돌려준다")
        void fallsBackToAnAncestorsScope() {
            File f = file(UUID.randomUUID(), "/shared");
            File linkedAncestor = linkDirectory(UUID.randomUUID(), "/", "shared");

            assertThat(fileAccessGuard.linkRoleFallback(f, List.of(linkedAncestor))).isEqualTo(Role.VIEWER);
        }

        @Test
        @DisplayName("자신도 조상 누구도 LINK가 아니면 null")
        void nullWhenNeitherFileNorAnyAncestorIsLinkScoped() {
            File f = file(UUID.randomUUID(), "/shared");
            File plainAncestor = directory(UUID.randomUUID(), "/", "shared");

            assertThat(fileAccessGuard.linkRoleFallback(f, List.of(plainAncestor))).isNull();
        }
    }

    @Nested
    @DisplayName("access().grant() 는")
    class ResolveGrant {

        private final UUID fileId = UUID.randomUUID();
        private final UUID sharedDirId = UUID.randomUUID();
        private final File f = file(fileId, "/shared");

        private void ancestorExists() {
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "shared"))
                    .willReturn(Optional.of(directory(sharedDirId, "/", "shared")));
        }

        @Test
        @DisplayName("파일 자신에 직접 grant가 있으면 그걸 돌려준다")
        void prefersTheFilesOwnGrant() {
            FileShare own = grant(fileId, callerId, Role.VIEWER);
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.of(own));

            assertThat(Optional.ofNullable(fileAccessGuard.access(f, callerId).grant())).contains(own);
        }

        @Test
        @DisplayName("파일 자신엔 grant가 없으면 가장 가까운 상위 폴더의 grant로 대체한다")
        void fallsBackToTheNearestAncestorsGrant() {
            ancestorExists();
            FileShare ancestorGrant = grant(sharedDirId, callerId, Role.EDITOR);
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .willReturn(Optional.of(ancestorGrant));

            assertThat(Optional.ofNullable(fileAccessGuard.access(f, callerId).grant())).contains(ancestorGrant);
        }

        @Test
        @DisplayName("조상 여러 곳에 grant가 있으면 가장 가까운(자식 쪽) 조상의 grant를 돌려준다")
        void prefersTheNearerOfTwoAncestorGrants() {
            UUID nearFileId = UUID.randomUUID();
            UUID nearDirId = UUID.randomUUID();
            UUID farDirId = UUID.randomUUID();
            // /far/near/report.pdf — far는 루트 바로 밑, near는 far 밑, 파일은 near 밑.
            File nested = file(nearFileId, "/far/near");
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "far"))
                    .willReturn(Optional.of(directory(farDirId, "/", "far")));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/far", "near"))
                    .willReturn(Optional.of(directory(nearDirId, "/far", "near")));
            // near is checked first and matches, so far's own grant is never even queried —
            // access() takes on the first hit walking child-to-root.
            FileShare nearGrant = grant(nearDirId, callerId, Role.VIEWER);
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(nearFileId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(nearDirId), callerId))
                    .willReturn(Optional.of(nearGrant));

            assertThat(Optional.ofNullable(fileAccessGuard.access(nested, callerId).grant())).contains(nearGrant);
        }

        @Test
        @DisplayName("경로 어디에도 grant가 없으면 비어있다")
        void emptyWhenNoGrantAnywhere() {
            ancestorExists();
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(sharedDirId), callerId))
                    .willReturn(Optional.empty());

            assertThat(Optional.ofNullable(fileAccessGuard.access(f, callerId).grant())).isEmpty();
        }

        @Test
        @DisplayName("호출자가 null이면 조회 없이 비어있다")
        void emptyForAnonymousCaller() {
            assertThat(Optional.ofNullable(fileAccessGuard.access(f, null).grant())).isEmpty();
        }

        @Test
        @DisplayName("포트가 아직 claim 안 된 pending 게스트 share를 돌려줘도 그대로 반환한다")
        void returnsAStillPendingGuestShareAsIs() {
            // access() trusts whatever the port's query gives it — it doesn't re-check
            // claim state. A real repository query filters by sharedWithUserId=callerId, which a
            // pending row (sharedWithUserId==null) would never match, but access() itself has
            // no such guard, so a stubbed port here documents that it just passes the row through.
            FileShare pending = FileShare.createPending(new FileShareFileId(fileId),
                    new FileShareOwnerId(ownerId), new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.of(pending));

            assertThat(Optional.ofNullable(fileAccessGuard.access(f, callerId).grant())).contains(pending);
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
