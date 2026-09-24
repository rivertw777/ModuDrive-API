package com.moduDrive.storage.adapter.out.client;

import java.util.UUID;

public record ArchiveEntryDto(String path, UUID fileId, String s3Path, Integer blockCount, Long fileSize) {
}
