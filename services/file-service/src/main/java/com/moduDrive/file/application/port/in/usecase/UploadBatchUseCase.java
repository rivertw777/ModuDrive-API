package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.UploadBatchCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface UploadBatchUseCase {

    /** Every entry the batch created or replaced, parents before children, including intermediate
     * folders the request never listed. A skipped file is absent. */
    List<UploadedItem> uploadBatch(UploadBatchCommand command);

    /** {@code relativePath} echoes the request's path (before any conflict renaming), so the
     * client can match each result back to the file it picked. */
    record UploadedItem(String relativePath, File file, boolean replaced) {}
}
