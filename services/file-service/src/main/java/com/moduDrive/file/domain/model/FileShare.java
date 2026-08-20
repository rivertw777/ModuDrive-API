package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileShare {

    private final UUID id;
    private final UUID fileId;
    private final UUID ownerId;
    /** Null for a pending guest share (see {@link #createPending}) — there is no member to point
     * at yet, only an invited email. Filled in later by {@link #claim} once that email signs up. */
    private UUID sharedWithUserId;
    private Role role;
    /** Non-null only for a pending guest share: the one-time capability token that stands in for
     * membership, minted per invite so each guest can be revoked without touching any other
     * share or the file's own {@code linkToken}. Null for a real member grant, including a
     * claimed one — see {@link #claim}. */
    private UUID token;
    /** Non-null only for a pending guest share — the invited address, kept so the owner's share
     * list can display it without a member-service lookup. Null for a real member grant, including
     * a claimed one — see {@link #claim}. */
    private String granteeEmail;

    public static FileShare create(FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role) {
        return new FileShare(null, fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value(), null, null);
    }

    /** A restricted share for an email nobody has registered yet. Kept as {@code RESTRICTED},
     * unlike the old fallback that reused the file's "anyone with the link" mechanism: this
     * grantee gets their own {@code token}, so nobody else who obtains it gets in, and revoking
     * this one invite never touches anyone else's access. */
    public static FileShare createPending(FileShareFileId fileId,
                                          FileShareOwnerId ownerId,
                                          FileShareGranteeEmail granteeEmail,
                                          FileShareRole role) {
        return new FileShare(null, fileId.value(), ownerId.value(), null, role.value(),
                UUID.randomUUID(), granteeEmail.value());
    }

    public static FileShare withId(FileShareId id,
                                   FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role) {
        return new FileShare(id.value(), fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value(), null, null);
    }

    /** Same as {@link #withId(FileShareId, FileShareFileId, FileShareOwnerId,
     * FileShareSharedWithUserId, FileShareRole)}, plus the pending-guest-share columns — used only
     * by the persistence mapper, which is the one caller that ever reads a guest row back out. */
    public static FileShare withId(FileShareId id,
                            FileShareFileId fileId,
                            FileShareOwnerId ownerId,
                            FileShareSharedWithUserId sharedWithUserId,
                            FileShareRole role,
                            UUID token,
                            String granteeEmail) {
        return new FileShare(id.value(), fileId.value(), ownerId.value(),
                sharedWithUserId != null ? sharedWithUserId.value() : null, role.value(), token, granteeEmail);
    }

    public void changeRole(FileShareRole role) {
        this.role = role.value();
    }

    /** Links a pending guest share to the member who just signed up with its {@code granteeEmail}.
     * Clears {@code token}/{@code granteeEmail} so the row becomes indistinguishable from a normal
     * member grant: {@code UpdateFileScopeService.revokePendingGuestShares} deletes any share whose
     * {@code token} is still non-null when sharing is turned off, and a claimed share must survive
     * that the same way a directly-invited member share does. */
    public void claim(UUID memberId) {
        this.sharedWithUserId = memberId;
        this.token = null;
        this.granteeEmail = null;
    }

    public record FileShareId(UUID value) {}
    public record FileShareFileId(UUID value) {}
    public record FileShareOwnerId(UUID value) {}
    public record FileShareSharedWithUserId(UUID value) {}
    public record FileShareGranteeEmail(String value) {}
    public record FileShareRole(Role value) {}
}
