package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.command.UploadBatchCommand.ConflictResolution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UploadBatchRequest(
        /** Full path of the folder being uploaded into, "/" for the drive root. */
        @NotBlank String path,
        @NotEmpty @Size(max = 5000, message = "한 번에 5,000개까지 올릴 수 있습니다.")
        List<@NotNull @Valid Item> items,
        /** Only on a retry after 409: the user's choice per conflicting top-level file name. */
        Map<String, @NotNull ConflictResolution> resolutions
) {
    public record Item(
            /** Relative to {@code path}, "/"-separated, e.g. "사진/2024/a.jpg". Capped at the
             * column length up front: it also bounds how deep one entry can nest, so a single
             * "a/a/a/…" item can't make parsing blow up quadratically. */
            @NotBlank @Size(max = 255) String relativePath,
            @NotNull Boolean directory,
            /** Required for a file (checked by the service), ignored for a folder. */
            @PositiveOrZero Long size
    ) {}
}
