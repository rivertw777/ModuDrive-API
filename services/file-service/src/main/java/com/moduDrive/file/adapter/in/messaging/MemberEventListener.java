package com.moduDrive.file.adapter.in.messaging;

import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.event.member.MemberTopics;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MemberEventListener {

    private final ClaimPendingFileSharesUseCase claimPendingFileSharesUseCase;

    @KafkaListener(topics = MemberTopics.SIGNED_UP)
    void onMemberSignedUp(MemberSignedUp event) {
        claimPendingFileSharesUseCase.claimPendingFileShares(
                new ClaimPendingFileSharesCommand(event.memberId(), event.email()));
    }
}
