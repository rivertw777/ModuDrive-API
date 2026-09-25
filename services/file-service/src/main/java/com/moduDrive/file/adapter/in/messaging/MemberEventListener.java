package com.moduDrive.file.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import com.moduDrive.file.application.port.in.usecase.CreateNamespaceUseCase;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.common.infrastructure.sqs.SqsAttributes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

@EventListener
@RequiredArgsConstructor
class MemberEventListener {

    private final CreateNamespaceUseCase createNamespaceUseCase;
    private final ClaimPendingFileSharesUseCase claimPendingFileSharesUseCase;
    private final ProcessedEvents processedEvents;

    /** @Transactional so the "already handled" record commits with the claimed shares: if the claim
     * fails, both are rolled back and the retry starts over. The namespace comes first and commits
     * in its own transaction (REQUIRES_NEW), so a failed claim — member-service down — never leaves
     * the new member without a drive; the retry finds it and only redoes the claim (#424). */
    @Transactional
    @SqsListener(MemberQueues.SIGNED_UP)
    void onMemberSignedUp(MemberSignedUp event,
                          @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        if (!processedEvents.claim(MemberQueues.SIGNED_UP, deduplicationId)) {
            return;
        }
        createNamespaceUseCase.createNamespace(new CreateNamespaceCommand(new NamespaceUserId(event.memberId())));
        claimPendingFileSharesUseCase.claimPendingFileShares(
                new ClaimPendingFileSharesCommand(event.memberId(), event.email()));
        processedEvents.markProcessed(MemberQueues.SIGNED_UP, deduplicationId);
    }
}
