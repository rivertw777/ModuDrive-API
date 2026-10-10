package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.exception.ExceptionCase;
import com.moduDrive.common.infrastructure.resilience4j.CircuitBreakerExceptionCase;
import com.moduDrive.file.application.port.in.command.CommitDirectoryCommand;
import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase;
import com.moduDrive.file.application.port.out.FindCommittedBlocksPort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReferenceBlocksPort;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileVersionPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileNamespaceId;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.FileVersionFileId;
import com.moduDrive.file.domain.model.FileVersion.FileVersionOwnerId;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The commit of spec 001 2장: the client sends a file's blocklist and where it goes, gets back the
 * blocks the server doesn't have yet, uploads those to storage-service, and commits again. Commits
 * that come back with {@code needBlocks} change nothing — no file or folder row exists until the
 * version does — so asking again is also how an interrupted upload finds where it stopped (2-1).
 *
 * <p>A request carries a group of files (spec 001 2장 3번), so which blocks exist is asked once for
 * the whole group — and outside any transaction, storage-service included, so a slow
 * storage-service never holds a DB connection or a block lock here (spec 006 2-4). Each file with
 * nothing missing then gets its own short transaction, which re-checks under the lock. */
@UseCase
@RequiredArgsConstructor
class CommitFileUploadService implements CommitFileUploadUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final FindFileVersionsPort findFileVersionsPort;
    private final SaveFileVersionPort saveFileVersionPort;
    private final LockCommittedBlocksPort lockCommittedBlocksPort;
    private final FindUploadedBlocksPort findUploadedBlocksPort;
    private final ReferenceBlocksPort referenceBlocksPort;
    private final SaveFileAccessPort saveFileAccessPort;
    private final FindCommittedBlocksPort findCommittedBlocksPort;
    private final TransactionTemplate transactionTemplate;

    /** A file's blocklist can't be longer (5GB / 4MB), and a group is capped at the same, so one
     * request locks and looks up at most this many blocks. */
    static final int MAX_HASHES_PER_COMMIT = 1280;

    @Override
    public List<CommitResult> commit(List<CommitFileUploadCommand> commands) {
        if (commands.isEmpty()) {
            return List.of();
        }
        // Only into the caller's own drive, so the blocks always land in the caller's space —
        // there is no shared-EDITOR upload to authorize. One request, one caller.
        UUID ownerId = commands.getFirst().getCallerId();
        Namespace namespace = findNamespacePort.findByUserId(new NamespaceUserId(ownerId))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));
        if (commands.stream().mapToInt(c -> c.getBlocklist().value().size()).sum() > MAX_HASHES_PER_COMMIT) {
            throw new BusinessException(FileExceptionCase.COMMIT_TOO_LARGE);
        }

        CommitResult[] results = new CommitResult[commands.size()];
        Set<String> asked = new LinkedHashSet<>();
        for (int i = 0; i < commands.size(); i++) {
            try {
                results[i] = alreadyDone(commands.get(i), ownerId);
            } catch (BusinessException e) {
                results[i] = CommitResult.failed(e.getExceptionCase());
            }
            if (results[i] == null) {
                asked.addAll(commands.get(i).getBlocklist().value());
            }
        }

        // One look for the whole group, outside any transaction (see the class comment).
        Set<String> committed = asked.isEmpty() ? Set.of() : findCommittedBlocksPort.findCommittedHashes(ownerId, asked);
        List<String> notCommitted = asked.stream().filter(h -> !committed.contains(h)).toList();
        Map<String, Integer> found = Map.of();
        // storage-service down: files made only of committed blocks still go through; the rest fail
        // on their own with its 503 instead of failing the whole group (spec 001 2장 3번).
        ExceptionCase storageDown = null;
        if (!notCommitted.isEmpty()) {
            try {
                found = findUploadedBlocksPort.findUploadedBlocks(ownerId, notCommitted);
            } catch (BusinessException e) {
                storageDown = e.getExceptionCase();
            } catch (FeignException e) {
                // A plain 500 from storage-service: same outcome, only those files fail.
                storageDown = CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE;
            }
        }
        Map<String, Integer> uploaded = found;

        for (int i = 0; i < commands.size(); i++) {
            if (results[i] != null) {
                continue;
            }
            CommitFileUploadCommand command = commands.get(i);
            Set<String> distinct = new LinkedHashSet<>(command.getBlocklist().value());
            List<String> needBlocks = distinct.stream()
                    .filter(h -> !committed.contains(h) && !uploaded.containsKey(h))
                    .toList();
            if (!needBlocks.isEmpty()) {
                results[i] = storageDown != null ? CommitResult.failed(storageDown) : CommitResult.need(needBlocks);
                continue;
            }
            try {
                // Its own transaction: one file failing leaves the others committed.
                results[i] = transactionTemplate.execute(status -> commitLocked(command, namespace, distinct, uploaded));
            } catch (BusinessException | DataIntegrityViolationException e) {
                // The same commit sent twice at once (a retry racing the original): the loser hits
                // a unique slot — the file's, or the uploadId's — and answers with the winner's
                // version, checked like any resend (alreadyDone).
                try {
                    CommitResult done = alreadyDone(command, ownerId);
                    if (done != null) {
                        results[i] = done;
                    } else if (e instanceof BusinessException business) {
                        results[i] = CommitResult.failed(business.getExceptionCase());
                    } else {
                        throw e;
                    }
                } catch (BusinessException mismatch) {
                    results[i] = CommitResult.failed(mismatch.getExceptionCase());
                }
            } catch (PessimisticLockingFailureException e) {
                // Postgres broke a lock cycle with another commit or a trash: only this file fails,
                // and it can be sent again.
                results[i] = CommitResult.failed(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
            }
        }
        return List.of(results);
    }

    @Override
    public List<DirectoryResult> commitDirectories(List<CommitDirectoryCommand> commands) {
        if (commands.isEmpty()) {
            return List.of();
        }
        UUID ownerId = commands.getFirst().getCallerId();
        Namespace namespace = findNamespacePort.findByUserId(new NamespaceUserId(ownerId))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));
        NamespaceId namespaceId = new NamespaceId(namespace.getId());
        return commands.stream().map(command -> {
            try {
                requireSpot(command.getPath(), command.getName());
                String target = UploadBatchService.child(command.getPath(), command.getName());
                File folder;
                try {
                    // Its own transaction, like a file: one folder failing leaves the others.
                    folder = transactionTemplate.execute(status -> folderAt(namespaceId, target, ownerId));
                } catch (BusinessException e) {
                    if (e.getExceptionCase() != FileExceptionCase.FILE_ALREADY_EXISTS) {
                        throw e;
                    }
                    // Another commit created a folder on the way first (the unique slot): the second
                    // look finds it. A file in the way fails the same again.
                    folder = transactionTemplate.execute(status -> folderAt(namespaceId, target, ownerId));
                }
                return DirectoryResult.created(folder.getId());
            } catch (BusinessException e) {
                return DirectoryResult.failed(e.getExceptionCase());
            }
        }).toList();
    }

    /** Checks what can be checked without the blocks, and answers a resend of a commit that already
     * went through (its response was lost) with the version it made. Null means: look at the blocks. */
    private CommitResult alreadyDone(CommitFileUploadCommand command, UUID ownerId) {
        requireSpot(command.getPath(), command.getName());

        Optional<FileVersion> done = findFileVersionsPort.findByUploadId(command.getUploadId().value());
        if (done.isPresent()) {
            // Only a resend of the very same commit gets it back; anything else reusing the id is a bug.
            if (!done.get().getOwnerId().equals(ownerId)
                    || !done.get().getHashes().equals(command.getBlocklist().value())
                    || !done.get().getFileSize().equals(command.getFileSize().value())) {
                throw new BusinessException(FileExceptionCase.INVALID_BLOCKLIST);
            }
            return CommitResult.committed(done.get());
        }

        long fileSize = command.getFileSize().value();
        if (fileSize > UploadBatchService.MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(FileExceptionCase.FILE_TOO_LARGE);
        }
        if (command.getBlocklist().value().size() != FileVersion.blockCountFor(fileSize)) {
            throw new BusinessException(FileExceptionCase.INVALID_BLOCKLIST);
        }
        return null;
    }

    /** The version itself. The block rows are locked first, so the unreferenced-block sweep can't
     * delete one this commit is about to reference; one it deleted since the unlocked look — and
     * not uploaded again — goes back to the client as needed. */
    private CommitResult commitLocked(CommitFileUploadCommand command, Namespace namespace,
                                      Set<String> distinct, Map<String, Integer> uploaded) {
        UUID ownerId = command.getCallerId();
        long fileSize = command.getFileSize().value();
        List<String> blocklist = command.getBlocklist().value();
        Map<String, Integer> sizeByHash = new HashMap<>(lockCommittedBlocksPort.lockCommittedBlocks(ownerId, distinct));
        List<String> gone = distinct.stream()
                .filter(h -> !sizeByHash.containsKey(h) && !uploaded.containsKey(h))
                .toList();
        if (!gone.isEmpty()) {
            return CommitResult.need(gone);
        }
        uploaded.forEach(sizeByHash::putIfAbsent);
        requireShape(blocklist, sizeByHash, fileSize);

        String path = command.getPath();
        String name = command.getName();
        NamespaceId namespaceId = new NamespaceId(namespace.getId());
        File file = fileAt(namespaceId, path, name, ownerId);
        // The same content as the current version — a re-upload of an unchanged file — makes no new
        // version; otherwise repeating it would pile up versions (and their block rows) forever.
        Optional<FileVersion> current = file.getCurrentVersionId() == null ? Optional.empty()
                : findFileVersionsPort.findAllByIds(List.of(file.getCurrentVersionId())).stream().findFirst();
        if (file.getStatus() == FileStatus.UPLOADED && current.isPresent()
                && current.get().getFileSize() == fileSize && current.get().getHashes().equals(blocklist)) {
            saveFileAccessPort.recordAccesses(ownerId, List.of(file.getId()), LocalDateTime.now());
            return CommitResult.committed(current.get());
        }

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
        // An uploaded file counts as "opened" for 최근 문서함 (spec 001 2장).
        saveFileAccessPort.recordAccesses(ownerId, List.of(file.getId()), LocalDateTime.now());
        return CommitResult.committed(version);
    }

    /** The active file at {@code path}/{@code name} — a same-name upload is a new version of it —
     * or a new one, creating any folder above it that doesn't exist yet. */
    private File fileAt(NamespaceId namespaceId, String path, String name, UUID ownerId) {
        Optional<File> existing = findFilePort.lockActiveByNamespaceIdAndPathAndName(namespaceId, path, name);
        if (existing.isPresent()) {
            if (existing.get().isDirectory()) {
                throw new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS);
            }
            return existing.get();
        }
        folderAt(namespaceId, path, ownerId);
        return saveFilePort.saveFile(File.create(new FileNamespaceId(namespaceId.value()), new FileName(name),
                new FilePath(path), new FileOwnerId(ownerId), new FileIsDirectory(false)));
    }

    /** The folder at {@code path}, creating it and any folder above it that doesn't exist yet,
     * top-down; null for the root. A file anywhere on the way is FILE_ALREADY_EXISTS. */
    private File folderAt(NamespaceId namespaceId, String path, UUID ownerId) {
        FileNamespaceId fileNamespaceId = new FileNamespaceId(namespaceId.value());
        FileOwnerId fileOwnerId = new FileOwnerId(ownerId);
        String parent = "/";
        File folder = null;
        // ponytail: one lookup per path segment; uploads are a handful deep. Two commits creating
        // the same missing folder at once would make the second fail on the unique slot
        // (400 FILE_ALREADY_EXISTS) for that file alone, and its retry finds the folder.
        for (String segment : "/".equals(path) ? new String[0] : path.substring(1).split("/")) {
            // Locked top-down, like a trash locks a subtree: the folder can't be trashed while a file
            // goes in under it, and one trashed meanwhile isn't found — a new one is made instead.
            Optional<File> found = findFilePort.lockActiveByNamespaceIdAndPathAndName(namespaceId, parent, segment);
            if (found.isPresent() && !found.get().isDirectory()) {
                throw new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS);
            }
            String at = parent;
            folder = found.orElseGet(() -> saveFilePort.saveFile(
                    File.createDirectory(fileNamespaceId, new FileName(segment), new FilePath(at), fileOwnerId)));
            parent = UploadBatchService.child(parent, segment);
        }
        return folder;
    }

    private static void requireSpot(String path, String name) {
        if (!isCanonicalPath(path) || !UploadBatchService.isValidName(name)
                || path.length() > UploadBatchService.MAX_COLUMN_LENGTH
                || name.length() > UploadBatchService.MAX_COLUMN_LENGTH) {
            throw new BusinessException(FileExceptionCase.INVALID_BATCH_ITEM);
        }
    }

    /** "/" or "/a/b": rows are stored under this exact string, so "//a" or "a/" would make
     * entries no listing ever shows. */
    private static boolean isCanonicalPath(String path) {
        return "/".equals(path) || (path.startsWith("/")
                && Arrays.stream(path.substring(1).split("/", -1)).allMatch(UploadBatchService::isValidName));
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
