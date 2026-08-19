package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

/** Exactly one of {@code headerUserId}/{@code streamToken} is expected to be non-null — the
 * gateway-authenticated caller carries the former, a &lt;video&gt;/&lt;audio&gt; element's
 * direct-{@code src} request carries the latter. Both null is a legitimate case (unauthenticated
 * request with no token either) that the use case rejects, not a validation error here. */
@Getter
@EqualsAndHashCode(callSuper = false)
public class ResolveViewIdentityCommand extends SelfValidating<ResolveViewIdentityCommand> {

    @NotNull
    private final UUID fileId;

    private final UUID headerUserId;

    private final String streamToken;

    public ResolveViewIdentityCommand(String fileId, UUID headerUserId, String streamToken) {
        this.fileId = UUID.fromString(fileId);
        this.headerUserId = headerUserId;
        this.streamToken = streamToken;
        this.validateSelf();
    }
}
