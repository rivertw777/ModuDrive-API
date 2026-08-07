package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.FileCategory;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class GetFilesByCategoryCommand {

    private final NamespaceUserId userId;
    private final FileCategory category;

    public GetFilesByCategoryCommand(UUID userId, FileCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        this.userId = new NamespaceUserId(userId);
        this.category = category;
    }
}
