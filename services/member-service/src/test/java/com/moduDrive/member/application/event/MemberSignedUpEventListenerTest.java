package com.moduDrive.member.application.event;

import com.moduDrive.member.application.port.out.CreateNamespacePort;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
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
class MemberSignedUpEventListenerTest {

    @Mock private CreateNamespacePort createNamespacePort;
    @Mock private PublishMemberEventPort publishMemberEventPort;
    @InjectMocks private MemberSignedUpEventListener listener;

    private final UUID memberId = UUID.randomUUID();
    private final String email = "river@modudrive.com";

    @Nested
    @DisplayName("회원가입 커밋 직전 이벤트를 받으면")
    class WhenHandlingBeforeCommit {

        @Test
        void publishesSignedUpWithoutCreatingTheNamespace() {
            listener.publishSignedUp(new MemberSignedUpEvent(memberId, email));

            then(publishMemberEventPort).should().publishSignedUp(memberId, email);
            then(createNamespacePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("회원가입 커밋 후 이벤트를 받으면")
    class WhenHandlingAfterCommit {

        @Test
        void createsTheNamespaceWithoutPublishing() {
            listener.createNamespace(new MemberSignedUpEvent(memberId, email));

            then(createNamespacePort).should().createNamespace(memberId);
            then(publishMemberEventPort).shouldHaveNoInteractions();
        }
    }
}
