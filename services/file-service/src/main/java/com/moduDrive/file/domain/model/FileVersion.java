package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileVersion {

    private final UUID id;
    private final UUID fileId;
    private final Long fileSize;
    private final int blockCount;
    private final String s3Path;

    public static FileVersion create(FileVersionFileId fileId,
                                     FileVersionFileSize fileSize,
                                     FileVersionBlockCount blockCount,
                                     FileVersionS3Path s3Path) {
        return new FileVersion(null, fileId.value(), fileSize.value(), blockCount.value(), s3Path.value());
    }

    public static FileVersion withId(FileVersionId id,
                                     FileVersionFileId fileId,
                                     FileVersionFileSize fileSize,
                                     FileVersionBlockCount blockCount,
                                     FileVersionS3Path s3Path) {
        return new FileVersion(id.value(), fileId.value(), fileSize.value(), blockCount.value(), s3Path.value());
    }

    public record FileVersionId(UUID value) {}
    public record FileVersionFileId(UUID value) {}
    public record FileVersionFileSize(Long value) {}
    public record FileVersionBlockCount(int value) {}
    public record FileVersionS3Path(String value) {}
}
