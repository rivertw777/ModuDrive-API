package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** A group of files committed together (spec 001 2장 3번); the service also caps the group's
 * blocklists at 1,280 hashes in total. {@code directories} are the folders of an uploaded folder
 * tree that no file creates (2-2) — either list may be left out, not both. */
public record CommitFileUploadRequest(
        @Size(max = 100) List<@NotNull @Valid File> files,
        @Size(max = 1000) List<@NotNull @Valid Directory> directories
) {
    public CommitFileUploadRequest {
        files = files == null ? List.of() : files;
        directories = directories == null ? List.of() : directories;
    }

    @AssertTrue(message = "올릴 파일이나 폴더가 없습니다.")
    public boolean isNotEmpty() {
        return !files.isEmpty() || !directories.isEmpty();
    }

    public record Directory(
            /** Full path of the folder it goes in, "/" for the drive root. */
            @NotBlank @Size(max = 255) String path,
            @NotBlank @Size(max = 255) String name
    ) {}

    public record File(
            /** Full path of the folder the file goes in, "/" for the drive root. */
            @NotBlank @Size(max = 255) String path,
            @NotBlank @Size(max = 255) String name,
            @NotNull UUID uploadId,
            // Zero is a real file (an empty .gitkeep in an uploaded folder), with an empty blocklist.
            @NotNull @PositiveOrZero Long size,
            // 1,280 = 5GB / 4MB, the most blocks a file can have; the service checks the exact count.
            @NotNull @Size(max = 1280) List<@NotNull @Pattern(regexp = "[0-9a-f]{64}") String> blocklist
    ) {}
}
