package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;

public interface SaveFilePort {

    File saveFile(File file);

    /** Purge from trash: drop blocks/versions/shares/favorites, keep the metadata row as a
     * tombstone with {@code deletedAt} stamped. */
    void purgeFile(FileId fileId);

    /** Hard delete — everything, row included. Not used by the trash flow (see {@link #purgeFile});
     * for a future tombstone-cleanup job. */
    void deleteFile(FileId fileId);
}
