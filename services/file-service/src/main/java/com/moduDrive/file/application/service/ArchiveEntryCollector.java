package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lays out already-authorized roots as zip entries — shared by the signed-in and the public zip
 * routes so the two can't disagree about what a folder zip contains. Access is checked on the
 * roots only: a grant (or LINK scope, or guest invite) on a folder reaches everything under it and
 * no descendant grant can take DOWNLOAD away (every role has it), so re-checking each descendant
 * would only cost an ancestor walk per file. The zip size caps live here too, so both the prepare
 * and the download step (which re-resolves) enforce them.
 */
@Component
@RequiredArgsConstructor
class ArchiveEntryCollector {

    // ponytail: fixed caps; make configurable if a real need to tune them shows up.
    static final int MAX_FILES = 10_000;
    static final long MAX_BYTES = 20L * 1024 * 1024 * 1024;

    private final FindFilePort findFilePort;
    private final FindFileVersionsPort findFileVersionsPort;

    List<ArchiveEntry> collect(List<File> roots) {
        // zip path -> file, insertion-ordered so a folder's own entry precedes its contents.
        Map<String, File> laidOut = new LinkedHashMap<>();
        Set<String> rootNames = new HashSet<>();
        for (File root : withoutNested(roots)) {
            String name = uniqueName(root, rootNames);
            if (!root.isDirectory()) {
                laidOut.put(name, root);
                continue;
            }
            laidOut.put(name + "/", root);
            String rootPath = root.fullPath();
            findFilePort.findByNamespaceIdAndPathStartingWith(new NamespaceId(root.getNamespaceId()), rootPath).stream()
                    .filter(descendant -> !descendant.isRemoved())
                    .forEach(descendant -> laidOut.put(
                            name + descendant.fullPath().substring(rootPath.length()) + (descendant.isDirectory() ? "/" : ""),
                            descendant));
            // Checked per root, so a huge pick stops before every other root is expanded too.
            requireWithinLimits(laidOut.values());
        }

        Map<UUID, FileVersion> versions = findFileVersionsPort.findAllByIds(laidOut.values().stream()
                        .map(File::getCurrentVersionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(FileVersion::getId, Function.identity()));

        List<ArchiveEntry> entries = new ArrayList<>();
        laidOut.forEach((path, file) -> {
            if (file.isDirectory()) {
                entries.add(new ArchiveEntry(path, null));
                return;
            }
            // Never finished uploading — nothing to put in the zip.
            FileVersion version = versions.get(file.getCurrentVersionId());
            if (version != null) {
                entries.add(new ArchiveEntry(path, version));
            }
        });
        return entries;
    }

    /** Drops a pick that sits inside another picked folder — the folder already carries it, and
     * keeping both would zip (and meter) the same bytes twice. */
    private static List<File> withoutNested(List<File> roots) {
        return roots.stream()
                .filter(root -> roots.stream().noneMatch(other -> other.isDirectory()
                        && other.getNamespaceId().equals(root.getNamespaceId())
                        && (root.getPath() + "/").startsWith(other.fullPath() + "/")))
                .toList();
    }

    private static void requireWithinLimits(Collection<File> files) {
        List<File> plain = files.stream().filter(file -> !file.isDirectory()).toList();
        long bytes = plain.stream().map(File::getFileSize).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        if (plain.size() > MAX_FILES || bytes > MAX_BYTES) {
            throw new BusinessException(FileExceptionCase.ARCHIVE_TOO_LARGE);
        }
    }

    /** Roots can come from different folders (search, recent), so two may share a name —
     * number the later ones "(1)", "(2)" before the extension, like a browser does. Names inside
     * a folder are already unique. */
    private static String uniqueName(File root, Set<String> taken) {
        String name = root.getName();
        int dot = root.isDirectory() ? -1 : name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String candidate = name;
        for (int n = 1; !taken.add(candidate); n++) {
            candidate = base + " (" + n + ")" + ext;
        }
        return candidate;
    }
}
