package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Kept as raw path/query Strings, not UUID: an unauthenticated caller supplies them, and a
 * malformed {@code fileId}/{@code key} must 404 out of file-service like a wrong one rather than
 * 500 here. {@code fileId} may be the directly-shared file or one nested under a shared folder;
 * {@code key} is the capability that authorizes it. */
@Getter
@EqualsAndHashCode(callSuper = false)
public class PublicDownloadFileCommand extends SelfValidating<PublicDownloadFileCommand> {

    @NotBlank
    private final String fileId;

    /** Not @NotBlank on purpose: a missing/blank/malformed key is rejected by file-service (which
     * this always calls first, via getPublicVersion) as a uniform FILE_NOT_FOUND, the same 404 a
     * wrong key gets — validating it here would instead surface a distinguishable 400/500. */
    private final String key;

    /** True only for the inline-preview caller ({@code viewPublicFile}) — see
     * {@link DownloadFileCommand#isInlinePreview()}. */
    private final boolean inlinePreview;

    public PublicDownloadFileCommand(String fileId, String key) {
        this(fileId, key, false);
    }

    public PublicDownloadFileCommand(String fileId, String key, boolean inlinePreview) {
        this.fileId = fileId;
        this.key = key;
        this.inlinePreview = inlinePreview;
        this.validateSelf();
    }
}
