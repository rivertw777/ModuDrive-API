package com.moduDrive.file.domain.model;

public enum ShareScope {

    /** Only the owner and explicitly invited members. */
    RESTRICTED,

    /** Anyone holding the link token, at {@link Role#VIEWER} capability. */
    LINK
}
