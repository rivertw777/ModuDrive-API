package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.common.core.exception.ExceptionCase;
import com.moduDrive.file.application.port.in.command.CommitDirectoryCommand;
import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;
import java.util.UUID;

public interface CommitFileUploadUseCase {

    /** For each file, in order: makes its blocklist a new version of the file at its path and name —
     * creating the file and any missing folder above it — if every block is already stored;
     * otherwise changes nothing and answers which blocks to upload first. Files succeed or fail on
     * their own; only a problem with the whole group (no namespace, too many hashes) throws. */
    List<CommitResult> commit(List<CommitFileUploadCommand> commands);

    /** For each folder, in order: the folder at its path and name, creating it and any missing folder
     * above it — an existing one is answered as is. For folders no file commit creates: empty ones,
     * or ones whose files all failed. Each succeeds or fails on its own. */
    List<DirectoryResult> commitDirectories(List<CommitDirectoryCommand> commands);

    /** Exactly one of: {@code fileId} or {@code error}. */
    record DirectoryResult(UUID fileId, ExceptionCase error) {

        public static DirectoryResult created(UUID fileId) {
            return new DirectoryResult(fileId, null);
        }

        public static DirectoryResult failed(ExceptionCase error) {
            return new DirectoryResult(null, error);
        }
    }

    /** Exactly one of: {@code needBlocks} non-empty, {@code version} set, or {@code error}. */
    record CommitResult(List<String> needBlocks, FileVersion version, ExceptionCase error) {

        public static CommitResult need(List<String> needBlocks) {
            return new CommitResult(needBlocks, null, null);
        }

        public static CommitResult committed(FileVersion version) {
            return new CommitResult(List.of(), version, null);
        }

        public static CommitResult failed(ExceptionCase error) {
            return new CommitResult(null, null, error);
        }
    }
}
