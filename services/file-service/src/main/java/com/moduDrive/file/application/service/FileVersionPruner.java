package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.DeleteFileVersionsPort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** Drops one file's versions past retention, in a transaction of its own — a separate bean so
 * {@link PruneFileVersionsService}'s per-file calls go through the transactional proxy. */
@Component
@RequiredArgsConstructor
class FileVersionPruner {

    private final FindFilePort findFilePort;
    private final FindFileVersionsPort findFileVersionsPort;
    private final LockCommittedBlocksPort lockCommittedBlocksPort;
    private final ReleaseBlocksPort releaseBlocksPort;
    private final DeleteFileVersionsPort deleteFileVersionsPort;

    /** Returns how many versions it dropped. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int prune(FileId fileId, LocalDateTime cutoff, int keep) {
        List<FileVersion> candidates = findFileVersionsPort.findVersionsToPrune(fileId, cutoff, keep);
        if (candidates.isEmpty()) {
            return 0;
        }
        // Blocks first (hash order), then the file row — a commit's order (lockCommittedBlocks, then
        // fileAt). The other way round, re-uploading this file (old and new versions sharing most
        // blocks) while this runs would leave each holding what the other waits for.
        candidates.stream()
                .collect(Collectors.groupingBy(FileVersion::getOwnerId, TreeMap::new,
                        Collectors.flatMapping(version -> version.getHashes().stream(),
                                Collectors.toCollection(TreeSet::new))))
                .forEach(lockCommittedBlocksPort::lockCommittedBlocks);
        // The file row, as FilePurger locks it: a purge of this file runs wholly before or after this,
        // so no version's blocks are released twice. Read again under it, and drop only versions both
        // reads picked — gone or made current since means kept, picked only now means its blocks
        // weren't locked above (the next run takes it).
        if (findFilePort.lockById(fileId).isEmpty()) {
            return 0;
        }
        Set<UUID> picked = candidates.stream().map(FileVersion::getId).collect(Collectors.toSet());
        List<FileVersion> versions = findFileVersionsPort.findVersionsToPrune(fileId, cutoff, keep).stream()
                .filter(version -> picked.contains(version.getId()))
                .toList();
        if (versions.isEmpty()) {
            return 0;
        }
        releaseBlocksPort.releaseBlocks(versions);
        deleteFileVersionsPort.deleteFileVersions(versions);
        return versions.size();
    }
}
