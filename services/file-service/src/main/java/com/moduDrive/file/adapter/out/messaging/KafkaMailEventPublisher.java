package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.file.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class KafkaMailEventPublisher implements PublishMailEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishShareInviteRequested(
            UUID fileId, String granteeEmail, String fileName, boolean directory, String category, String role,
            String granterName, String granterEmail, String message, UUID inviteToken) {
        kafkaTemplate.send(MailTopics.SHARE_INVITE_REQUESTED, fileId.toString(),
                        new ShareInviteMailRequested(fileId, granteeEmail, fileName, directory, category, role,
                                granterName, granterEmail, message, inviteToken))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish share invite mail event: fileId={}", fileId, ex);
                    }
                });
    }
}
