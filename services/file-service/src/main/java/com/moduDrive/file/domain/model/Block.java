package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Block {

    private final UUID id;
    private final UUID versionId;
    private final int blockOrder;
    private final String blockHash;
    private final int blockSize;
    private final String s3Key;

    public static Block create(BlockVersionId versionId,
                               BlockOrder blockOrder,
                               BlockHash blockHash,
                               BlockSize blockSize,
                               BlockS3Key s3Key) {
        return new Block(null, versionId.value(), blockOrder.value(), blockHash.value(), blockSize.value(), s3Key.value());
    }

    public static Block withId(BlockId id,
                               BlockVersionId versionId,
                               BlockOrder blockOrder,
                               BlockHash blockHash,
                               BlockSize blockSize,
                               BlockS3Key s3Key) {
        return new Block(id.value(), versionId.value(), blockOrder.value(), blockHash.value(), blockSize.value(), s3Key.value());
    }

    public record BlockId(UUID value) {}
    public record BlockVersionId(UUID value) {}
    public record BlockOrder(int value) {}
    public record BlockHash(String value) {}
    public record BlockSize(int value) {}
    public record BlockS3Key(String value) {}
}
