package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase.UploadedItem;

import java.util.List;
import java.util.UUID;

public record UploadBatchResponse(List<Item> items) {

    /** {@code relativePath} is the request's own path; {@code name}/{@code path} are where the
     * entry actually landed, which differ when a top-level name was numbered on a conflict. */
    public record Item(String relativePath, UUID fileId, String name, String path,
                       boolean directory, boolean replaced) {}

    public static UploadBatchResponse from(List<UploadedItem> uploaded) {
        return new UploadBatchResponse(uploaded.stream()
                .map(u -> new Item(u.relativePath(), u.file().getId(), u.file().getName(),
                        u.file().getPath(), u.file().isDirectory(), u.replaced()))
                .toList());
    }
}
