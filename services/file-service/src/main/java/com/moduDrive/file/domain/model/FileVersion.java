package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** One version of a file: the ordered SHA-256 hashes of its 4MB blocks (the blocklist). The blocks
 * themselves live at {@code blocks/{ownerId}/{hash}} and are shared by every version, of any of the
 * owner's files, that has the same bytes. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileVersion {

    // ponytail: must equal storage-service's storage.block-size (and WEB's CHUNK_SIZE) — the
    // blocklist's shape is checked against it. Make it shared config if it ever needs to change.
    public static final int BLOCK_SIZE = 4 * 1024 * 1024;

    private final UUID id;
    private final UUID fileId;
    private final UUID ownerId;
    private final Long fileSize;
    private final UUID uploadId;
    private final List<String> hashes;

    public static FileVersion create(FileVersionFileId fileId,
                                     FileVersionOwnerId ownerId,
                                     FileVersionFileSize fileSize,
                                     FileVersionUploadId uploadId,
                                     FileVersionHashes hashes) {
        return new FileVersion(null, fileId.value(), ownerId.value(), fileSize.value(), uploadId.value(),
                List.copyOf(hashes.value()));
    }

    public static FileVersion withId(FileVersionId id,
                                     FileVersionFileId fileId,
                                     FileVersionOwnerId ownerId,
                                     FileVersionFileSize fileSize,
                                     FileVersionUploadId uploadId,
                                     FileVersionHashes hashes) {
        return new FileVersion(id.value(), fileId.value(), ownerId.value(), fileSize.value(), uploadId.value(),
                List.copyOf(hashes.value()));
    }

    /** How many blocks a file of {@code size} bytes splits into — 0 for an empty file. */
    public static long blockCountFor(long size) {
        return (size + BLOCK_SIZE - 1) / BLOCK_SIZE;
    }

    public record FileVersionId(UUID value) {}
    public record FileVersionFileId(UUID value) {}
    public record FileVersionOwnerId(UUID value) {}
    public record FileVersionFileSize(Long value) {}
    /** Picked by the client per upload attempt, so a commit whose response was lost can be sent
     * again and get back the version it already made. */
    public record FileVersionUploadId(UUID value) {}
    public record FileVersionHashes(List<String> value) {}
}
