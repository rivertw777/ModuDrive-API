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

    public static Namespace create(NamespaceUserId userId, NamespaceQuotaBytes quotaBytes) {
        return new Namespace(
                null,
                userId.value(),
                "/" + userId.value(),
                quotaBytes.value()
        );
    }

    public static Namespace withId(NamespaceId id, NamespaceUserId userId, NamespaceRootPath rootPath, NamespaceQuotaBytes quotaBytes) {
        return new Namespace(id.value(), userId.value(), rootPath.value(), quotaBytes.value());
    }

    public record NamespaceId(UUID value) {}
    public record NamespaceUserId(UUID value) {}
    public record NamespaceRootPath(String value) {}
    public record NamespaceQuotaBytes(long value) {}
}
