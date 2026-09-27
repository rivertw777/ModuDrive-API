package com.moduDrive.common.event.member;

import java.util.UUID;

/** Published by member-service (queue {@link MemberQueues#PASSWORD_CHANGED}) in the transaction that
 * changes a member's password. auth-service ends every session of the member and forgets their
 * verified devices (auth spec 004), so a leaked old password stops working everywhere. */
public record MemberPasswordChanged(UUID memberId) {
}
