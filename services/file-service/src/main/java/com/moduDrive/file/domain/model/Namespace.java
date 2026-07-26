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

    public static Namespace create(NamespaceUserId userId) {
        return new Namespace(
                null,
                userId.value(),
                "/" + userId.value()
        );
    }

    public static Namespace withId(NamespaceId id, NamespaceUserId userId, NamespaceRootPath rootPath) {
        return new Namespace(id.value(), userId.value(), rootPath.value());
    }

    public record NamespaceId(UUID value) {}
    public record NamespaceUserId(UUID value) {}
    public record NamespaceRootPath(String value) {}
}
