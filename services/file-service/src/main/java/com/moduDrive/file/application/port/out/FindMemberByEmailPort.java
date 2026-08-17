package com.moduDrive.file.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface FindMemberByEmailPort {

    /** Resolves a share invite's target email to its member id.
     * Empty when no ModuDrive member owns that email — a guest invite, not an error. */
    Optional<UUID> findMemberIdByEmail(String email);
}
