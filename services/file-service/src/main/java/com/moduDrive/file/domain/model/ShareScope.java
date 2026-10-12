package com.moduDrive.file.domain.model;

public enum ShareScope {

    /** Only the owner and explicitly invited members. */
    RESTRICTED,

    /** Anyone who has this entry's {@code fileId}, which is itself the unguessable capability.
     * Always {@link Role#VIEWER} (see {@link File#enableLinkSharing()}). */
    LINK
}
