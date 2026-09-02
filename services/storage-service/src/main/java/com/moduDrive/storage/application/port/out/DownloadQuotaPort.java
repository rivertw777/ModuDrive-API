package com.moduDrive.storage.application.port.out;

public interface DownloadQuotaPort {

    /**
     * Throws {@code BusinessException(DOWNLOAD_QUOTA_EXCEEDED)} when {@code fileKey} has already
     * spent its window; a cheap read, meant to run before a byte is streamed. The opening request
     * of a window always passes, even for a file larger than the whole quota — otherwise such a
     * file would be permanently undownloadable.
     *
     * <p>{@code scope} confines a counter to one actor: the caller's user id for an authenticated
     * download, the link token for an anonymous one. Without it an anonymous holder of a share
     * link could burn the counter and lock the owner out of their own file; two different links to
     * the same file therefore meter independently, matching Drive's per-link model.
     *
     * <p>{@code fileKey} is the storage path of a single version, so uploading a new version starts
     * a fresh window — a new version is new bytes.
     */
    void checkWithinQuota(String scope, String fileKey);

    /**
     * Records {@code bytes} actually served for {@code fileKey}, opening the window (and setting its
     * TTL) on the first recorded byte. Call it in a {@code finally} so an aborted transfer only
     * spends what really left the building — the quota is a cap on egress, and charging a full
     * nominal size up front would let a client burn a window with connections it drops at byte 0.
     * A non-positive {@code bytes} records nothing. Best-effort: a Redis failure here is logged,
     * not surfaced.
     */
    void recordUsage(String scope, String fileKey, long bytes);
}
