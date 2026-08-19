package com.moduDrive.storage.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Backs the inline-preview streaming flow: a native &lt;video&gt;/&lt;audio&gt; element can't
 * attach an Authorization header, so a short-lived opaque token stands in for it on the
 * direct-{@code src} request. The token carries identity only — the download permission check
 * still runs exactly as it does for every other view, keyed off the (fileId, userId) it
 * resolves to. */
public interface StreamTokenPort {

    String issue(UUID fileId, UUID userId);

    Optional<StreamTokenTarget> resolve(String token);
}
