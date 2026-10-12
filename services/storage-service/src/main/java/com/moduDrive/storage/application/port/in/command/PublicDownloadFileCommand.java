package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Raw Strings, not UUIDs: an anonymous caller supplies them, and a malformed {@code fileId}/{@code key}
 * must 404 out of file-service like a wrong one rather than fail here. */
@Getter
@EqualsAndHashCode(callSuper = false)
public class PublicDownloadFileCommand extends SelfValidating<PublicDownloadFileCommand> {

    @NotBlank
    private final String fileId;

    /** Optional: a LINK-scoped entry needs no key. */
    private final String key;

    /** See {@link DownloadFileCommand#isInlinePreview()}. */
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
