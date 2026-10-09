package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReferenceBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileVersionPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.FileVersionFileId;
import com.moduDrive.file.domain.model.FileVersion.FileVersionOwnerId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The commit of spec 008 2장: the client sends a file's blocklist, gets back the blocks the server
 * doesn't have yet, uploads those to storage-service, and commits again. Commits that come back
 * with {@code needBlocks} change nothing, so asking again is also how an interrupted upload
 * finds where it stopped (2-1). */
@UseCase
@RequiredArgsConstructor
class CommitFileUploadService implements CommitFileUploadUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final FindFileVersionsPort findFileVersionsPort;
    private final SaveFileVersionPort saveFileVersionPort;
    private final LockCommittedBlocksPort lockCommittedBlocksPort;
    private final FindUploadedBlocksPort findUploadedBlocksPort;
    private final ReferenceBlocksPort referenceBlocksPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public CommitResult commit(CommitFileUploadCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        // Only the owner uploads: UploadBatchService creates the PENDING row in the caller's own
        // namespace, so there is no shared-EDITOR upload to authorize — and the blocks therefore
        // always land in the owner's space.
        fileAccessGuard.requireOwner(file, command.getCallerId());
        // A trashed or purged file takes no new version: it would come back as UPLOADED, and a
        // purged one could never release the blocks it references.
        if (file.isRemoved()) {
            throw new BusinessException(FileExceptionCase.FILE_NOT_FOUND);
        }
        if (file.isDirectory()) {
            throw new BusinessException(FileExceptionCase.INVALID_BLOCKLIST);
        }

        // A resend of a commit that already went through (its response was lost).
        Optional<FileVersion> done = findFileVersionsPort.findByUploadId(command.getUploadId().value());
        if (done.isPresent()) {
            if (!done.get().getFileId().equals(file.getId())) {
                throw new BusinessException(FileExceptionCase.INVALID_BLOCKLIST);
            }
            return CommitResult.committed(done.get());
        }

        long fileSize = command.getFileSize().value();
        List<String> blocklist = command.getBlocklist().value();
        if (fileSize > UploadBatchService.MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(FileExceptionCase.FILE_TOO_LARGE);
        }
        if (blocklist.size() != FileVersion.blockCountFor(fileSize)) {
            throw new BusinessException(FileExceptionCase.INVALID_BLOCKLIST);
        }

        UUID ownerId = file.getOwnerId();
        LinkedHashSet<String> distinct = new LinkedHashSet<>(blocklist);
        Map<String, Integer> sizeByHash = new HashMap<>(lockCommittedBlocksPort.lockCommittedBlocks(ownerId, distinct));
        List<String> notCommitted = distinct.stream().filter(h -> !sizeByHash.containsKey(h)).toList();
        if (!notCommitted.isEmpty()) {
            Map<String, Integer> uploaded = findUploadedBlocksPort.findUploadedBlocks(ownerId, notCommitted);
            List<String> needBlocks = notCommitted.stream().filter(h -> !uploaded.containsKey(h)).toList();
            if (!needBlocks.isEmpty()) {
                return CommitResult.need(needBlocks);
            }
            sizeByHash.putAll(uploaded);
        }
        requireShape(blocklist, sizeByHash, fileSize);

        Map<String, Integer> countByHash = new HashMap<>();
        blocklist.forEach(hash -> countByHash.merge(hash, 1, Integer::sum));
        referenceBlocksPort.referenceBlocks(ownerId, sizeByHash, countByHash);

        FileVersion version = saveFileVersionPort.saveFileVersion(FileVersion.create(
                new FileVersionFileId(file.getId()),
                new FileVersionOwnerId(ownerId),
                command.getFileSize(),
                command.getUploadId(),
                command.getBlocklist()));
        file.markUploaded(version.getId(), version.getFileSize());
        saveFilePort.saveFile(file);
        return CommitResult.committed(version);
    }

    /** Every block but the last is exactly one block size and the sizes add up to the file — the
     * sizes are what storage-service actually received, not what the client claims. */
    private static void requireShape(List<String> blocklist, Map<String, Integer> sizeByHash, long fileSize) {
        long total = 0;
        for (int i = 0; i < blocklist.size(); i++) {
            int size = sizeByHash.get(blocklist.get(i));
            boolean last = i == blocklist.size() - 1;
            if (last ? size < 1 || size > FileVersion.BLOCK_SIZE : size != FileVersion.BLOCK_SIZE) {
                throw new BusinessException(FileExceptionCase.INVALID_BLOCKLIST);
            }
            total += size;
        }
        if (total != fileSize) {
            throw new BusinessException(FileExceptionCase.INVALID_BLOCKLIST);
        }
    }
}
