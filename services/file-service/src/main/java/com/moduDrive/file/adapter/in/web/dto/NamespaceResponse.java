package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.Namespace;

import java.util.UUID;

public record NamespaceResponse(
        UUID namespaceId,
        Long userId,
        String rootPath
) {
    public static NamespaceResponse from(Namespace namespace) {
        return new NamespaceResponse(namespace.getId(), namespace.getUserId(), namespace.getRootPath());
    }
}
