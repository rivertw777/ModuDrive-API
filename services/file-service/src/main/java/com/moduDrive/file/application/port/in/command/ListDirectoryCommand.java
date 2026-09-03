package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.DirectorySort;
import com.moduDrive.file.domain.model.File.FilePath;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ListDirectoryCommand {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final NamespaceUserId userId;
    private final FilePath path;
    private final DirectorySort sort;
    /** Opaque page token from the previous page's response; null for the first page. */
    private final String cursor;
    private final int limit;

    public ListDirectoryCommand(UUID userId, String path, DirectorySort sort, String cursor, int limit) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (sort == null) {
            throw new IllegalArgumentException("sort must not be null");
        }
        this.userId = new NamespaceUserId(userId);
        this.path = new FilePath(path);
        this.sort = sort;
        this.cursor = (cursor == null || cursor.isBlank()) ? null : cursor;
        this.limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
