package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.UUID;

public interface GetFileVersionPort {

    /** {@code markAccessed} true records a file-access (moves the file to the top of the user's
     * "recent") — set it only for an inline preview/open, not a plain download, to match Google
     * Drive's behaviour. One lookup, since s3Path and blockCount come from the same response. */
    VersionLocation getLatestVersion(UUID fileId, UUID userId, boolean markAccessed);

    /** Link lookup for anonymous visitors: {@code fileId} identifies the entry (the shared file
     * or one nested under a shared folder) and {@code key} authorizes the read, so there is no
     * caller id to pass along. One lookup — like {@link #getLatestVersion} — so s3Path and
     * blockCount can't tear across a version committed mid-download. */
    VersionLocation getPublicVersion(String fileId, String key);

    /** Every version ever uploaded for this file, not just the latest — a purge has to delete
     * every version's blocks, not only the one a download would resolve to. */
    List<VersionLocation> getAllVersions(UUID fileId, UUID userId);

    record VersionLocation(String s3Path, int blockCount) {}
}
