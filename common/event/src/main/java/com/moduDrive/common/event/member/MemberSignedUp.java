package com.moduDrive.common.event.member;

import java.util.UUID;

/** Published by member-service (queue {@link MemberDestinations#SIGNED_UP}) once a member row and
 * namespace exist. Lets other services claim anything that was waiting on this email — e.g.
 * file-service auto-linking a pending guest share to the new member. */
public record MemberSignedUp(UUID memberId, String email) {
}
