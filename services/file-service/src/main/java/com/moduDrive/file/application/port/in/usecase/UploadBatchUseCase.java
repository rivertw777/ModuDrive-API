package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.UploadBatchCommand;

import java.util.List;
import java.util.UUID;

public interface UploadBatchUseCase {

    /** Where every entry of the selection will land, parents before children, including
     * intermediate folders the request never listed. A skipped entry is absent. Creates nothing. */
    List<UploadedItem> uploadBatch(UploadBatchCommand command);

    /** {@code relativePath} echoes the request's path (before any conflict renaming), so the
     * client can match each result back to the file it picked; {@code path}/{@code name} are what
     * it commits to. {@code fileId} is the existing entry a replace or merge reuses, null for a
     * new one. */
    record UploadedItem(String relativePath, UUID fileId, String name, String path,
                        boolean directory, boolean replaced) {}
}
