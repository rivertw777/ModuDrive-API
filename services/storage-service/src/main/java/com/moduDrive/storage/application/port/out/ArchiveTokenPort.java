package com.moduDrive.storage.application.port.out;

import java.util.Optional;

/** Lets the browser fetch a zip by plain link navigation (native download UI, no Blob in memory): the
 * checked request is parked behind a short-lived, single-use token that the link carries. */
public interface ArchiveTokenPort {

    String issue(ArchiveRequest request);

    /** Consumes the token — a second redeem of the same token finds nothing. */
    Optional<ArchiveRequest> redeem(String token);
}
