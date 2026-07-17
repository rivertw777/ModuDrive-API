package com.moduDrive.storage.adapter.in.web.dto;

import java.util.UUID;

public record ResumableUploadSessionResponse(String sessionId) {

    public static ResumableUploadSessionResponse of(UUID sessionId) {
        return new ResumableUploadSessionResponse(sessionId.toString());
    }
}
