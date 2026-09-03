package com.moduDrive.file.application.event;

import com.moduDrive.file.application.port.out.PublishMailEventPort;
import com.moduDrive.file.application.port.out.PublishNotificationEventPort;
import com.moduDrive.file.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FileShareInvitedEventListenerTest {

    @Mock
    private PublishMailEventPort publishMailEventPort;
    @Mock
    private PublishNotificationEventPort publishNotificationEventPort;
    @InjectMocks
    private FileShareInvitedEventListener fileShareInvitedEventListener;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID GRANTER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("가입된 회원에게 공유했을 때")
    class WhenGranteeIsRegisteredMember {

        @Test
        void publishesBothMailAndNotificationEvents() {
            UUID granteeId = UUID.randomUUID();
            FileShareInvitedEvent event = new FileShareInvitedEvent(
                    FILE_ID, GRANTER_ID, "홍길동", "owner@modudrive.com", granteeId, "grantee@modudrive.com",
                    "report.pdf", true, Role.EDITOR, null);

            fileShareInvitedEventListener.onFileShareInvited(event);

            then(publishMailEventPort).should().publishShareInviteRequested(
                    FILE_ID, "grantee@modudrive.com", "report.pdf", "EDITOR", null);
            then(publishNotificationEventPort).should().publishFileShared(
                    FILE_ID, granteeId, "report.pdf", "EDITOR", true, "홍길동", "owner@modudrive.com");
        }
    }

    @Nested
    @DisplayName("가입되지 않은 이메일(게스트)에게 공유했을 때")
    class WhenGranteeIsGuest {

        @Test
        void publishesOnlyTheMailEvent() {
            UUID linkToken = UUID.randomUUID();
            FileShareInvitedEvent event = new FileShareInvitedEvent(
                    FILE_ID, GRANTER_ID, "홍길동", "owner@modudrive.com", null, "guest@modudrive.com",
                    "report.pdf", false, Role.VIEWER, linkToken);

            fileShareInvitedEventListener.onFileShareInvited(event);

            then(publishMailEventPort).should().publishShareInviteRequested(
                    FILE_ID, "guest@modudrive.com", "report.pdf", "VIEWER", linkToken);
            then(publishNotificationEventPort).shouldHaveNoInteractions();
        }
    }
}
