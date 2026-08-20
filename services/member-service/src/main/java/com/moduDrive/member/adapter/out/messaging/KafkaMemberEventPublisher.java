package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.event.member.MemberTopics;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class KafkaMemberEventPublisher implements PublishMemberEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishSignedUp(UUID memberId, String email) {
        kafkaTemplate.send(MemberTopics.SIGNED_UP, email, new MemberSignedUp(memberId, email))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish member signed up event: memberId={}", memberId, ex);
                    }
                });
    }
}
