package com.moduDrive.file.fixture;

import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.FileVersionFileId;
import com.moduDrive.file.domain.model.FileVersion.FileVersionFileSize;
import com.moduDrive.file.domain.model.FileVersion.FileVersionHashes;
import com.moduDrive.file.domain.model.FileVersion.FileVersionId;
import com.moduDrive.file.domain.model.FileVersion.FileVersionOwnerId;
import com.moduDrive.file.domain.model.FileVersion.FileVersionUploadId;

import java.util.List;
import java.util.UUID;

public final class FileVersionTestFixture {

    public static final String HASH_A = "a".repeat(64);
    public static final String HASH_B = "b".repeat(64);
    public static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private FileVersionTestFixture() {
    }

    /** A one-block version of {@code fileId}, owned by {@link #OWNER_ID}. */
    public static FileVersion aVersion(UUID id, UUID fileId, long fileSize) {
        return aVersion(id, fileId, fileSize, List.of(HASH_A));
    }

    public static FileVersion aVersion(UUID id, UUID fileId, long fileSize, List<String> hashes) {
        return FileVersion.withId(new FileVersionId(id), new FileVersionFileId(fileId),
                new FileVersionOwnerId(OWNER_ID), new FileVersionFileSize(fileSize),
                new FileVersionUploadId(UUID.randomUUID()), new FileVersionHashes(hashes));
    }
}
