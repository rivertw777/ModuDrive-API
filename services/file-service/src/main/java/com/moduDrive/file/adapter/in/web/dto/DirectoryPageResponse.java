package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.DirectoryPage;

import java.util.List;

public record DirectoryPageResponse(List<FileResponse> content, String nextCursor, boolean hasNext) {

    public static DirectoryPageResponse from(DirectoryPage page) {
        return new DirectoryPageResponse(
                page.content().stream().map(FileResponse::from).toList(),
                page.nextCursor(),
                page.hasNext());
    }
}
