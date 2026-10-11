package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Namespace {

    private final UUID id;
    private final UUID userId;
    private final String rootPath;
    private final long quotaBytes;
    /** What {@code quotaBytes} is checked against: the current size of every file, trash included,
     * purged ones not. Read here only as a hint — the authoritative check is the conditional UPDATE
     * behind {@code UpdateNamespaceUsagePort}. */
    private final long usedBytes;

    public static Namespace create(NamespaceUserId userId, NamespaceQuotaBytes quotaBytes) {
        return new Namespace(
                null,
                userId.value(),
                "/" + userId.value(),
                quotaBytes.value(),
                0L
        );
    }

    public static Namespace withId(NamespaceId id, NamespaceUserId userId, NamespaceRootPath rootPath, NamespaceQuotaBytes quotaBytes) {
        return withId(id, userId, rootPath, quotaBytes, new NamespaceUsedBytes(0L));
    }

    public static Namespace withId(NamespaceId id, NamespaceUserId userId, NamespaceRootPath rootPath,
                                   NamespaceQuotaBytes quotaBytes, NamespaceUsedBytes usedBytes) {
        return new Namespace(id.value(), userId.value(), rootPath.value(), quotaBytes.value(), usedBytes.value());
    }

    public record NamespaceId(UUID value) {}
    public record NamespaceUserId(UUID value) {}
    public record NamespaceRootPath(String value) {}
    public record NamespaceQuotaBytes(long value) {}
    public record NamespaceUsedBytes(long value) {}
}
