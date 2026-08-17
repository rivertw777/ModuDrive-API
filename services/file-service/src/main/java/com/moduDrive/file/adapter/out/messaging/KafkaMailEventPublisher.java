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
    public void publishShareInviteRequested(UUID fileId, String granteeEmail, String fileName, String role, UUID linkToken) {
        kafkaTemplate.send(MailTopics.SHARE_INVITE_REQUESTED, fileId.toString(),
                        new ShareInviteMailRequested(fileId, granteeEmail, fileName, role, linkToken))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish share invite mail event: fileId={}", fileId, ex);
                    }
                });
    }
}
