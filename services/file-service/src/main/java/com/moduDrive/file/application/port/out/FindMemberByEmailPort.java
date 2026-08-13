package com.moduDrive.file.application.port.out;

import java.util.UUID;

public interface FindMemberByEmailPort {

    /** Resolves a share invite's target email to its member id.
     * Throws {@code BusinessException(SHARE_TARGET_NOT_FOUND)} when no member owns that email. */
    UUID findMemberIdByEmail(String email);
}
