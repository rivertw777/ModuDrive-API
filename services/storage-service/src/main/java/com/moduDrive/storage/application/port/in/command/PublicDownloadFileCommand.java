package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Kept as the raw path-segment String, not UUID: an unauthenticated caller supplies it, and a
 * malformed token must 404 out of file-service like a wrong one rather than 500 here. */
@Getter
@EqualsAndHashCode(callSuper = false)
public class PublicDownloadFileCommand extends SelfValidating<PublicDownloadFileCommand> {

    @NotBlank
    private final String token;

    /** True only for the inline-preview caller ({@code viewPublicFile}) — see
     * {@link DownloadFileCommand#isInlinePreview()}. */
    private final boolean inlinePreview;

    public PublicDownloadFileCommand(String token) {
        this(token, false);
    }

    public PublicDownloadFileCommand(String token, boolean inlinePreview) {
        this.token = token;
        this.inlinePreview = inlinePreview;
        this.validateSelf();
    }
}
