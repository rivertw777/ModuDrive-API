package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Kept as the raw path-segment String, not UUID: an unauthenticated caller supplies it, and a
 * malformed token/entryId must 404 out of file-service like a wrong one rather than 500 here. */
@Getter
@EqualsAndHashCode(callSuper = false)
public class PublicDownloadFileCommand extends SelfValidating<PublicDownloadFileCommand> {

    @NotBlank
    private final String token;

    /** Set only when the token belongs to a folder and the caller wants one file nested under it;
     * null for a direct file link. */
    private final String entryId;

    /** True only for the inline-preview caller ({@code viewPublicFile}) — see
     * {@link DownloadFileCommand#isInlinePreview()}. */
    private final boolean inlinePreview;

    public PublicDownloadFileCommand(String token) {
        this(token, null, false);
    }

    public PublicDownloadFileCommand(String token, boolean inlinePreview) {
        this(token, null, inlinePreview);
    }

    public PublicDownloadFileCommand(String token, String entryId, boolean inlinePreview) {
        this.token = token;
        this.entryId = entryId;
        this.inlinePreview = inlinePreview;
        this.validateSelf();
    }

    public boolean hasEntry() {
        return entryId != null && !entryId.isBlank();
    }
}
