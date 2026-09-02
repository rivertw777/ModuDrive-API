package com.moduDrive.storage.application.port.out;

public interface DownloadQuotaPort {

    /**
     * Records {@code bytes} as served for {@code fileKey} within the current window and throws
     * {@code BusinessException(DOWNLOAD_QUOTA_EXCEEDED)} once that file's cumulative volume crosses
     * the per-file limit. The window is fixed-length and starts on the first recorded byte, so
     * downloads of an over-shared file stay blocked until it rolls over (~24h), the same way Google
     * Drive locks a file that has been downloaded too much in a day.
     *
     * <p>{@code scope} confines a counter to one actor: the caller's user id for an authenticated
     * download, the link token for an anonymous one. Without it, an anonymous holder of a share
     * link could burn the counter and lock the owner out of their own file. Two different share
     * links to the same file therefore meter independently — matching Drive's per-link model.
     *
     * <p>{@code fileKey} is the storage path of a single version, so uploading a new version starts
     * a fresh window — acceptable: a new version is new bytes. The recorded amount is the nominal
     * block-padded size ({@code blockCount * blockSize}), not the exact file length; it is a volume
     * cap, not an accounting ledger. Inline-preview fetches count too, so a {@code <video>} element
     * re-requesting on every seek each spend against the quota (bounded per fetch by the 100 MB
     * preview cap).
     */
    void recordAndEnforce(String scope, String fileKey, long bytes);
}
