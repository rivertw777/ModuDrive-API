package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareFileId;
import com.moduDrive.file.domain.model.FileShare.FileShareGranteeEmail;
import com.moduDrive.file.domain.model.FileShare.FileShareOwnerId;
import com.moduDrive.file.domain.model.FileShare.FileShareRole;
import com.moduDrive.file.domain.model.Role;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ClaimPendingFileSharesServiceTest {

    @Mock private FindFileSharePort findFileSharePort;
    @Mock private SaveFileSharePort saveFileSharePort;
    @Mock private FindMemberByEmailPort findMemberByEmailPort;
    @InjectMocks private ClaimPendingFileSharesService claimPendingFileSharesService;

    private static final String EMAIL = "river@modudrive.com";
    private final UUID memberId = UUID.randomUUID();
    private final ClaimPendingFileSharesCommand command = new ClaimPendingFileSharesCommand(memberId, EMAIL);

    private FileShare aPendingShare() {
        return FileShare.createPending(
                new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(UUID.randomUUID()),
                new FileShareGranteeEmail(EMAIL), new FileShareRole(Role.VIEWER));
    }

    @Nested
    @DisplayName("이메일을 실제로 그 회원이 소유하고 있을 때")
    class WhenMemberOwnsTheEmail {

        @Test
        void claimsAndSavesEachPendingShare() {
            FileShare pending = aPendingShare();
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.of(memberId));
            given(findFileSharePort.findPendingByGranteeEmail(EMAIL)).willReturn(List.of(pending));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(
                    new FileId(pending.getFileId()), memberId)).willReturn(false);

            claimPendingFileSharesService.claimPendingFileShares(command);

            then(saveFileSharePort).should().saveFileShare(pending);
            assertThat(pending.getSharedWithUserId()).isEqualTo(memberId);
            assertThat(pending.getToken()).isNull();
            assertThat(pending.getGranteeEmail()).isNull();
        }

        @Test
        void doesNothingWhenNoPendingSharesExist() {
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.of(memberId));
            given(findFileSharePort.findPendingByGranteeEmail(EMAIL)).willReturn(List.of());

            claimPendingFileSharesService.claimPendingFileShares(command);

            then(saveFileSharePort).shouldHaveNoInteractions();
        }

        @Test
        void skipsAShareThatWouldCollideWithAnExistingGrantButClaimsTheRest() {
            FileShare colliding = aPendingShare();
            FileShare claimable = aPendingShare();
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.of(memberId));
            given(findFileSharePort.findPendingByGranteeEmail(EMAIL)).willReturn(List.of(colliding, claimable));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(
                    new FileId(colliding.getFileId()), memberId)).willReturn(true);
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(
                    new FileId(claimable.getFileId()), memberId)).willReturn(false);

            claimPendingFileSharesService.claimPendingFileShares(command);

            then(saveFileSharePort).should().saveFileShare(claimable);
            then(saveFileSharePort).shouldHaveNoMoreInteractions();
            assertThat(colliding.getSharedWithUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("이벤트의 memberId가 실제로는 그 이메일의 소유자가 아닐 때")
    class WhenMemberDoesNotOwnTheEmail {

        @Test
        void rejectsTheClaimWithoutTouchingAnyShare() {
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.of(UUID.randomUUID()));

            claimPendingFileSharesService.claimPendingFileShares(command);

            then(findFileSharePort).shouldHaveNoInteractions();
            then(saveFileSharePort).shouldHaveNoInteractions();
        }

        @Test
        void rejectsTheClaimWhenNoMemberOwnsTheEmailAtAll() {
            given(findMemberByEmailPort.findMemberIdByEmail(EMAIL)).willReturn(Optional.empty());

            claimPendingFileSharesService.claimPendingFileShares(command);

            then(findFileSharePort).shouldHaveNoInteractions();
            then(saveFileSharePort).shouldHaveNoInteractions();
        }
    }
}
