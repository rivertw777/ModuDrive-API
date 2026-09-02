package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.UUID;

public interface GetFileVersionPort {

    String getS3Path(UUID fileId, UUID userId);

    int getBlockCount(UUID fileId, UUID userId);

    /** Link-token lookups for anonymous visitors: the token identifies the file and authorizes
     * the read at once, so there is no caller id to pass along. */
    String getPublicS3Path(String token);

    int getPublicBlockCount(String token);

    /** Same as the two above but for a file reached through a link-shared <b>folder</b>: the
     * folder token plus the descendant's id. One lookup, since both fields come from the same
     * response. */
    VersionLocation getPublicDescendantVersion(String token, String entryId);

    /** Every version ever uploaded for this file, not just the latest — a purge has to delete
     * every version's blocks, not only the one a download would resolve to. */
    List<VersionLocation> getAllVersions(UUID fileId, UUID userId);

    record VersionLocation(String s3Path, int blockCount) {}
}
