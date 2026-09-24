package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.UUID;

/** What a zip download was prepared for: a signed-in caller's pick ({@code userId} set), or an
 * anonymous one through a public link ({@code userId} null, {@code key} optional — see
 * {@code PublicFileResolver}). */
public record ArchiveRequest(UUID userId, String key, List<UUID> fileIds) {

    public boolean isPublic() {
        return userId == null;
    }

    /** Same counters a single download of {@code fileId} would spend — see
     * {@code DownloadFileService} / {@code PublicDownloadFileService.quotaScope}. */
    public String quotaScope(UUID fileId) {
        return isPublic() ? "public:" + fileId : userId.toString();
    }
}
