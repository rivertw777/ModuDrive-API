package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand.ConflictResolution;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand.Item;
import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileNamespaceId;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registers a whole upload selection in one transaction — see .docs/spec/001-file-upload-spec.md.
 * Only top-level entries can collide with what's already in the target folder: everything below
 * them lands inside a folder this batch creates, and folders are never merged. */
@UseCase
@RequiredArgsConstructor
class UploadBatchService implements UploadBatchUseCase {

    // ponytail: mirrors storage-service's modudrive.storage.max-file-size-bytes, which is what
    // actually enforces the limit on the bytes; this only turns an oversized batch away before
    // any row is created. Keep the two in sync.
    static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 * 1024;
    /** file.name and file.path are varchar(255) (V1__init.sql). Checked here so an overlong entry
     * is a 400 instead of a raw DataIntegrityViolation surfacing as a 500. */
    static final int MAX_COLUMN_LENGTH = 255;

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final SaveFileAccessPort saveFileAccessPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public List<UploadedItem> uploadBatch(UploadBatchCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(new NamespaceUserId(command.getUserId()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));
        NamespaceId namespaceId = new NamespaceId(namespace.getId());
        String target = command.getTargetPath().value();
        requireTargetDirectory(namespaceId, target);

        Map<String, Node> nodes = parse(command.getItems());

        // Active entries only (the adapter drops TRASHED/DELETED), so a trashed same-name item is
        // never a conflict — same rule as the single-file metadata upload.
        Map<String, File> existing = findFilePort.findByNamespaceIdAndPath(namespaceId, target).stream()
                .collect(Collectors.toMap(File::getName, Function.identity(), (a, b) -> a));
        Set<String> taken = new HashSet<>(existing.keySet());
        // Every top-level name the user actually picked is off-limits to numbering, so numbering
        // an earlier entry into "a (1).txt" can't push a later, non-conflicting "a (1).txt" aside.
        Set<String> requested = nodes.values().stream()
                .filter(Node::isTopLevel).map(Node::name).collect(Collectors.toSet());
        Map<String, String> finalTopNames = new HashMap<>();
        Map<String, File> replacing = new HashMap<>();
        List<String> conflicts = new ArrayList<>();

        for (Node node : nodes.values()) {
            if (!node.isTopLevel()) {
                continue;
            }
            String name = node.name();
            File clash = existing.get(name);
            if (!taken.contains(name)) {
                finalTopNames.put(name, name);
                taken.add(name);
            } else if (clash != null && !clash.isDirectory() && !node.directory()) {
                ConflictResolution resolution = command.getResolutions().get(name);
                if (resolution == null) {
                    conflicts.add(name);
                } else if (resolution == ConflictResolution.REPLACE) {
                    fileAccessGuard.requireOwner(clash, command.getUserId());
                    replacing.put(name, clash);
                    finalTopNames.put(name, name);
                } else if (resolution == ConflictResolution.KEEP_BOTH) {
                    finalTopNames.put(name, takeFreeName(name, false, taken, requested));
                }
                // SKIP: no final name, so the loop below leaves it out.
            } else {
                // Folder vs folder or file vs folder — never asked, always kept side by side.
                finalTopNames.put(name, takeFreeName(name, node.directory(), taken, requested));
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException(FileExceptionCase.FILE_BATCH_CONFLICT, Map.of("conflicts", conflicts));
        }

        FileNamespaceId fileNamespaceId = new FileNamespaceId(namespace.getId());
        FileOwnerId ownerId = new FileOwnerId(command.getUserId());
        List<String> order = new ArrayList<>(nodes.size());
        Map<String, File> saved = new HashMap<>();
        Set<String> replacedPaths = new HashSet<>();
        List<String> newPaths = new ArrayList<>();
        List<File> newFiles = new ArrayList<>();
        for (Node node : nodes.values()) {
            String top = node.segments()[0];
            String finalTop = finalTopNames.get(top);
            if (finalTop == null) {
                continue;
            }
            order.add(node.relativePath());
            File replaced = node.isTopLevel() ? replacing.get(top) : null;
            if (replaced != null) {
                replaced.restartUpload();
                saved.put(node.relativePath(), saveFilePort.saveFile(replaced));
                replacedPaths.add(node.relativePath());
                continue;
            }
            String name = node.isTopLevel() ? finalTop : node.name();
            String parent = parentPath(target, finalTop, node.segments());
            // After renaming, so a " (1)" suffix that tips a name over the limit is caught too.
            if (name.length() > MAX_COLUMN_LENGTH || parent.length() > MAX_COLUMN_LENGTH) {
                throw invalidItem();
            }
            newPaths.add(node.relativePath());
            newFiles.add(node.directory()
                    ? File.createDirectory(fileNamespaceId, new FileName(name), new FilePath(parent), ownerId)
                    : File.create(fileNamespaceId, new FileName(name), new FilePath(parent), ownerId,
                            new FileIsDirectory(false)));
        }
        if (!newFiles.isEmpty()) {
            List<File> inserted = saveFilePort.saveNewFiles(newFiles);
            for (int i = 0; i < inserted.size(); i++) {
                saved.put(newPaths.get(i), inserted.get(i));
            }
        }

        List<UploadedItem> uploaded = order.stream()
                .map(path -> new UploadedItem(path, saved.get(path), replacedPaths.contains(path)))
                .toList();
        // An uploaded file counts as "opened" for 최근 문서함, a folder doesn't — same rule as the
        // single-file metadata upload. Done here, in one pass, rather than one transaction per file
        // from the controller (that took ~70s for 5,000 files). Brand-new files can't collide on
        // uk_file_access_user_file, so this can only fail the batch in the vanishingly rare race of
        // a replaced file being first-opened by the same user at this exact moment.
        List<UUID> fileIds = uploaded.stream()
                .filter(item -> !item.file().isDirectory())
                .map(item -> item.file().getId())
                .toList();
        if (!fileIds.isEmpty()) {
            saveFileAccessPort.recordAccesses(command.getUserId(), fileIds, LocalDateTime.now());
        }
        return uploaded;
    }

    /** "/" always exists; anything else has to be an active folder in the caller's own drive —
     * which also rules out uploading into someone else's shared folder, same as today. */
    private void requireTargetDirectory(NamespaceId namespaceId, String target) {
        if ("/".equals(target)) {
            return;
        }
        // Rows are stored under this exact string, so a non-canonical spelling ("//업무", "업무/")
        // that still resolved to a real folder would create rows no listing ever shows.
        if (!target.startsWith("/") || !Arrays.stream(target.substring(1).split("/", -1)).allMatch(UploadBatchService::isValidName)) {
            throw new BusinessException(FileExceptionCase.DIRECTORY_NOT_FOUND);
        }
        int slash = target.lastIndexOf('/');
        String parent = slash == 0 ? "/" : target.substring(0, slash);
        boolean exists = findFilePort
                .findActiveByNamespaceIdAndPathAndName(namespaceId, parent, target.substring(slash + 1))
                .filter(File::isDirectory)
                .isPresent();
        if (!exists) {
            throw new BusinessException(FileExceptionCase.DIRECTORY_NOT_FOUND);
        }
    }

    /** Every listed entry plus the folders above it, keyed by relative path, parents inserted
     * before their children — so creating in iteration order never needs a parent that doesn't
     * exist yet. */
    private static Map<String, Node> parse(List<Item> items) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        Set<String> listed = new HashSet<>();
        for (Item item : items) {
            String relativePath = item.relativePath();
            if (relativePath == null || !listed.add(relativePath)) {
                throw invalidItem();
            }
            String[] segments = relativePath.split("/", -1);
            for (String segment : segments) {
                requireValidName(segment);
            }
            if (!item.directory()
                    && (item.size() == null || item.size() < 0 || item.size() > MAX_FILE_SIZE_BYTES)) {
                throw invalidItem();
            }
            for (int depth = 1; depth < segments.length; depth++) {
                String[] ancestor = Arrays.copyOf(segments, depth);
                Node known = nodes.putIfAbsent(String.join("/", ancestor), new Node(String.join("/", ancestor), ancestor, true));
                if (known != null && !known.directory()) {
                    throw invalidItem(); // "a.txt" listed as a file, then used as a folder
                }
            }
            if (nodes.containsKey(relativePath)) {
                // Already added as an earlier entry's parent folder: fine when this one is that
                // folder, contradictory when it claims to be a file.
                if (!item.directory()) {
                    throw invalidItem();
                }
                continue;
            }
            nodes.put(relativePath, new Node(relativePath, segments, item.directory()));
        }
        return nodes;
    }

    private static void requireValidName(String segment) {
        if (!isValidName(segment)) {
            throw invalidItem();
        }
    }

    private static boolean isValidName(String segment) {
        try {
            new FileName(segment);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static BusinessException invalidItem() {
        return new BusinessException(FileExceptionCase.INVALID_BATCH_ITEM);
    }

    private static String takeFreeName(String name, boolean directory, Set<String> taken, Set<String> requested) {
        FileName original = new FileName(name);
        for (int n = 1; ; n++) {
            String candidate = original.numbered(n, directory).value();
            if (!requested.contains(candidate) && taken.add(candidate)) {
                return candidate;
            }
        }
    }

    /** Where a node's row goes: the target, then the (possibly renamed) top-level folder, then
     * every segment between it and the node itself. */
    private static String parentPath(String target, String finalTop, String[] segments) {
        if (segments.length == 1) {
            return target;
        }
        String path = child(target, finalTop);
        for (int i = 1; i < segments.length - 1; i++) {
            path = child(path, segments[i]);
        }
        return path;
    }

    private static String child(String parent, String name) {
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    private record Node(String relativePath, String[] segments, boolean directory) {

        boolean isTopLevel() {
            return segments.length == 1;
        }

        String name() {
            return segments[segments.length - 1];
        }
    }
}
