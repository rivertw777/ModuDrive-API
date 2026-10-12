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

    /** Gateway permitAll, so no X_USER_ID: {@code fileId} authorizes a LINK-scoped entry, {@code key} a
     * guest invite, and file-service decides which applies. Streamed like
     * {@link DownloadFileController#downloadFile}. */
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

    /** Inline counterpart to {@link #publicDownloadFile}, like {@link DownloadFileController#viewFile}. */
    @GetMapping("/api/v1/storage/public/{fileId}/view")
    public ResponseEntity<byte[]> viewPublicFile(
            @PathVariable String fileId,
            @RequestParam(required = false) String key,
            @RequestParam String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        PublicDownloadFileCommand command = new PublicDownloadFileCommand(fileId, key, true);
        InlineResponses.RequestedRange range = InlineResponses.RequestedRange.parse(rangeHeader);
        return range == null
                ? InlineResponses.whole(publicDownloadFileUseCase.downloadPublic(command), fileName)
                : InlineResponses.partial(publicDownloadFileUseCase.viewPublicRange(command, range.start(), range.end()), fileName);
    }
}
