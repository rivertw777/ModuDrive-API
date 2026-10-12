package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileScopeCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UpdateFileScopeServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
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
        void switchesScopeToLinkWithAViewerRole() {
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(makeFile()));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileScopeService.updateFileScope(command(ShareScope.LINK));

            assertThat(result.getAccessScope()).isEqualTo(ShareScope.LINK);
            assertThat(result.getLinkRole()).isEqualTo(Role.VIEWER);
        }

        @Test
        @DisplayName("LINK 전환은 조상을 건드리지 않는다 (restrictLinkedAncestors는 RESTRICTED 분기 전용)")
        void neverTouchesAncestors() {
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(makeFile()));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.LINK));

            then(fileAccessGuard).should(never()).ancestorDirectories(any());
            then(saveFilePort).should(times(1)).saveFile(any(File.class));
        }
    }

    @Nested
    @DisplayName("LINK 파일을 RESTRICTED로 되돌릴 때")
    class WhenSwitchingToRestricted {

        @Test
        void clearsTheScope() {
            File linked = makeFile();
            linked.enableLinkSharing();
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(linked));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            assertThat(result.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
            assertThat(result.getLinkRole()).isNull();
        }

        // Email invites (FileShare rows) are never touched by a scope change (issue #303). The service has no FileShare
        // port, so the guarantee is structural and no mock interaction could regress it.
    }

    @Nested
    @DisplayName("LINK 디렉토리를 RESTRICTED로 되돌릴 때")
    class WhenRestrictingALinkedDirectory {

        private final UUID namespaceId = UUID.randomUUID();

        private File makeLinkedDirectory() {
            File directory = File.withId(new FileId(fileId), new FileNamespaceId(namespaceId),
                    new FileName("a"), new FilePath("/"), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
            directory.enableLinkSharing();
            return directory;
        }

        private File makeDescendant(String name) {
            return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId),
                    new FileName(name), new FilePath("/a"), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
        }

        @Test
        void turnsOffADescendantsOwnIndependentLinkToo() {
            File directory = makeLinkedDirectory();
            // b: shared with its own separate LINK, not merely inherited from `a`.
            File descendantWithOwnLink = makeDescendant("b");
            descendantWithOwnLink.enableLinkSharing();
            // c: never had its own scope — already RESTRICTED, only ever reachable via `a`.
            File alreadyRestrictedDescendant = makeDescendant("c");

            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(directory));
            given(findFilePort.findByNamespaceIdAndPathStartingWith(
                    new NamespaceId(namespaceId), directory.fullPath()))
                    .willReturn(List.of(descendantWithOwnLink, alreadyRestrictedDescendant));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            assertThat(descendantWithOwnLink.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
            assertThat(descendantWithOwnLink.getLinkRole()).isNull();
            then(saveFilePort).should().saveFile(descendantWithOwnLink);
            // No scope of its own to clear; restricting `a` already cuts its only (inherited) access path.
            then(saveFilePort).should(never()).saveFile(alreadyRestrictedDescendant);
        }

        @Test
        void doesNotSweepWhenTheDirectoryWasAlreadyRestricted() {
            // A RESTRICTED->RESTRICTED request must not sweep descendants; that would kill their own link scopes for no change.
            File directory = File.withId(new FileId(fileId), new FileNamespaceId(namespaceId),
                    new FileName("a"), new FilePath("/"), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(directory));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            then(findFilePort).should(never())
                    .findByNamespaceIdAndPathStartingWith(any(NamespaceId.class), any(String.class));
        }

        @Test
        void leavesDescendantsAloneWhenRestrictingAPlainFile() {
            // makeFile() is a leaf file, not a directory — no subtree to sweep.
            File linkedFile = makeFile();
            linkedFile.enableLinkSharing();
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(linkedFile));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            then(findFilePort).should(never())
                    .findByNamespaceIdAndPathStartingWith(any(NamespaceId.class), any(String.class));
        }
    }

    @Nested
    @DisplayName("조상 폴더가 LINK로 공유 중인 파일을 RESTRICTED로 바꿀 때")
    class WhenAnAncestorIsStillLinkShared {

        private final UUID namespaceId = UUID.randomUUID();

        private File makeLinkedAncestor(String name, String path) {
            File ancestor = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId),
                    new FileName(name), new FilePath(path), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
            ancestor.enableLinkSharing();
            return ancestor;
        }

        @Test
        void restrictsTheAncestorToo() {
            // FileAccessGuard's link fallback still gives VIEWER through the ancestor, so leaving it LINK would answer RESTRICTED for a public file.
            File linkedFile = makeFile();
            linkedFile.enableLinkSharing();
            File ancestor = makeLinkedAncestor("a", "/");
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(linkedFile));
            given(fileAccessGuard.ancestorDirectories(linkedFile)).willReturn(List.of(ancestor));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            assertThat(ancestor.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
            assertThat(ancestor.getLinkRole()).isNull();
            then(saveFilePort).should().saveFile(ancestor);
        }

        @Test
        void sweepsTheAncestorsOwnSubtreeToo() {
            // Restricting the ancestor must make everything under it non-public, including a sibling with its own LINK scope.
            File linkedFile = makeFile();
            linkedFile.enableLinkSharing();
            File ancestor = makeLinkedAncestor("a", "/");
            File siblingWithOwnLink = File.withId(new FileId(UUID.randomUUID()),
                    new FileNamespaceId(namespaceId), new FileName("sibling.pdf"), new FilePath("/a"),
                    new FileOwnerId(ownerId), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            siblingWithOwnLink.enableLinkSharing();
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(linkedFile));
            given(fileAccessGuard.ancestorDirectories(linkedFile)).willReturn(List.of(ancestor));
            given(findFilePort.findByNamespaceIdAndPathStartingWith(
                    new NamespaceId(namespaceId), ancestor.fullPath()))
                    .willReturn(List.of(siblingWithOwnLink));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            assertThat(siblingWithOwnLink.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
            then(saveFilePort).should().saveFile(siblingWithOwnLink);
        }

        @Test
        void sweepsOnlyTheRootMostLinkedAncestorsSubtree() {
            // g(LINK) > p(LINK) > the file. Sweeping g already reaches p and everything under it, so
            // sweeping p again would rewrite the same rows twice.
            File linkedFile = makeFile();
            linkedFile.enableLinkSharing();
            File grandParent = makeLinkedAncestor("g", "/");
            File parent = makeLinkedAncestor("p", "/g");
            // The row the sweep loads for p is a separate instance from the one the ancestor walk returned, as with a real
            // repository, so the walk still sees p as LINK after the sweep restricted it.
            File parentAsLoadedBySweep = File.withId(new FileId(parent.getId()), new FileNamespaceId(namespaceId),
                    new FileName("p"), new FilePath("/g"), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
            parentAsLoadedBySweep.enableLinkSharing();
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(linkedFile));
            given(fileAccessGuard.ancestorDirectories(linkedFile)).willReturn(List.of(grandParent, parent));
            given(findFilePort.findByNamespaceIdAndPathStartingWith(
                    new NamespaceId(namespaceId), grandParent.fullPath()))
                    .willReturn(List.of(parentAsLoadedBySweep));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            then(findFilePort).should(never())
                    .findByNamespaceIdAndPathStartingWith(any(NamespaceId.class), eq(parent.fullPath()));
            // Exactly three writes: the file itself, g, and p's row once.
            then(saveFilePort).should(times(3)).saveFile(any(File.class));
            then(saveFilePort).should(never()).saveFile(parent);
            assertThat(parentAsLoadedBySweep.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
        }

        @Test
        void cleansTheAncestorEvenWhenTheFileWasAlreadyRestricted() {
            // The file's stored scope never changes, so the request is a no-op for the file itself, but the ancestor
            // check must still run; it must not sit behind the wasLinkShared guard that suppresses the descendant sweep.
            File alreadyRestricted = makeFile();
            File ancestor = makeLinkedAncestor("a", "/");
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(alreadyRestricted));
            given(fileAccessGuard.ancestorDirectories(alreadyRestricted)).willReturn(List.of(ancestor));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            assertThat(ancestor.getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
            then(saveFilePort).should().saveFile(ancestor);
        }

        @Test
        void savesNothingExtraWhenNoAncestorIsLinkShared() {
            File alreadyRestricted = makeFile();
            File plainAncestor = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId),
                    new FileName("a"), new FilePath("/"), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(alreadyRestricted));
            given(fileAccessGuard.ancestorDirectories(alreadyRestricted)).willReturn(List.of(plainAncestor));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            updateFileScopeService.updateFileScope(command(ShareScope.RESTRICTED));

            then(saveFilePort).should(times(1)).saveFile(any(File.class));
            then(saveFilePort).should().saveFile(alreadyRestricted);
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

        @Test
        void throwsInvalidLinkRoleWhenRoleIsEditor() {
            given(findFilePort.findById(new FileId(fileId))).willReturn(Optional.of(makeFile()));

            Throwable thrown = catchThrowable(
                    () -> updateFileScopeService.updateFileScope(command(ShareScope.LINK, Role.EDITOR)));

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
