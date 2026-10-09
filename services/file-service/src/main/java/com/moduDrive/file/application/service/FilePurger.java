package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
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
 * Purges one trash root — every purge path (single-file purge, empty-trash, scheduled retention
 * sweep) ends up doing exactly this for each root it finds, so it's centralized here rather than
 * repeated per caller. "Purge" keeps the metadata row as a tombstone ({@code file.deleted_at});
 * only the versions/shares/favorites go, and the versions' block references with them. A directory has no blocks of its own;
 * {@link DirectoryCascader#purge} tombstones its descendants and releases their blocks.
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
    private final ReleaseBlocksPort releaseBlocksPort;
    private final FindFileVersionsPort findFileVersionsPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void purgeRoot(File root, UUID deletedBy) {
        if (root.isDirectory()) {
            directoryCascader.purge(new NamespaceId(root.getNamespaceId()), root.fullPath(), root.getTrashedAt(), deletedBy);
        } else {
            // Read the versions now: purgeFile below deletes their rows. Releasing only lowers the
            // blocks' reference counts in this transaction — a block is deleted by the
            // unreferenced-block sweep later, so a rolled-back purge never loses one.
            releaseBlocksPort.releaseBlocks(findFileVersionsPort.findAllByFileId(new FileId(root.getId())));
        }
        saveFilePort.purgeFile(new FileId(root.getId()), deletedBy);
    }
}
