package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Purges one trash root, for every purge path (single purge, empty trash, retention sweep). The
 * row is kept as a tombstone ({@code file.deleted_at}); its versions, shares and favorites go, and
 * the versions' block references with them. For a directory, {@link DirectoryCascader#purge} does
 * the same for its descendants.
 *
 * <p>{@code REQUIRES_NEW}: a batch caller purges many roots, and one root's failure must not roll
 * back the others.
 */
@Component
@RequiredArgsConstructor
class FilePurger {

    private final SaveFilePort saveFilePort;
    private final DirectoryCascader directoryCascader;
    private final ReleaseBlocksPort releaseBlocksPort;
    private final FindFilePort findFilePort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void purgeRoot(File candidate, UUID deletedBy) {
        // Callers find the roots outside this transaction; locked and read again here, so one
        // restored (or re-trashed) since is skipped instead of losing a live file's blocks.
        File root = findFilePort.lockById(new FileId(candidate.getId())).orElse(null);
        if (root == null || root.getStatus() != FileStatus.TRASHED
                || !Objects.equals(root.getTrashedAt(), candidate.getTrashedAt())) {
            return;
        }
        if (root.isDirectory()) {
            directoryCascader.purge(new NamespaceId(root.getNamespaceId()), root.fullPath(), root.getTrashedAt(), deletedBy);
        } else {
            // Before purgeFile below deletes the versions. Releasing only lowers the blocks'
            // reference counts in this transaction — a block is deleted by the unreferenced-block
            // sweep later, so a rolled-back purge never loses one.
            releaseBlocksPort.releaseBlocksOfFiles(List.of(root.getId()));
        }
        saveFilePort.purgeFile(new FileId(root.getId()), deletedBy);
    }
}
