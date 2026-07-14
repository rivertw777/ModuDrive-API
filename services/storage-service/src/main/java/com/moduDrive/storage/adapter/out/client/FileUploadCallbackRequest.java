package com.moduDrive.storage.adapter.out.client;

public record FileUploadCallbackRequest(
        Long fileSize,
        Integer blockCount,
        String s3Path
) {}
