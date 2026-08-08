package com.moduDrive.file.adapter.in.web.dto;

public record StorageUsageResponse(
        long usedBytes,
        long quotaBytes
) {
}
