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
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ListFileSharesServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private FindMemberByIdPort findMemberByIdPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private ListFileSharesService listFileSharesService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final ListFileSharesCommand command = new ListFileSharesCommand(fileId, ownerId);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    private FileShare memberShare(UUID targetFileId, UUID grantee, Role role) {
        return FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(targetFileId),
                new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(grantee), new FileShareRole(role));
    }

    @Nested
    @DisplayName("소유자가 공유 목록을 조회할 때")
    class WhenOwnerLists {

        @Test
        void returnsFileWithItsSharesAndMemberSummaries() {
            UUID sharedWithUserId = UUID.randomUUID();
            FileShare share = memberShare(fileId, sharedWithUserId, Role.EDITOR);
            MemberSummary summary = new MemberSummary("river", "river@modudrive.com");
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of(share));
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of());
            given(findMemberByIdPort.findMemberById(sharedWithUserId)).willReturn(summary);

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.file().getOwnerId()).isEqualTo(ownerId);
            assertThat(result.shares()).containsExactly(share);
            assertThat(result.inheritedShares()).isEmpty();
            assertThat(result.inheritedLinkSources()).isEmpty();
            assertThat(result.memberSummaries()).containsEntry(sharedWithUserId, summary);
            assertThat(result.hasSharedDescendant()).isFalse();
        }

        @Test
        void returnsEmptyShareListWhenNobodyWasInvited() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of());
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of());

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.shares()).isEmpty();
            assertThat(result.memberSummaries()).isEmpty();
        }

        @Test
        void degradesToUnknownWhenMemberLookupFails() {
            UUID sharedWithUserId = UUID.randomUUID();
            FileShare share = memberShare(fileId, sharedWithUserId, Role.VIEWER);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of(share));
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of());
            willThrow(new BusinessException(FileExceptionCase.SHARE_TARGET_NOT_FOUND))
                    .given(findMemberByIdPort).findMemberById(sharedWithUserId);

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.shares()).containsExactly(share);
            assertThat(result.memberSummaries().get(sharedWithUserId))
                    .isEqualTo(new MemberSummary(null, null));
        }
    }

    @Nested
    @DisplayName("상위 디렉토리에 공유가 있을 때")
    class WhenAncestorDirectoryIsShared {

        private final UUID parentId = UUID.randomUUID();
        private final File parentDir = File.withId(new FileId(parentId), new FileNamespaceId(file.getNamespaceId()),
                new FileName("shared-folder"), new FilePath("/"), new FileOwnerId(ownerId),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(true));

        @Test
        void includesInheritedMemberGrantTaggedWithSourceDirectory() {
            UUID grantee = UUID.randomUUID();
            FileShare inherited = memberShare(parentId, grantee, Role.VIEWER);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of());
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of(parentDir));
            given(findFileSharePort.findByFileId(new FileId(parentId))).willReturn(List.of(inherited));
            given(findMemberByIdPort.findMemberById(grantee))
                    .willReturn(new MemberSummary("guest", "guest@modudrive.com"));

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.shares()).isEmpty();
            assertThat(result.inheritedShares()).hasSize(1);
            assertThat(result.inheritedShares().get(0).share()).isEqualTo(inherited);
            assertThat(result.inheritedShares().get(0).source()).isEqualTo(parentDir);
        }

        @Test
        @DisplayName("같은 사람이 이 파일에 직접 공유도 있으면 direct/inherited 둘 다 돌려준다 (상위 grant까지 지워야 진짜 revoke — RevokeInheritedDialog가 이 정보로 판단함)")
        void includesInheritedGrantEvenWhenSameMemberIsAlsoSharedDirectly() {
            UUID grantee = UUID.randomUUID();
            FileShare direct = memberShare(fileId, grantee, Role.EDITOR);
            FileShare inherited = memberShare(parentId, grantee, Role.VIEWER);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of(direct));
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of(parentDir));
            given(findFileSharePort.findByFileId(new FileId(parentId))).willReturn(List.of(inherited));
            given(findMemberByIdPort.findMemberById(grantee))
                    .willReturn(new MemberSummary("guest", "guest@modudrive.com"));

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.shares()).containsExactly(direct);
            assertThat(result.inheritedShares()).hasSize(1);
            assertThat(result.inheritedShares().get(0).share()).isEqualTo(inherited);
        }

        @Test
        @DisplayName("조상 둘이 같은 멤버에게 각자 공유했으면 둘 다 반환 (하나로 합치지 않음 — 완전히 revoke하려면 둘 다 지워야 함)")
        void doesNotCollapseTwoIndependentAncestorGrantsOnTheSameMember() {
            UUID nearId = UUID.randomUUID();
            File nearDir = File.withId(new FileId(nearId), new FileNamespaceId(file.getNamespaceId()),
                    new FileName("sub"), new FilePath("/shared-folder"), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
            UUID grantee = UUID.randomUUID();
            FileShare rootGrant = memberShare(parentId, grantee, Role.VIEWER);
            FileShare nearGrant = memberShare(nearId, grantee, Role.EDITOR);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of());
            // ancestorDirectories returns root-most first
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of(parentDir, nearDir));
            given(findFileSharePort.findByFileId(new FileId(parentId))).willReturn(List.of(rootGrant));
            given(findFileSharePort.findByFileId(new FileId(nearId))).willReturn(List.of(nearGrant));
            given(findMemberByIdPort.findMemberById(grantee))
                    .willReturn(new MemberSummary("guest", "guest@modudrive.com"));

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.inheritedShares()).hasSize(2);
            // containsExactly: root-most-first is a contract the web layer relies on (FileAccessListResponse.shares).
            assertThat(result.inheritedShares())
                    .extracting(s -> s.source().getId(), s -> s.share().getRole())
                    .containsExactly(tuple(parentId, Role.VIEWER), tuple(nearId, Role.EDITOR));
        }

        @Test
        @DisplayName("조상 폴더에 미가입 게스트 초대가 있으면 상속 목록에 포함한다 (issue #313 — 예전엔 조용히 누락됨)")
        void includesAPendingGuestAncestorInvite() {
            FileShare pendingGuestInvite = FileShare.withId(new FileShareId(UUID.randomUUID()),
                    new FileShareFileId(parentId), new FileShareOwnerId(ownerId), null,
                    new FileShareRole(Role.VIEWER), UUID.randomUUID(), "guest@example.com", null);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of());
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of(parentDir));
            given(findFileSharePort.findByFileId(new FileId(parentId))).willReturn(List.of(pendingGuestInvite));

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.inheritedShares()).hasSize(1);
            assertThat(result.inheritedShares().get(0).share()).isEqualTo(pendingGuestInvite);
            assertThat(result.inheritedShares().get(0).source()).isEqualTo(parentDir);
            // No sharedWithUserId to look up — member-service is never consulted for a guest row.
            assertThat(result.memberSummaries()).isEmpty();
            then(findMemberByIdPort).shouldHaveNoInteractions();
        }

        @Test
        void reportsLinkSharedAncestorAsInheritedLinkSource() {
            parentDir.enableLinkSharing();
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByFileId(command.getFileId())).willReturn(List.of());
            given(fileAccessGuard.ancestorDirectories(any(File.class))).willReturn(List.of(parentDir));
            given(findFileSharePort.findByFileId(new FileId(parentId))).willReturn(List.of());

            FileSharesView result = listFileSharesService.listFileShares(command);

            assertThat(result.inheritedLinkSources()).containsExactly(parentDir);
        }
    }

    @Nested
    @DisplayName("조회 대상이 디렉토리일 때")
    class WhenTargetIsDirectory {

        private final UUID directoryId = UUID.randomUUID();
        private final UUID namespaceId = UUID.randomUUID();
        private final File directory = File.withId(new FileId(directoryId), new FileNamespaceId(namespaceId),
                new FileName("folder"), new FilePath("/"), new FileOwnerId(ownerId),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
        private final ListFileSharesCommand directoryCommand = new ListFileSharesCommand(directoryId, ownerId);

        private File descendantFile(String name) {
            return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId),
                    new FileName(name), new FilePath(directory.fullPath()), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
        }

        @Test
        @DisplayName("하위 항목 중 하나라도 공유 중이면 hasSharedDescendant=true")
        void flagsTrueWhenAnyDescendantIsShared() {
            File child = descendantFile("child.txt");
            given(findFilePort.findById(directoryCommand.getFileId())).willReturn(Optional.of(directory));
            given(findFileSharePort.findByFileId(directoryCommand.getFileId())).willReturn(List.of());
            given(fileAccessGuard.ancestorDirectories(directory)).willReturn(List.of());
            given(findFilePort.findByNamespaceIdAndPathStartingWith(any(), eq(directory.fullPath())))
                    .willReturn(List.of(child));
            given(findFileSharePort.existsByFileIdIn(List.of(new FileId(child.getId())))).willReturn(true);

            FileSharesView result = listFileSharesService.listFileShares(directoryCommand);

            assertThat(result.hasSharedDescendant()).isTrue();
        }

        @Test
        @DisplayName("하위 항목이 있어도 아무도 공유돼 있지 않으면 hasSharedDescendant=false")
        void flagsFalseWhenNoDescendantIsShared() {
            File child = descendantFile("child.txt");
            given(findFilePort.findById(directoryCommand.getFileId())).willReturn(Optional.of(directory));
            given(findFileSharePort.findByFileId(directoryCommand.getFileId())).willReturn(List.of());
            given(fileAccessGuard.ancestorDirectories(directory)).willReturn(List.of());
            given(findFilePort.findByNamespaceIdAndPathStartingWith(any(), eq(directory.fullPath())))
                    .willReturn(List.of(child));
            given(findFileSharePort.existsByFileIdIn(List.of(new FileId(child.getId())))).willReturn(false);

            FileSharesView result = listFileSharesService.listFileShares(directoryCommand);

            assertThat(result.hasSharedDescendant()).isFalse();
        }

        @Test
        @DisplayName("하위 항목이 없으면 existsByFileIdIn을 호출하지 않고 곧장 false")
        void skipsShareLookupWhenSubtreeIsEmpty() {
            given(findFilePort.findById(directoryCommand.getFileId())).willReturn(Optional.of(directory));
            given(findFileSharePort.findByFileId(directoryCommand.getFileId())).willReturn(List.of());
            given(fileAccessGuard.ancestorDirectories(directory)).willReturn(List.of());
            given(findFilePort.findByNamespaceIdAndPathStartingWith(any(), eq(directory.fullPath())))
                    .willReturn(List.of());

            FileSharesView result = listFileSharesService.listFileShares(directoryCommand);

            assertThat(result.hasSharedDescendant()).isFalse();
            then(findFileSharePort).should(org.mockito.Mockito.never()).existsByFileIdIn(any());
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
