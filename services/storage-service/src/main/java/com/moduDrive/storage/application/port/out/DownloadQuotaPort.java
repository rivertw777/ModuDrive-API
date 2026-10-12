package com.moduDrive.storage.application.port.out;

public interface DownloadQuotaPort {

    /**
     * Throws {@code BusinessException(DOWNLOAD_QUOTA_EXCEEDED)} when {@code fileKey} has already spent
     * its window. The first request of a window always passes, so a file larger than the quota can
     * still be downloaded.
     *
     * <p>{@code scope} is the caller's user id for an authenticated download and the file for an
     * anonymous one (see {@code PublicDownloadFileService.quotaScope}), so anonymous visitors can't
     * lock the owner out. {@code fileKey} is one version; a new version starts a fresh window.
     */
    void checkWithinQuota(String scope, String fileKey);

    /**
     * Records {@code bytes} actually served, opening the window on the first recorded byte. Call it in a
     * {@code finally} so an aborted transfer is charged only what was sent. Best-effort: a Redis failure
     * is logged, not thrown.
     */
    void recordUsage(String scope, String fileKey, long bytes);
}
