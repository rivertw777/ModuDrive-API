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
    /** Null for a pending guest share (see {@link #createPending}) until {@link #claim}. */
    private UUID sharedWithUserId;
    private Role role;
    /** Per-invite capability token of a guest share, revoked only by deleting the row. Kept after
     * {@link #claim} so the emailed link still resolves. Null for a share made directly for a
     * member. */
    private UUID token;
    /** The invited address, only while a guest share is unclaimed; cleared by {@link #claim}. */
    private String granteeEmail;
    /** "Shared on" date, set by JPA auditing; null until persisted. */
    private LocalDateTime createdAt;

    public static FileShare create(FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role) {
        return new FileShare(null, fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value(),
                null, null, null);
    }

    /** A share for an email nobody has registered yet, with its own {@code token}, so revoking
     * it touches no other grant. */
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

    /** Every column, including a guest share's — what the persistence mapper reads back. */
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

    /** Links a pending guest share to the member who signed up with its email. Keeps
     * {@code token} so the emailed {@code /files/{fileId}?key=} link keeps working; "still
     * unclaimed" is {@code sharedWithUserId == null}. */
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
