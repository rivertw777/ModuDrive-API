package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase.UploadedItem;

import java.util.List;
import java.util.UUID;

public record UploadBatchResponse(List<Item> items) {

    /** {@code relativePath} is the request's own path; {@code name}/{@code path} are where the
     * entry will land, which differ when a top-level name was numbered on a conflict.
     * {@code fileId} is set only for an existing entry being replaced or merged into. */
    public record Item(String relativePath, UUID fileId, String name, String path,
                       boolean directory, boolean replaced) {}

    public static UploadBatchResponse from(List<UploadedItem> planned) {
        return new UploadBatchResponse(planned.stream()
                .map(p -> new Item(p.relativePath(), p.fileId(), p.name(), p.path(), p.directory(), p.replaced()))
                .toList());
    }
}
