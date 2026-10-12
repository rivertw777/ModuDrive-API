package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class DownloadFileController {

    private final DownloadFileUseCase downloadFileUseCase;

    /** Streamed block by block: a regular download has no size cap, so buffering it could exhaust the heap. */
    @GetMapping("/api/v1/storage/download/{fileId}")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String fileId) {
        StreamingResponseBody body = out -> downloadFileUseCase.downloadStream(new DownloadFileCommand(fileId, userId), out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"")
                .body(body);
    }

    /** Inline counterpart to {@link #downloadFile}, with a real Content-Type and {@code Range} support so
     * a &lt;video&gt;/&lt;audio&gt; element can seek. {@code fileName} is caller-supplied display text: it
     * only feeds response headers via {@link FileMimeTypes}, never a storage lookup. */
    @GetMapping("/api/v1/storage/view/{fileId}")
    public ResponseEntity<byte[]> viewFile(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String fileId,
            @RequestParam String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        DownloadFileCommand command = new DownloadFileCommand(fileId, userId, true);
        InlineResponses.RequestedRange range = InlineResponses.RequestedRange.parse(rangeHeader);
        return range == null
                ? InlineResponses.whole(downloadFileUseCase.download(command), fileName)
                : InlineResponses.partial(downloadFileUseCase.viewRange(command, range.start(), range.end()), fileName);
    }
}
