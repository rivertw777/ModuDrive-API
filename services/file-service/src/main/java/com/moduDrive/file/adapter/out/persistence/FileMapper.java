package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Block;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.Namespace;
import org.springframework.stereotype.Component;

import static com.moduDrive.file.domain.model.Block.*;
import static com.moduDrive.file.domain.model.File.*;
import static com.moduDrive.file.domain.model.FileShare.*;
import static com.moduDrive.file.domain.model.FileVersion.*;
import static com.moduDrive.file.domain.model.Namespace.*;

@Component
class FileMapper {

    Namespace mapNamespaceToDomain(NamespaceJpaEntity entity) {
        return Namespace.withId(
                new NamespaceId(entity.getId()),
                new NamespaceUserId(entity.getUserId()),
                new NamespaceRootPath(entity.getRootPath()),
                new NamespaceQuotaBytes(entity.getQuotaBytes())
        );
    }

    File mapFileToDomain(FileJpaEntity entity) {
        File file = File.withId(
                new FileId(entity.getId()),
                new FileNamespaceId(entity.getNamespaceId()),
                new FileName(entity.getName()),
                new FilePath(entity.getPath()),
                new FileOwnerId(entity.getOwnerId()),
                entity.getCurrentVersionId() != null ? new FileCurrentVersionId(entity.getCurrentVersionId()) : null,
                entity.getFileSize() != null ? new FileSize(entity.getFileSize()) : null,
                entity.getStatus(),
                new FileIsDirectory(entity.isDirectory())
        );
        file.markFavorite(entity.isFavorite());
        return file;
    }

    FileVersion mapFileVersionToDomain(FileVersionJpaEntity entity) {
        return FileVersion.withId(
                new FileVersionId(entity.getId()),
                new FileVersionFileId(entity.getFileId()),
                new FileVersionFileSize(entity.getFileSize()),
                new FileVersionBlockCount(entity.getBlockCount()),
                new FileVersionS3Path(entity.getS3Path())
        );
    }

    FileShare mapFileShareToDomain(FileShareJpaEntity entity) {
        return FileShare.withId(
                new FileShareId(entity.getId()),
                new FileShareFileId(entity.getFileId()),
                new FileShareOwnerId(entity.getOwnerId()),
                new FileShareSharedWithUserId(entity.getSharedWithUserId()),
                new FileSharePermission(entity.getPermission())
        );
    }
}
