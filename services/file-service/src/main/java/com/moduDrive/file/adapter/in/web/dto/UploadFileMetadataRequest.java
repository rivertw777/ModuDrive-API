package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadFileMetadataRequest(
        @NotBlank String name,
        @NotBlank String path,
        @NotNull Boolean directory,
        /** "기존 파일 대체" vs "두 파일 모두 유지": true overwrites the active file already at this
         * name/path as a new version; omitted/false rejects that case so the client can ask. */
        Boolean replaceExisting
) {}
