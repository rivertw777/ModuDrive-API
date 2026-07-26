package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class SearchFilesCommand {

    private final NamespaceUserId userId;
    private final String query;

    public SearchFilesCommand(UUID userId, String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        this.userId = new NamespaceUserId(userId);
        this.query = query;
    }
}
