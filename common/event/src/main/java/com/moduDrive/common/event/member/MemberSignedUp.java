package com.moduDrive.common.event.member;

import java.util.UUID;

/** Published by member-service (queue {@link MemberQueues#SIGNED_UP}) once the member row exists.
 * file-service creates the member's namespace and links pending guest shares for this email. */
public record MemberSignedUp(UUID memberId, String email) {
}
