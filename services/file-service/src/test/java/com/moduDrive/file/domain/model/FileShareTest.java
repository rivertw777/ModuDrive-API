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
        void fillsSharedWithUserIdAndClearsEmailButKeepsToken() {
            FileShare pending = FileShare.createPending(
                    new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("river@modudrive.com"), new FileShareRole(Role.VIEWER));
            UUID token = pending.getToken();
            UUID memberId = UUID.randomUUID();

            pending.claim(memberId);

            assertThat(pending.getSharedWithUserId()).isEqualTo(memberId);
            // granteeEmail is cleared (there's a real member behind it now), but the token is
            // kept so the /public/{fileId}?key= link the guest was emailed keeps working. "Still
            // pending" is now sharedWithUserId == null (see UpdateFileScopeService).
            assertThat(pending.getToken()).isEqualTo(token);
            assertThat(pending.getGranteeEmail()).isNull();
        }
    }
}
