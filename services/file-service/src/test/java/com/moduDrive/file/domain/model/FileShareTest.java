package com.moduDrive.file.domain.model;

import com.moduDrive.file.domain.model.FileShare.FileShareFileId;
import com.moduDrive.file.domain.model.FileShare.FileShareGranteeEmail;
import com.moduDrive.file.domain.model.FileShare.FileShareOwnerId;
import com.moduDrive.file.domain.model.FileShare.FileShareRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileShareTest {

    @Nested
    @DisplayName("대기 중인 게스트 공유를 클레임할 때")
    class WhenClaimingAPendingShare {

        @Test
        void fillsSharedWithUserIdAndClearsTokenAndEmail() {
            FileShare pending = FileShare.createPending(
                    new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("river@modudrive.com"), new FileShareRole(Role.VIEWER));
            UUID memberId = UUID.randomUUID();

            pending.claim(memberId);

            assertThat(pending.getSharedWithUserId()).isEqualTo(memberId);
            // Cleared, not kept: a claimed share must read as an ordinary member grant everywhere
            // token/granteeEmail non-null used to mean "still a pending guest share" (see
            // UpdateFileScopeService.revokePendingGuestShares).
            assertThat(pending.getToken()).isNull();
            assertThat(pending.getGranteeEmail()).isNull();
        }
    }
}
