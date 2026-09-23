package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.adapter.in.web.dto.ArchiveTokenResponse;
import com.moduDrive.storage.adapter.in.web.dto.PrepareArchiveRequest;
import com.moduDrive.storage.application.port.in.command.PrepareArchiveCommand;
import com.moduDrive.storage.application.port.in.usecase.OpenArchiveUseCase;
import com.moduDrive.storage.application.port.in.usecase.OpenArchiveUseCase.Archive;
import com.moduDrive.storage.application.port.in.usecase.PrepareArchiveUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Zip download of several files and/or folders, in two steps: prepare (checked, answers JSON),
 * then a plain link to the returned token, so the browser downloads natively with progress and
 * without buffering the whole zip in a Blob. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class ArchiveController {

    private final PrepareArchiveUseCase prepareArchiveUseCase;
    private final OpenArchiveUseCase openArchiveUseCase;

    @PostMapping("/api/v1/storage/archive")
    public ApiResponse<ArchiveTokenResponse> prepareArchive(
            @RequestHeader("X_USER_ID") UUID userId,
            @Valid @RequestBody PrepareArchiveRequest request) {
        String token = prepareArchiveUseCase.prepare(new PrepareArchiveCommand(userId, null, request.fileIds()));
        return ApiResponse.success(new ArchiveTokenResponse(token));
    }

    /** Gateway permitAll: no X_USER_ID, the picked ids (LINK scope) or {@code key} (guest invite)
     * are the credential, judged by file-service. */
    @PostMapping("/api/v1/storage/public/archive")
    public ApiResponse<ArchiveTokenResponse> preparePublicArchive(
            @RequestParam(required = false) String key,
            @Valid @RequestBody PrepareArchiveRequest request) {
        String token = prepareArchiveUseCase.prepare(new PrepareArchiveCommand(null, key, request.fileIds()));
        return ApiResponse.success(new ArchiveTokenResponse(token));
    }

    /** Under the public prefix (gateway permitAll) for both flavors: the single-use token is the
     * whole credential, since a navigated link can't carry a Bearer header. */
    @GetMapping("/api/v1/storage/public/archive/{token}")
    public ResponseEntity<StreamingResponseBody> downloadArchive(@PathVariable String token) {
        Archive archive = openArchiveUseCase.open(token);
        StreamingResponseBody body = archive.writer()::writeTo;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archive.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}
