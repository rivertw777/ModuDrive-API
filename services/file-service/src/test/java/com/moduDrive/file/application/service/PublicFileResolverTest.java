package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PublicFileResolverTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private PublicFileResolver publicFileResolver;

    private final UUID key = UUID.randomUUID();
    private final UUID namespaceId = UUID.randomUUID();

    private File file(String name, String path, boolean directory, FileStatus status) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId),
                new FileName(name), new FilePath(path), new FileOwnerId(UUID.randomUUID()),
                null, null, status, new FileIsDirectory(directory));
    }

    private void assertNotFound(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
    }

    private void givenFound(File f) {
        given(findFilePort.findById(new FileId(f.getId()))).willReturn(Optional.of(f));
    }

    /** The ancestor walk is computed once up front (issue #320) and shared by the LINK check and the guest-invite
     * check, so every scenario must stub it: an unstubbed mock returns null and matchesGuestInvite would NPE. */
    private void givenAncestors(File f, List<File> ancestors) {
        given(fileAccessGuard.ancestorDirectories(f)).willReturn(ancestors);
    }

    private void givenNoAncestors(File f) {
        givenAncestors(f, List.of());
    }

    private FileShare guestShareOn(File f) {
        return FileShare.createPending(new FileShareFileId(f.getId()), new FileShareOwnerId(f.getOwnerId()),
                new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER));
    }

    @Nested
    @DisplayName("fileId만으로 scope==LINK에 도달할 때 (issue #303) — key 불필요")
    class WhenLinkScopeReachesWithoutAKey {

        @Test
        @DisplayName("파일 자신이 LINK scope면 key가 없어도 열린다")
        void ownLinkScopeNeedsNoKey() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);
            givenNoAncestors(f);
            given(fileAccessGuard.linkRoleFallback(f, List.of())).willReturn(Role.VIEWER);

            assertThat(publicFileResolver.resolve(f.getId().toString(), null).getName())
                    .isEqualTo("report.pdf");
        }

        @Test
        @DisplayName("조상 폴더가 LINK scope면 key가 없어도(엉뚱한 key여도) 열린다")
        void ancestorLinkScopeNeedsNoKeyEitherAndIgnoresAGarbageKey() {
            File f = file("report.pdf", "/shared", false, FileStatus.UPLOADED);
            givenFound(f);
            givenNoAncestors(f);
            given(fileAccessGuard.linkRoleFallback(f, List.of())).willReturn(Role.VIEWER);

            assertThat(publicFileResolver.resolve(f.getId().toString(), "not-a-uuid").getName())
                    .isEqualTo("report.pdf");
            // The guest-token path is never even consulted once the scope check already succeeded.
            then(findFileSharePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("디렉토리 자신이 LINK scope면 key 없이도 자식 목록을 조회할 수 있다")
        void resolvesChildrenOfALinkScopedDirectoryWithoutAKey() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            givenFound(folder);
            givenNoAncestors(folder);
            given(fileAccessGuard.linkRoleFallback(folder, List.of())).willReturn(Role.VIEWER);
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            given(findFilePort.findByNamespaceIdAndPath(any(), eq("/shared"))).willReturn(List.of(child));

            assertThat(publicFileResolver.resolveChildren(folder.getId().toString(), null))
                    .containsExactly(child);
        }

        @Test
        @DisplayName("트래시/퍼지된 자식은 목록에서 제외된다")
        void excludesRemovedChildrenFromTheListing() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            givenFound(folder);
            givenNoAncestors(folder);
            given(fileAccessGuard.linkRoleFallback(folder, List.of())).willReturn(Role.VIEWER);
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            File trashed = file("b.txt", "/shared", false, FileStatus.TRASHED);
            given(findFilePort.findByNamespaceIdAndPath(any(), eq("/shared"))).willReturn(List.of(child, trashed));

            assertThat(publicFileResolver.resolveChildren(folder.getId().toString(), null)).containsExactly(child);
        }

        @Test
        @DisplayName("LINK scope여도 파일(디렉토리 아님)에는 자식 목록 조회가 안 된다")
        void nonDirectoryStillRejectedForResolveChildrenEvenWhenLinkScoped() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);

            assertNotFound(catchThrowable(() -> publicFileResolver.resolveChildren(f.getId().toString(), null)));
            // isDirectory() is checked first and short-circuits before any ancestor walk or scope
            // check — a file id used against this route was never going to authorize anything here.
            then(fileAccessGuard).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("scope==LINK가 아니고 게스트 초대도 없는 디렉토리는 자식 목록 조회가 거부된다")
        void rejectsResolveChildrenWhenTheDirectoryIsNotLinkScopedNorInvited() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            givenFound(folder);
            givenNoAncestors(folder);
            given(fileAccessGuard.linkRoleFallback(folder, List.of())).willReturn(null);

            // No key at all: matchesGuestInvite short-circuits on parseUuid before ever consulting
            // findFileSharePort, same as the "unknown/malformed key" cases in resolve().
            assertNotFound(catchThrowable(() -> publicFileResolver.resolveChildren(folder.getId().toString(), null)));
        }
    }

    @Nested
    @DisplayName("key가 게스트 초대(pending share) 토큰일 때")
    class WhenKeyIsAGuestShareToken {

        @Test
        void returnsTheFileEvenThoughItsScopeStaysRestricted() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);
            givenNoAncestors(f);
            given(fileAccessGuard.linkRoleFallback(f, List.of())).willReturn(null);
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(f)));

            assertThat(publicFileResolver.resolve(f.getId().toString(), key.toString()).getName())
                    .isEqualTo("report.pdf");
        }

        @Test
        @DisplayName("폴더에 발급된 토큰은 그 안의 파일도 열어준다 (폴더 공유의 상속과 동일)")
        void unlocksAFileNestedUnderTheInvitedFolder() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            givenFound(child);
            givenAncestors(child, List.of(folder));
            given(fileAccessGuard.linkRoleFallback(child, List.of(folder))).willReturn(null);
            // Token was minted for the folder, not this child — reached through inheritance.
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(folder)));

            assertThat(publicFileResolver.resolve(child.getId().toString(), key.toString()).getName())
                    .isEqualTo("a.txt");
        }

        @Test
        @DisplayName("다른 항목에 발급된 토큰으로는 무관한 항목을 열지 못한다")
        void doesNotUnlockAnUnrelatedEntry() {
            File other = file("private.txt", "/1", false, FileStatus.UPLOADED);
            File unrelated = file("a.txt", "/2", false, FileStatus.UPLOADED);
            givenFound(unrelated);
            givenNoAncestors(unrelated);
            given(fileAccessGuard.linkRoleFallback(unrelated, List.of())).willReturn(null);
            // Token was minted for a file that isn't an ancestor of (or the same as) the target.
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(other)));

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(unrelated.getId().toString(), key.toString())));
        }

        @Test
        @DisplayName("게스트 토큰이 폴더 자신 또는 조상 폴더에 발급되어 있으면 폴더 목록도 조회할 수 있다")
        void listsChildrenWhenTheGuestTokenReachesTheDirectory() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            givenFound(folder);
            givenNoAncestors(folder);
            given(fileAccessGuard.linkRoleFallback(folder, List.of())).willReturn(null);
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(folder)));
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            given(findFilePort.findByNamespaceIdAndPath(any(), eq("/shared"))).willReturn(List.of(child));

            assertThat(publicFileResolver.resolveChildren(folder.getId().toString(), key.toString()))
                    .containsExactly(child);
        }

        @Test
        @DisplayName("무관한 항목에 발급된 게스트 토큰으로는 폴더 목록을 조회할 수 없다")
        void cannotListChildrenWithAnUnrelatedGuestToken() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            File other = file("private.txt", "/1", false, FileStatus.UPLOADED);
            givenFound(folder);
            givenNoAncestors(folder);
            given(fileAccessGuard.linkRoleFallback(folder, List.of())).willReturn(null);
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(other)));

            assertNotFound(catchThrowable(() -> publicFileResolver.resolveChildren(folder.getId().toString(), key.toString())));
        }
    }

    @Nested
    @DisplayName("공개된 파일이 휴지통으로 갔을 때")
    class WhenFileIsDeleted {

        @Test
        @DisplayName("target() 자체가 트래시를 걸러내므로 scope/key와 무관하게 거부된다")
        void throwsFileNotFoundWhenTheTargetIsTrashed() {
            File f = file("report.pdf", "/1", false, FileStatus.TRASHED);
            givenFound(f);

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), key.toString())));
        }
    }

    @Nested
    @DisplayName("잘못된 입력")
    class WhenInputIsBad {

        @Test
        void unknownFileId() {
            UUID id = UUID.randomUUID();
            given(findFilePort.findById(new FileId(id))).willReturn(Optional.empty());

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(id.toString(), key.toString())));
        }

        @Test
        void malformedFileId() {
            assertNotFound(catchThrowable(() -> publicFileResolver.resolve("not-a-uuid", key.toString())));
        }

        @Test
        void unknownOrExpiredKey() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);
            givenNoAncestors(f);
            given(fileAccessGuard.linkRoleFallback(f, List.of())).willReturn(null);
            given(findFileSharePort.findByToken(key)).willReturn(Optional.empty());

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), key.toString())));
        }

        @Test
        void malformedKey() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);
            givenNoAncestors(f);
            given(fileAccessGuard.linkRoleFallback(f, List.of())).willReturn(null);

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), "not-a-uuid")));
        }

        @Test
        void nullKey() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);
            givenNoAncestors(f);
            given(fileAccessGuard.linkRoleFallback(f, List.of())).willReturn(null);

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), null)));
        }

        @Test
        void blankKey() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);
            givenNoAncestors(f);
            given(fileAccessGuard.linkRoleFallback(f, List.of())).willReturn(null);

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), "  ")));
        }
    }
}
