package com.moduDrive.common.event.file;

/** Queues file-service publishes to. The queue name as the broker knows it. */
public final class FileQueues {

    public static final String SHARE_INVITE_MAIL_REQUESTED = "mail-share-invite-requested";
    public static final String FILE_SHARED = "notification-file-shared";
    public static final String BLOCKS_PURGE_REQUESTED = "storage-blocks-purge-requested";

    private FileQueues() {
    }
}
