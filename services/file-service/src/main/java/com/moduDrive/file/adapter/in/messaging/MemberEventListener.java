package com.moduDrive.file.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import com.moduDrive.common.infrastructure.sqs.SqsQueues;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

@EventListener
@RequiredArgsConstructor
class MemberEventListener {

    private final ClaimPendingFileSharesUseCase claimPendingFileSharesUseCase;
    private final ProcessedEvents processedEvents;

    /** @Transactional so the "already handled" record commits with the claimed shares: if the claim
     * fails, both are rolled back and the retry starts over. */
    @Transactional
    @SqsListener(MemberQueues.SIGNED_UP + SqsQueues.FIFO_SUFFIX)
    void onMemberSignedUp(MemberSignedUp event,
                          @Header(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER) String deduplicationId) {
        if (processedEvents.isProcessed(MemberQueues.SIGNED_UP, deduplicationId)) {
            return;
        }
        claimPendingFileSharesUseCase.claimPendingFileShares(
                new ClaimPendingFileSharesCommand(event.memberId(), event.email()));
        processedEvents.markProcessed(MemberQueues.SIGNED_UP, deduplicationId);
    }
}
