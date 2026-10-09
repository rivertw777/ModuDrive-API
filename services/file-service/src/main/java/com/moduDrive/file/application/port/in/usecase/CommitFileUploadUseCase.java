package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface CommitFileUploadUseCase {

    /** Makes the blocklist the file's new version if every block is already stored; otherwise
     * changes nothing and answers which blocks to upload first. */
    CommitResult commit(CommitFileUploadCommand command);

    /** Either {@code needBlocks} is non-empty and {@code version} is null, or the reverse. */
    record CommitResult(List<String> needBlocks, FileVersion version) {

        public static CommitResult need(List<String> needBlocks) {
            return new CommitResult(needBlocks, null);
        }

        public static CommitResult committed(FileVersion version) {
            return new CommitResult(List.of(), version);
        }
    }
}
