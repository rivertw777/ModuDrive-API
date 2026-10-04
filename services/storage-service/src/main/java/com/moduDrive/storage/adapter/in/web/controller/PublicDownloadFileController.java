package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@WebAdapter
@RestController
@RequiredArgsConstructor
class PublicDownloadFileController {

    private final PublicDownloadFileUseCase publicDownloadFileUseCase;

    /** Reached through the gateway's permitAll list, so there is deliberately no X_USER_ID —
     * {@code fileId} alone authorizes a LINK-scoped entry, {@code key} authorizes a guest invite,
     * and file-service (not this service) is what decides which, if either, applies. Streamed for
     * the same reason as {@link DownloadFileController#downloadFile}. */
    @GetMapping("/api/v1/storage/public/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> publicDownloadFile(@PathVariable String fileId,
                                                                    @RequestParam(required = false) String key) {
        StreamingResponseBody body = out -> publicDownloadFileUseCase.downloadPublicStream(
                new PublicDownloadFileCommand(fileId, key), out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"")
                .body(body);
    }

    /** Inline counterpart to {@link #publicDownloadFile}, same relationship as
     * {@link DownloadFileController#viewFile} is to {@link DownloadFileController#downloadFile}. {@code fileId}/{@code key} are already the whole credential
     * between them, so it goes straight in the URL. */
    @GetMapping("/api/v1/storage/public/{fileId}/view")
    public ResponseEntity<byte[]> viewPublicFile(
            @PathVariable String fileId,
            @RequestParam(required = false) String key,
            @RequestParam String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        byte[] data = publicDownloadFileUseCase.downloadPublic(new PublicDownloadFileCommand(fileId, key, true));
        return InlineResponses.inline(data, fileName, rangeHeader);
    }
}
