package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.common.infrastructure.outbox.OutboxEventRecorder;
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
class KafkaMailEventPublisherTest {

    @Mock
    private OutboxEventRecorder outboxEventRecorder;
    @InjectMocks
    private KafkaMailEventPublisher kafkaMailEventPublisher;

    @Nested
    @DisplayName("파일 공유 초대 메일 이벤트를 발행할 때")
    class WhenPublishingShareInviteRequested {

        @Test
        void sendsPayloadToShareInviteTopicKeyedByFileId() {
            UUID fileId = UUID.randomUUID();

            kafkaMailEventPublisher.publishShareInviteRequested(
                    fileId, "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", "홍길동",
                    "owner@modudrive.com", "확인 부탁드려요", null);

            then(outboxEventRecorder).should().record(
                    MailTopics.SHARE_INVITE_REQUESTED, fileId.toString(),
                    new ShareInviteMailRequested(fileId, "grantee@modudrive.com", "report.pdf", false, "DOCUMENT",
                            "VIEWER", "홍길동", "owner@modudrive.com", "확인 부탁드려요", null));
        }

        @Test
        void includesTheInviteTokenForAGuestInvite() {
            UUID fileId = UUID.randomUUID();
            UUID inviteToken = UUID.randomUUID();

            kafkaMailEventPublisher.publishShareInviteRequested(
                    fileId, "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", "홍길동",
                    "owner@modudrive.com", null, inviteToken);

            then(outboxEventRecorder).should().record(
                    MailTopics.SHARE_INVITE_REQUESTED, fileId.toString(),
                    new ShareInviteMailRequested(fileId, "grantee@modudrive.com", "report.pdf", false, "DOCUMENT",
                            "VIEWER", "홍길동", "owner@modudrive.com", null, inviteToken));
        }
    }
}
