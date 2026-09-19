package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Block;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileAccess;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.ShareScope;
import org.springframework.stereotype.Component;

import static com.moduDrive.file.domain.model.Block.*;
import static com.moduDrive.file.domain.model.File.*;
import static com.moduDrive.file.domain.model.FileAccess.*;
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
        file.markUpdatedAt(entity.getUpdatedAt());
        file.markTrashedAt(entity.getTrashedAt());
        file.markDeletedAt(entity.getDeletedAt());
        if (entity.getAccessScope() == ShareScope.LINK) {
            // link_role is deliberately not read back: a link is viewer-only by domain invariant
            // (see File#enableLinkSharing, spec 2), so a null link_role and one somehow carrying
            // EDITOR resolve the same viewer-only way (#318).
            file.enableLinkSharing();
        }
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

    FileAccess mapFileAccessToDomain(FileAccessJpaEntity entity) {
        return FileAccess.of(
                new FileAccessUserId(entity.getUserId()),
                new FileAccessFileId(entity.getFileId()),
                entity.getAccessedAt());
    }

    FileShare mapFileShareToDomain(FileShareJpaEntity entity) {
        return FileShare.withId(
                new FileShareId(entity.getId()),
                new FileShareFileId(entity.getFileId()),
                new FileShareOwnerId(entity.getOwnerId()),
                entity.getSharedWithUserId() != null ? new FileShareSharedWithUserId(entity.getSharedWithUserId()) : null,
                new FileShareRole(entity.getGrantedRole()),
                entity.getToken(),
                entity.getGranteeEmail(),
                entity.getCreatedAt()
        );
    }
}
