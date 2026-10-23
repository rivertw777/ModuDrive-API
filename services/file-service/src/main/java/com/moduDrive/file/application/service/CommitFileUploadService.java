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
import com.moduDrive.file.application.port.out.UpdateNamespaceUsagePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileNamespaceId;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The commit of spec 001 2-3: the client sends a file's blocklist and where it goes, gets back the
 * blocks the server doesn't have yet, uploads those to storage-service, and commits again. Commits
 * that come back with {@code needBlocks} change nothing — no file or folder row exists until the
 * version does — so asking again is also how an interrupted upload finds where it stopped (2-1).
 *
 * <p>A request carries a group of files (spec 001 2-3), so which blocks exist is asked once for
 * the whole group — and outside any transaction, storage-service included, so a slow
 * storage-service never holds a DB connection or a block lock here (spec 006 2-4). The files with
 * nothing missing are then committed in one transaction, which re-checks under the locks; if any of
 * them goes off the plain path, each gets its own short transaction instead. */
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
    private final UpdateNamespaceUsagePort updateNamespaceUsagePort;
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
        // Drive quota (quota_bytes against used_bytes, trash included — what the usage screen
        // shows), checked before any block is asked for, so a file that won't fit is turned away
        // before its bytes are sent. Only a first pass: two requests can both pass it, so
        // commitLocked checks again, atomically, as it adds the file to the usage.
        NamespaceId namespaceId = new NamespaceId(namespace.getId());
        long used = namespace.getUsedBytes();
        // One query each for the whole group, not one per file.
        Map<UUID, FileVersion> doneByUploadId = findFileVersionsPort
                .findAllByUploadIds(commands.stream().map(c -> c.getUploadId().value()).toList()).stream()
                .collect(Collectors.toMap(FileVersion::getUploadId, Function.identity()));
        Map<String, File> activeBySpot = activeFilesAt(namespaceId, commands);
        for (int i = 0; i < commands.size(); i++) {
            CommitFileUploadCommand command = commands.get(i);
            try {
                results[i] = alreadyDone(command, ownerId, Optional.ofNullable(doneByUploadId.get(command.getUploadId().value())));
            } catch (BusinessException e) {
                results[i] = CommitResult.failed(e.getExceptionCase());
            }
            if (results[i] == null) {
                long growth = growthOf(command,
                        activeBySpot.get(UploadBatchService.child(command.getPath(), command.getName())));
                // Every growing file of the group counted, committed or not yet — the second commit
                // of the same files sees them all again, and they must fit together. A file that
                // doesn't grow the drive always goes through, and frees nothing for the others: it
                // may never be committed (its blocks may never come).
                if (growth > 0 && used + growth > namespace.getQuotaBytes()) {
                    results[i] = CommitResult.failed(FileExceptionCase.QUOTA_EXCEEDED);
                } else {
                    used += Math.max(growth, 0);
                    asked.addAll(command.getBlocklist().value());
                }
            }
        }

        // One look for the whole group, outside any transaction (see the class comment).
        Set<String> committed = asked.isEmpty() ? Set.of() : findCommittedBlocksPort.findCommittedHashes(ownerId, asked);
        List<String> notCommitted = asked.stream().filter(h -> !committed.contains(h)).toList();
        Map<String, Integer> found = Map.of();
        // storage-service down: files made only of committed blocks still go through; the rest fail
        // on their own with its 503 instead of failing the whole group (spec 001 2-3).
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

        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            if (results[i] != null) {
                continue;
            }
            List<String> needBlocks = commands.get(i).getBlocklist().value().stream().distinct()
                    .filter(h -> !committed.contains(h) && !uploaded.containsKey(h))
                    .toList();
            if (!needBlocks.isEmpty()) {
                results[i] = storageDown != null ? CommitResult.failed(storageDown) : CommitResult.need(needBlocks);
            } else {
                ready.add(i);
            }
        }

        List<CommitResult> together = ready.size() > 1
                ? commitTogether(ready.stream().map(commands::get).toList(), namespace, uploaded) : null;
        for (int k = 0; k < ready.size(); k++) {
            int i = ready.get(k);
            results[i] = together != null ? together.get(k) : commitAlone(commands.get(i), ownerId, namespace, uploaded);
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
                    folder = transactionTemplate.execute(status -> folderAt(namespaceId, target, ownerId, new HashMap<>()));
                } catch (BusinessException e) {
                    if (e.getExceptionCase() != FileExceptionCase.FILE_ALREADY_EXISTS) {
                        throw e;
                    }
                    // Another commit created a folder on the way first (the unique slot): the second
                    // look finds it. A file in the way fails the same again.
                    folder = transactionTemplate.execute(status -> folderAt(namespaceId, target, ownerId, new HashMap<>()));
                }
                return DirectoryResult.created(folder.getId());
            } catch (BusinessException e) {
                return DirectoryResult.failed(e.getExceptionCase());
            } catch (PessimisticLockingFailureException e) {
                // A lock cycle Postgres broke, as for a file: only this folder fails.
                return DirectoryResult.failed(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
            }
        }).toList();
    }

    /** One file in its own transaction: it failing leaves the others committed. */
    private CommitResult commitAlone(CommitFileUploadCommand command, UUID ownerId, Namespace namespace,
                                     Map<String, Integer> uploaded) {
        Set<String> distinct = new LinkedHashSet<>(command.getBlocklist().value());
        try {
            return transactionTemplate.execute(status -> commitLocked(command, namespace, distinct, uploaded));
        } catch (BusinessException | DataIntegrityViolationException e) {
            // The same commit sent twice at once (a retry racing the original): the loser hits
            // a unique slot — the file's, or the uploadId's — and answers with the winner's
            // version, checked like any resend (alreadyDone).
            try {
                CommitResult done = alreadyDone(command, ownerId,
                        findFileVersionsPort.findByUploadId(command.getUploadId().value()));
                if (done != null) {
                    return done;
                }
                if (e instanceof BusinessException business) {
                    return CommitResult.failed(business.getExceptionCase());
                }
                throw e;
            } catch (BusinessException mismatch) {
                return CommitResult.failed(mismatch.getExceptionCase());
            }
        } catch (PessimisticLockingFailureException e) {
            // Postgres broke a lock cycle with another commit or a trash: only this file fails,
            // and it can be sent again.
            return CommitResult.failed(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
        }
    }

    /** The group's files in one transaction — the locks, rows and usage once for all of them, not
     * once per file. Null when anything goes off the plain path (a slot taken, a block swept, the
     * quota raced, a lock cycle, a bad blocklist): it is all rolled back and the caller commits the
     * files one by one, each answering for itself as before. */
    private List<CommitResult> commitTogether(List<CommitFileUploadCommand> group, Namespace namespace,
                                              Map<String, Integer> uploaded) {
        // Two files at one spot, or one uploadId twice: the one-by-one path tells them apart.
        if (group.stream().map(c -> UploadBatchService.child(c.getPath(), c.getName())).distinct().count() < group.size()
                || group.stream().map(c -> c.getUploadId().value()).distinct().count() < group.size()) {
            return null;
        }
        try {
            return transactionTemplate.execute(status -> commitTogetherLocked(group, namespace, uploaded));
        } catch (NotTogether | BusinessException | DataIntegrityViolationException | PessimisticLockingFailureException e) {
            return null;
        }
    }

    /** Thrown inside the group's transaction to roll it back: the files go one by one instead. */
    private static final class NotTogether extends RuntimeException {
        NotTogether() {
            super(null, null, false, false);
        }
    }

    /** commitLocked for a whole group. Blocks first and the drive's usage last, as there; in between
     * each folder top-down, then the file slots in it — the order a trash locks a subtree. */
    private List<CommitResult> commitTogetherLocked(List<CommitFileUploadCommand> group, Namespace namespace,
                                                    Map<String, Integer> uploaded) {
        UUID ownerId = group.getFirst().getCallerId();
        NamespaceId namespaceId = new NamespaceId(namespace.getId());
        Set<String> distinct = new TreeSet<>();
        group.forEach(c -> distinct.addAll(c.getBlocklist().value()));
        Map<String, Integer> sizeByHash = new HashMap<>(lockCommittedBlocksPort.lockCommittedBlocks(ownerId, distinct));
        if (distinct.stream().anyMatch(h -> !sizeByHash.containsKey(h) && !uploaded.containsKey(h))) {
            throw new NotTogether();  // swept since the unlocked look: needBlocks, file by file
        }
        uploaded.forEach(sizeByHash::putIfAbsent);
        group.forEach(c -> requireShape(c.getBlocklist().value(), sizeByHash, c.getFileSize().value()));

        // Segment by segment ("/a/b" before "/a-b"), the order the folders' rows lock in.
        Map<String, Set<String>> namesByPath = new TreeMap<>(Comparator.comparing((String path) -> path.replace('/', '\0')));
        group.forEach(c -> namesByPath.computeIfAbsent(c.getPath(), path -> new LinkedHashSet<>()).add(c.getName()));
        Map<String, File> bySpot = new HashMap<>();
        Map<String, File> folders = new HashMap<>();
        namesByPath.forEach((path, names) -> {
            folderAt(namespaceId, path, ownerId, folders);
            findFilePort.lockActiveByNamespaceIdAndPathAndNames(namespaceId, path, names)
                    .forEach(f -> bySpot.put(UploadBatchService.child(f.getPath(), f.getName()), f));
        });
        if (bySpot.values().stream().anyMatch(File::isDirectory)) {
            throw new NotTogether();  // FILE_ALREADY_EXISTS for that file alone
        }
        List<UUID> currentIds = bySpot.values().stream().map(File::getCurrentVersionId).filter(Objects::nonNull).toList();
        Map<UUID, FileVersion> currentById = currentIds.isEmpty() ? Map.of() : findFileVersionsPort.findAllByIds(currentIds)
                .stream().collect(Collectors.toMap(FileVersion::getId, Function.identity()));

        List<File> created = group.stream()
                .filter(c -> !bySpot.containsKey(UploadBatchService.child(c.getPath(), c.getName())))
                .map(c -> File.create(new FileNamespaceId(namespaceId.value()), new FileName(c.getName()),
                        new FilePath(c.getPath()), new FileOwnerId(ownerId), new FileIsDirectory(false)))
                .toList();
        if (!created.isEmpty()) {
            saveFilePort.saveFiles(created).forEach(f -> bySpot.put(UploadBatchService.child(f.getPath(), f.getName()), f));
        }

        CommitResult[] results = new CommitResult[group.size()];
        List<File> files = new ArrayList<>();
        List<FileVersion> versions = new ArrayList<>();
        Map<String, Integer> countByHash = new TreeMap<>();
        long growth = 0;
        for (int k = 0; k < group.size(); k++) {
            CommitFileUploadCommand command = group.get(k);
            File file = bySpot.get(UploadBatchService.child(command.getPath(), command.getName()));
            FileVersion current = file.getCurrentVersionId() == null ? null : currentById.get(file.getCurrentVersionId());
            files.add(file);
            if (current != null && current.getFileSize().longValue() == command.getFileSize().value()
                    && current.getHashes().equals(command.getBlocklist().value())) {
                results[k] = CommitResult.committed(current);  // unchanged content: no new version
                continue;
            }
            command.getBlocklist().value().forEach(hash -> countByHash.merge(hash, 1, Integer::sum));
            growth += command.getFileSize().value() - (file.getFileSize() == null ? 0 : file.getFileSize());
            versions.add(FileVersion.create(new FileVersionFileId(file.getId()), new FileVersionOwnerId(ownerId),
                    command.getFileSize(), command.getUploadId(), command.getBlocklist()));
        }

        referenceBlocksPort.referenceBlocks(ownerId, sizeByHash, countByHash);
        List<FileVersion> saved = versions.isEmpty() ? List.of() : saveFileVersionPort.saveFileVersions(versions);
        List<File> uploadedFiles = new ArrayList<>();
        for (int k = 0, v = 0; k < group.size(); k++) {
            if (results[k] == null) {
                FileVersion version = saved.get(v++);
                files.get(k).markUploaded(version.getId(), version.getFileSize());
                uploadedFiles.add(files.get(k));
                results[k] = CommitResult.committed(version);
            }
        }
        if (!uploadedFiles.isEmpty()) {
            saveFilePort.saveFiles(uploadedFiles);
        }
        // An uploaded file counts as "opened" for 최근 문서함 (spec 001 2-3-1).
        saveFileAccessPort.recordAccesses(ownerId, files.stream().map(File::getId).toList(), LocalDateTime.now());
        if (growth != 0 && !updateNamespaceUsagePort.addUsedBytes(namespaceId, growth)) {
            throw new NotTogether();  // another commit took the room: file by file, the ones that fit go in
        }
        return List.of(results);
    }

    /** Checks what can be checked without the blocks, and answers a resend of a commit that already
     * went through (its response was lost) with the version it made ({@code done}, the version with
     * the command's uploadId). Null means: look at the blocks. */
    private CommitResult alreadyDone(CommitFileUploadCommand command, UUID ownerId, Optional<FileVersion> done) {
        requireSpot(command.getPath(), command.getName());

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
        // Read under the file's lock: no other commit can replace it meanwhile.
        long replaced = file.getFileSize() == null ? 0 : file.getFileSize();
        // The same content as the current version — a re-upload of an unchanged file — makes no new
        // version; otherwise repeating it would pile up versions (and their block rows) forever.
        Optional<FileVersion> current = file.getCurrentVersionId() == null ? Optional.empty()
                : findFileVersionsPort.findAllByIds(List.of(file.getCurrentVersionId())).stream().findFirst();
        if (current.isPresent()
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
        // An uploaded file counts as "opened" for 최근 문서함 (spec 001 2-3-1).
        saveFileAccessPort.recordAccesses(ownerId, List.of(file.getId()), LocalDateTime.now());
        // The authoritative quota check, last: a commit that lost the race to another one into the
        // same drive rolls all of the above back.
        if (!updateNamespaceUsagePort.addUsedBytes(namespaceId, fileSize - replaced)) {
            throw new BusinessException(FileExceptionCase.QUOTA_EXCEEDED);
        }
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
        folderAt(namespaceId, path, ownerId, new HashMap<>());
        return saveFilePort.saveFile(File.create(new FileNamespaceId(namespaceId.value()), new FileName(name),
                new FilePath(path), new FileOwnerId(ownerId), new FileIsDirectory(false)));
    }

    /** The folder at {@code path}, creating it and any folder above it that doesn't exist yet,
     * top-down; null for the root. A file anywhere on the way is FILE_ALREADY_EXISTS. {@code known}
     * holds the folders this transaction already locked or made, by full path — not looked up again. */
    private File folderAt(NamespaceId namespaceId, String path, UUID ownerId, Map<String, File> known) {
        FileNamespaceId fileNamespaceId = new FileNamespaceId(namespaceId.value());
        FileOwnerId fileOwnerId = new FileOwnerId(ownerId);
        String parent = "/";
        File folder = null;
        // One lookup per path segment not yet known. Two commits creating the same missing folder at once: the
        // second fails on the unique slot (FILE_ALREADY_EXISTS) for that file alone, and its retry
        // finds the folder.
        for (String segment : "/".equals(path) ? new String[0] : path.substring(1).split("/")) {
            // Locked top-down, like a trash locks a subtree: the folder can't be trashed while a file
            // goes in under it, and one trashed meanwhile isn't found — a new one is made instead.
            String at = parent;
            parent = UploadBatchService.child(parent, segment);
            folder = known.get(parent);
            if (folder != null) {
                continue;
            }
            Optional<File> found = findFilePort.lockActiveByNamespaceIdAndPathAndName(namespaceId, at, segment);
            if (found.isPresent() && !found.get().isDirectory()) {
                throw new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS);
            }
            folder = found.orElseGet(() -> saveFilePort.saveFile(
                    File.createDirectory(fileNamespaceId, new FileName(segment), new FilePath(at), fileOwnerId)));
            known.put(parent, folder);
        }
        return folder;
    }

    /** The active rows the group's files would land on, by full path — one query per folder the
     * group goes into (usually one). */
    private Map<String, File> activeFilesAt(NamespaceId namespaceId, List<CommitFileUploadCommand> commands) {
        Map<String, File> bySpot = new HashMap<>();
        commands.stream().collect(Collectors.groupingBy(CommitFileUploadCommand::getPath,
                        Collectors.mapping(CommitFileUploadCommand::getName, Collectors.toSet())))
                .forEach((path, names) -> findFilePort.findActiveByNamespaceIdAndPathAndNames(namespaceId, path, names)
                        .forEach(file -> bySpot.put(UploadBatchService.child(file.getPath(), file.getName()), file)));
        return bySpot;
    }

    /** How much the drive grows if this file is committed: its size, less the current size of the
     * file it replaces ({@code existing}, the active row at its spot; a folder counts as 0). */
    private static long growthOf(CommitFileUploadCommand command, File existing) {
        long replaced = existing == null || existing.isDirectory() || existing.getFileSize() == null
                ? 0 : existing.getFileSize();
        return command.getFileSize().value() - replaced;
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
