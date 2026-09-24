package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
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
    /** The per-invite capability token, minted per guest so each can be revoked independently —
     * by removing the whole row (see {@code RevokeFileShareService}), never just this field; link
     * sharing (a separate, independent setting — issue #303) never touches it either. Kept alive
     * after {@link #claim} so the emailed link still resolves once they sign up. Null for a share
     * created directly for an existing member, and for any row read back through a {@code withId}
     * overload other than the persistence mapper's. */
    private UUID token;
    /** Non-null only while a guest share is still unclaimed — the invited address, kept so the
     * owner's share list can display it without a member-service lookup. Cleared by
     * {@link #claim} (the row is a real member grant from then on) and null for a direct member
     * share. */
    private String granteeEmail;
    /** Null until the row is persisted — filled in by JPA auditing. "Shared on" date. */
    private LocalDateTime createdAt;

    public static FileShare create(FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role) {
        return new FileShare(null, fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value(),
                null, null, null);
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
                UUID.randomUUID(), granteeEmail.value(), null);
    }

    public static FileShare withId(FileShareId id,
                                   FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role) {
        return new FileShare(id.value(), fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value(),
                null, null, null);
    }

    /** As {@link #withId(FileShareId, FileShareFileId, FileShareOwnerId, FileShareSharedWithUserId,
     * FileShareRole)}, plus the "shared on" date — for lists that show when a grant was made. */
    public static FileShare withId(FileShareId id,
                                   FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role,
                                   LocalDateTime createdAt) {
        return new FileShare(id.value(), fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value(),
                null, null, createdAt);
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
                            String granteeEmail,
                            LocalDateTime createdAt) {
        return new FileShare(id.value(), fileId.value(), ownerId.value(),
                sharedWithUserId != null ? sharedWithUserId.value() : null, role.value(), token, granteeEmail, createdAt);
    }

    public void changeRole(FileShareRole role) {
        this.role = role.value();
    }

    /** Links a pending guest share to the member who just signed up with its {@code granteeEmail}.
     * Clears {@code granteeEmail} (there is a real member behind it now) but keeps {@code token}
     * so the {@code /files/{fileId}?key=} link the guest was emailed keeps working. "Still
     * unclaimed" is therefore {@code sharedWithUserId == null}. */
    public void claim(UUID memberId) {
        this.sharedWithUserId = memberId;
        this.granteeEmail = null;
    }

    public record FileShareId(UUID value) {}
    public record FileShareFileId(UUID value) {}
    public record FileShareOwnerId(UUID value) {}
    public record FileShareSharedWithUserId(UUID value) {}
    public record FileShareGranteeEmail(String value) {}
    public record FileShareRole(Role value) {}
}
