package com.moduDrive.file.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;

@EventListener
@RequiredArgsConstructor
class MemberEventListener {

    private final ClaimPendingFileSharesUseCase claimPendingFileSharesUseCase;

    @SqsListener(MemberQueues.SIGNED_UP)
    void onMemberSignedUp(MemberSignedUp event) {
        claimPendingFileSharesUseCase.claimPendingFileShares(
                new ClaimPendingFileSharesCommand(event.memberId(), event.email()));
    }
}
