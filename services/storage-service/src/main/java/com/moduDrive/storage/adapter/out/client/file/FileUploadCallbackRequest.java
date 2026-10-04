package com.moduDrive.storage.adapter.out.client.file;

record FileUploadCallbackRequest(
        Long fileSize,
        Integer blockCount,
        String s3Path
) {}
