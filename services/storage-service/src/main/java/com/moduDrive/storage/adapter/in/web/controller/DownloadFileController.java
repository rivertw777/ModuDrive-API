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

    /** Streamed block-by-block instead of assembled into one byte[] — a regular download has no
     * size cap (unlike inline preview), so holding the whole file in memory would let a handful
     * of concurrent large downloads exhaust the heap. */
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

    /** Inline counterpart to {@link #downloadFile}: same permission check (reuses
     * {@link DownloadFileUseCase} as-is), but a real Content-Type and {@code inline} disposition.
     * A native &lt;video&gt;/&lt;audio&gt; {@code src} request carries the session cookie on its
     * own, so the gateway resolves X_USER_ID for it like any other request.
     * {@code fileName} is caller-supplied display text, not trusted file identity — it only
     * ever feeds a response header via {@link FileMimeTypes}, never a storage lookup. Honors
     * {@code Range} so a &lt;video&gt;/&lt;audio&gt; element can seek without pulling the whole
     * (already fully in-memory) file over the wire again. */
    @GetMapping("/api/v1/storage/view/{fileId}")
    public ResponseEntity<byte[]> viewFile(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String fileId,
            @RequestParam String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        byte[] data = downloadFileUseCase.download(new DownloadFileCommand(fileId, userId, true));
        return InlineResponses.inline(data, fileName, rangeHeader);
    }
}
