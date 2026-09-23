package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.domain.model.FileVersion;

/** One entry of a zip download: {@code path} is where it lands inside the zip. A directory ends in
 * {@code /} and has no {@code version} — it's there so an empty folder still shows up. */
public record ArchiveEntry(String path, FileVersion version) {

    public boolean isDirectory() {
        return version == null;
    }
}
