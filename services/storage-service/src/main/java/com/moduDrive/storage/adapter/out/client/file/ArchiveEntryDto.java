package com.moduDrive.storage.adapter.out.client.file;

import java.util.UUID;

record ArchiveEntryDto(String path, UUID fileId, String s3Path, Integer blockCount, Long fileSize) {
}
