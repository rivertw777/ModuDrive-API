package com.moduDrive.file.domain.model;

public enum ShareScope {

    /** Only the owner and explicitly invited members. */
    RESTRICTED,

    /** Anyone holding the link token. A signed-in visitor gets the file's
     * {@link File#getLinkRole() linkRole}; an anonymous one only ever gets read + download,
     * because an editor link cannot identify who is editing. */
    LINK
}
