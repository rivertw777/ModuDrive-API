package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Permanently deletes one trash root — every purge path (single-file purge, empty-trash,
 * scheduled retention sweep) ends up doing exactly this for each root it finds, so it's
 * centralized here rather than repeated per caller. A directory has no blocks of its own;
 * {@link DirectoryCascader#purge} both purges its descendants' blocks and deletes their rows.
 *
 * {@code REQUIRES_NEW}: a batch caller (empty-trash, the retention sweep) purges many roots in
 * one pass — without its own transaction, one root's failure would roll back every other root
 * already purged in the same call. Isolating each root means a failure only loses that one root's
 * progress, not the whole batch's.
 */
@Component
@RequiredArgsConstructor
class FilePurger {

    private final SaveFilePort saveFilePort;
    private final DirectoryCascader directoryCascader;
    private final PurgeStorageBlocksPort purgeStorageBlocksPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void purgeRoot(File root) {
        if (root.isDirectory()) {
            directoryCascader.purge(new NamespaceId(root.getNamespaceId()), root.fullPath(), root.getUpdatedAt());
        } else {
            FileId fileId = new FileId(root.getId());
            UUID ownerId = root.getOwnerId();
            // Deferred to after commit: the S3 delete can't be rolled back, so it must not run
            // until the row deletion below is actually durable — otherwise a later failure in
            // this same transaction would roll the row back while its blocks stay gone.
            AfterCommit.run(() -> purgeStorageBlocksPort.purgeBlocks(fileId, ownerId));
        }
        saveFilePort.deleteFile(new FileId(root.getId()));
    }
}
