package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
class RevokeFileShareServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private DeleteFileSharePort deleteFileSharePort;
    @Mock private FindMemberByIdPort findMemberByIdPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private RevokeFileShareService revokeFileShareService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID granteeId = UUID.randomUUID();
    private final RevokeFileShareCommand command = new RevokeFileShareCommand(fileId, shareId, ownerId);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    private FileShare share(UUID belongsToFileId) {
        return share(belongsToFileId, Role.VIEWER);
    }

    private FileShare share(UUID belongsToFileId, Role role) {
        return share(belongsToFileId, role, granteeId);
    }

    private FileShare share(UUID belongsToFileId, Role role, UUID grantee) {
        return FileShare.withId(new FileShareId(shareId), new FileShareFileId(belongsToFileId),
                new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(grantee),
                new FileShareRole(role));
    }

    @Nested
    @DisplayName("소유자가 자기 파일의 공유를 해제할 때")
    class WhenOwnerRevokesOwnFileShare {

        @Test
        void deletesShare() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of());

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(shareId));
        }
    }

    @Nested
    @DisplayName("같은 사람이 조상 폴더에도 공유를 가지고 있을 때")
    class WhenTheSameGranteeAlsoHasAnAncestorGrant {

        private File directory(String name, String path) {
            return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName(name), new FilePath(path), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
        }

        private FileShare grantOn(UUID onFileId, UUID grantShareId, Role role) {
            return FileShare.withId(new FileShareId(grantShareId), new FileShareFileId(onFileId),
                    new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(granteeId),
                    new FileShareRole(role));
        }

        @Test
        void deletesTheAncestorGrantTooSoTheGranteeIsNotPromoted() {
            // Issue #310: dropping only the direct VIEWER grant would uncover the parent's EDITOR one and give the grantee more access.
            File parent = directory("projects", "/");
            UUID parentShareId = UUID.randomUUID();
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId()))
                    .willReturn(Optional.of(share(fileId, Role.VIEWER)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.of(grantOn(parent.getId(), parentShareId, Role.EDITOR)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(shareId));
            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(parentShareId));
            // Every ancestor was found by id, so the email fallback never has anything to resolve.
            then(findMemberByIdPort).shouldHaveNoInteractions();
        }

        @Test
        void deletesEveryAncestorGrantNotJustTheNearest() {
            // One surviving ancestor re-grants access on its own, so the walk can't stop at the first hit.
            File grandParent = directory("work", "/");
            File parent = directory("projects", "/work");
            UUID grandParentShareId = UUID.randomUUID();
            UUID parentShareId = UUID.randomUUID();
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(grandParent, parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(grandParent.getId()), granteeId))
                    .willReturn(Optional.of(grantOn(grandParent.getId(), grandParentShareId, Role.VIEWER)));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.of(grantOn(parent.getId(), parentShareId, Role.EDITOR)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(grandParentShareId));
            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(parentShareId));
            then(findMemberByIdPort).shouldHaveNoInteractions();
        }

        @Test
        void deletesAPendingGuestsAncestorInviteByEmail() {
            // An unclaimed guest invite has no member id; only the invited email identifies them, so the lookup must use that column.
            File parent = directory("projects", "/");
            UUID parentShareId = UUID.randomUUID();
            FileShare pendingGuestShare = FileShare.withId(new FileShareId(shareId),
                    new FileShareFileId(fileId), new FileShareOwnerId(ownerId), null,
                    new FileShareRole(Role.VIEWER), UUID.randomUUID(), "guest@example.com", null);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(pendingGuestShare));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndGranteeEmail(new FileId(parent.getId()), "guest@example.com"))
                    .willReturn(Optional.of(FileShare.withId(new FileShareId(parentShareId),
                            new FileShareFileId(parent.getId()), new FileShareOwnerId(ownerId), null,
                            new FileShareRole(Role.EDITOR), UUID.randomUUID(), "guest@example.com", null)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(parentShareId));
            // A pending-row revoke has no member id; the member-service fallback is only reached from a member-id row.
            then(findMemberByIdPort).shouldHaveNoInteractions();
        }

        @Test
        void fallsBackToTheEmailWhenTheAncestorInviteWasNeverClaimed() {
            // The direct grant names a member (claim() clears the email column), but the ancestor's invite for the same
            // person is still an unclaimed, email-only row (the claim never went through, e.g. a lost MemberSignedUp event or
            // ClaimPendingFileSharesService.ownsEmail rejecting it). Lookup by member id finds nothing, so the fallback
            // resolves the person's email via member-service.
            File parent = directory("projects", "/");
            UUID parentShareId = UUID.randomUUID();
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.empty());
            given(findMemberByIdPort.findMemberByIdOrUnknown(granteeId))
                    .willReturn(new MemberSummary("guest", "guest@example.com"));
            given(findFileSharePort.findByFileIdAndGranteeEmail(new FileId(parent.getId()), "guest@example.com"))
                    .willReturn(Optional.of(FileShare.withId(new FileShareId(parentShareId),
                            new FileShareFileId(parent.getId()), new FileShareOwnerId(ownerId), null,
                            new FileShareRole(Role.EDITOR), UUID.randomUUID(), "guest@example.com", null)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(parentShareId));
        }

        @Test
        @DisplayName("member-service 조회가 실패해도 revoke 자체는 성공한다 (email fallback만 못 탐)")
        void stillRevokesWhenMemberLookupFails() {
            File parent = directory("projects", "/");
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.empty());
            given(findMemberByIdPort.findMemberByIdOrUnknown(granteeId)).willReturn(FindMemberByIdPort.UNKNOWN_MEMBER);

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should(times(1)).deleteFileShare(any(FileShareId.class));
        }

        @Test
        @DisplayName("조상 여러 개가 아무것도 주지 않아도 member-service는 revoke당 최대 1번만 부른다 (조상 수만큼 왕복하면 안 됨)")
        void deletesNothingExtraWhenNoAncestorGrantsThemAnythingAndOnlyResolvesEmailOnce() {
            File grandParent = directory("work", "/");
            File parent = directory("projects", "/work");
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(grandParent, parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(grandParent.getId()), granteeId))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.empty());
            given(findMemberByIdPort.findMemberByIdOrUnknown(granteeId))
                    .willReturn(new MemberSummary("river", "river@example.com"));
            given(findFileSharePort.findByFileIdAndGranteeEmail(new FileId(grandParent.getId()), "river@example.com"))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileIdAndGranteeEmail(new FileId(parent.getId()), "river@example.com"))
                    .willReturn(Optional.empty());

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should(times(1)).deleteFileShare(any(FileShareId.class));
            then(findMemberByIdPort).should(times(1)).findMemberByIdOrUnknown(granteeId);
        }
    }

    // Lenient: the grantee-blind listing a regression would reach for must be stubbed to be observable, and correct code never calls it.
    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("조상 폴더가 다른 사람에게만 공유되어 있을 때")
    class WhenAnAncestorGrantsSomeoneElse {

        @Test
        void leavesTheOtherGranteesAncestorGrantAlone() {
            // Spec 5: rows are independent per grantee. Revoking one person must not clear every share on the folders above.
            File parent = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("projects"), new FilePath("/"), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
            UUID someoneElsesShareId = UUID.randomUUID();
            FileShare someoneElsesGrant = FileShare.withId(new FileShareId(someoneElsesShareId),
                    new FileShareFileId(parent.getId()), new FileShareOwnerId(ownerId),
                    new FileShareSharedWithUserId(UUID.randomUUID()), new FileShareRole(Role.EDITOR));
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.empty());
            given(findMemberByIdPort.findMemberByIdOrUnknown(granteeId))
                    .willReturn(new MemberSummary("river", "river@example.com"));
            given(findFileSharePort.findByFileIdAndGranteeEmail(new FileId(parent.getId()), "river@example.com"))
                    .willReturn(Optional.empty());
            given(findFileSharePort.findByFileId(new FileId(parent.getId())))
                    .willReturn(List.of(someoneElsesGrant));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should(never()).deleteFileShare(new FileShareId(someoneElsesShareId));
            then(deleteFileSharePort).should(times(1)).deleteFileShare(any(FileShareId.class));
        }
    }

    @Nested
    @DisplayName("이미 해제된 공유를 다시 해제 요청할 때")
    class WhenTheShareIsAlreadyGone {

        @Test
        void succeedsWithoutThrowing() {
            // A concurrent revoke (e.g. a client firing the direct and ancestor revokes together) may already have
            // removed the row; that must not surface as an error.
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.empty());

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("공유가 다른 파일에 속할 때")
    class WhenShareBelongsToAnotherFile {

        @Test
        void throwsFileShareNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId()))
                    .willReturn(Optional.of(share(UUID.randomUUID())));

            Throwable thrown = catchThrowable(() -> revokeFileShareService.revokeFileShare(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_NOT_FOUND);
            then(deleteFileSharePort).shouldHaveNoInteractions();
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

            Throwable thrown = catchThrowable(() -> revokeFileShareService.revokeFileShare(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(deleteFileSharePort).shouldHaveNoInteractions();
        }
    }
}
