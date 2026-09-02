package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.adapter.in.web.dto.InitResumableUploadRequest;
import com.moduDrive.storage.adapter.in.web.dto.ResumableUploadSessionResponse;
import com.moduDrive.storage.adapter.in.web.dto.StreamTokenResponse;
import com.moduDrive.storage.application.port.in.command.CompleteResumableUploadCommand;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;
import com.moduDrive.storage.application.port.in.command.IssueStreamTokenCommand;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.command.ResolveViewIdentityCommand;
import com.moduDrive.storage.application.port.in.command.SimpleUploadCommand;
import com.moduDrive.storage.application.port.in.command.UploadChunkCommand;
import com.moduDrive.storage.application.port.in.usecase.CompleteResumableUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.IssueStreamTokenUseCase;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.ResolveViewIdentityUseCase;
import com.moduDrive.storage.application.port.in.usecase.SimpleUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class StorageController {

    private final SimpleUploadUseCase simpleUploadUseCase;
    private final InitResumableUploadUseCase initResumableUploadUseCase;
    private final UploadChunkUseCase uploadChunkUseCase;
    private final CompleteResumableUploadUseCase completeResumableUploadUseCase;
    private final DownloadFileUseCase downloadFileUseCase;
    private final PublicDownloadFileUseCase publicDownloadFileUseCase;
    private final IssueStreamTokenUseCase issueStreamTokenUseCase;
    private final ResolveViewIdentityUseCase resolveViewIdentityUseCase;

    @PostMapping("/api/v1/storage/upload")
    public ApiResponse<Void> simpleUpload(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam String fileId,
            @RequestParam MultipartFile file) throws IOException {
        simpleUploadUseCase.simpleUpload(new SimpleUploadCommand(fileId, userId, file.getBytes()));
        return ApiResponse.success();
    }

    @PostMapping("/api/v1/storage/upload/resumable")
    public ApiResponse<ResumableUploadSessionResponse> initResumableUpload(
            @RequestHeader("X_USER_ID") UUID userId,
            @Valid @RequestBody InitResumableUploadRequest request) {
        UUID sessionId = initResumableUploadUseCase.initResumableUpload(
                new InitResumableUploadCommand(request.fileId(), userId, request.totalChunks(), request.fileSize()));
        return ApiResponse.success(ResumableUploadSessionResponse.of(sessionId));
    }

    @PutMapping("/api/v1/storage/upload/resumable/{sessionId}")
    public ApiResponse<Void> uploadChunk(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String sessionId,
            @RequestParam int chunkIndex,
            @RequestParam MultipartFile chunk) throws IOException {
        uploadChunkUseCase.uploadChunk(
                new UploadChunkCommand(sessionId, userId, chunkIndex, chunk.getBytes()));
        return ApiResponse.success();
    }

    @PostMapping("/api/v1/storage/upload/resumable/{sessionId}/complete")
    public ApiResponse<Void> completeResumableUpload(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String sessionId) {
        completeResumableUploadUseCase.completeResumableUpload(
                new CompleteResumableUploadCommand(sessionId, userId));
        return ApiResponse.success();
    }

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

    /** Reached through the gateway's permitAll list, so there is deliberately no X_USER_ID —
     * the link token is the whole credential and file-service is what validates it. Streamed for
     * the same reason as {@link #downloadFile}. */
    @GetMapping("/api/v1/storage/public/{token}/download")
    public ResponseEntity<StreamingResponseBody> publicDownloadFile(@PathVariable String token) {
        StreamingResponseBody body = out -> publicDownloadFileUseCase.downloadPublicStream(new PublicDownloadFileCommand(token), out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + token + "\"")
                .body(body);
    }

    /** Mints a short-lived, single-file identity token so a native &lt;video&gt;/&lt;audio&gt;
     * element can hit {@link #viewFile} by URL alone — those elements can't attach an
     * Authorization header, so this stands in for one on that request only. Ordinary Bearer auth
     * (blob-fetch preview for text/image) still works on {@link #viewFile} unchanged; this is
     * purely an alternate credential for the direct-{@code src} case. */
    @PostMapping("/api/v1/storage/stream-token")
    public ApiResponse<StreamTokenResponse> issueStreamToken(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam String fileId) {
        String token = issueStreamTokenUseCase.issue(new IssueStreamTokenCommand(fileId, userId));
        return ApiResponse.success(new StreamTokenResponse(token));
    }

    /** Inline counterpart to {@link #downloadFile}: same permission check (reuses
     * {@link DownloadFileUseCase} as-is) once the caller's identity is resolved — either the
     * gateway-injected header (normal Bearer auth) or a {@code streamToken} from
     * {@link #issueStreamToken} — but a real Content-Type and {@code inline} disposition.
     * {@code fileName} is caller-supplied display text, not trusted file identity — it only
     * ever feeds a response header via {@link FileMimeTypes}, never a storage lookup. Honors
     * {@code Range} so a &lt;video&gt;/&lt;audio&gt; element can seek without pulling the whole
     * (already fully in-memory) file over the wire again. */
    @GetMapping("/api/v1/storage/view/{fileId}")
    public ResponseEntity<byte[]> viewFile(
            @RequestHeader(value = "X_USER_ID", required = false) UUID userId,
            @PathVariable String fileId,
            @RequestParam String fileName,
            @RequestParam(required = false) String streamToken,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        UUID resolvedUserId = resolveViewIdentityUseCase.resolve(
                new ResolveViewIdentityCommand(fileId, userId, streamToken));
        byte[] data = downloadFileUseCase.download(new DownloadFileCommand(fileId, resolvedUserId, true));
        return inline(data, fileName, rangeHeader);
    }

    /** Inline counterpart to {@link #publicDownloadFile}, same relationship as {@link #viewFile}
     * is to {@link #downloadFile}. The link token already is the full credential, so no
     * streamToken dance is needed here — it goes straight in the URL either way. */
    @GetMapping("/api/v1/storage/public/{token}/view")
    public ResponseEntity<byte[]> viewPublicFile(
            @PathVariable String token,
            @RequestParam String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        byte[] data = publicDownloadFileUseCase.downloadPublic(new PublicDownloadFileCommand(token, true));
        return inline(data, fileName, rangeHeader);
    }

    /** {@link #publicDownloadFile} / {@link #viewPublicFile} for one file nested under a
     * link-shared <b>folder</b>: same permitAll route family, the folder token plus the
     * descendant's id, file-service checks the entry really is under that folder. */
    @GetMapping("/api/v1/storage/public/{token}/entry/{entryId}/download")
    public ResponseEntity<StreamingResponseBody> publicDownloadDescendant(
            @PathVariable String token,
            @PathVariable String entryId) {
        StreamingResponseBody body = out -> publicDownloadFileUseCase.downloadPublicStream(
                new PublicDownloadFileCommand(token, entryId, false), out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entryId + "\"")
                .body(body);
    }

    @GetMapping("/api/v1/storage/public/{token}/entry/{entryId}/view")
    public ResponseEntity<byte[]> viewPublicDescendant(
            @PathVariable String token,
            @PathVariable String entryId,
            @RequestParam String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        byte[] data = publicDownloadFileUseCase.downloadPublic(new PublicDownloadFileCommand(token, entryId, true));
        return inline(data, fileName, rangeHeader);
    }

    /** No Range header: the full body, exactly as before. With one: the requested byte slice as
     * a {@code 206} — the file is already fully assembled in memory by the time this runs (see
     * {@link com.moduDrive.storage.application.service.DownloadFileService}), so slicing the
     * array is all a "region" means here; there's no lazily-read {@link org.springframework.core.io.Resource}
     * underneath to justify {@code ResourceRegion}/{@code ResourceRegionHttpMessageConverter}.
     * This only saves wire bytes and lets the browser seek, not server-side memory. A malformed
     * Range is treated as no Range rather than rejected, since a botched seek shouldn't break
     * plain playback. */
    private static ResponseEntity<byte[]> inline(byte[] data, String fileName, String rangeHeader) {
        MediaType contentType = FileMimeTypes.contentType(fileName);
        String disposition = FileMimeTypes.inlineDisposition(fileName);
        HttpRange range = parseFirstRange(rangeHeader, data.length);

        if (range == null) {
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(data);
        }

        long start = range.getRangeStart(data.length);
        long end = range.getRangeEnd(data.length);
        byte[] slice = Arrays.copyOfRange(data, (int) start, (int) end + 1);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + data.length)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(slice);
    }

    private static HttpRange parseFirstRange(String rangeHeader, int contentLength) {
        if (rangeHeader == null) {
            return null;
        }
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            // A multi-range request answered with only the first slice would report 206 success
            // while silently dropping the rest — treating it as no-range returns the whole body
            // instead, which the client can always re-seek against.
            if (ranges.size() != 1) {
                return null;
            }
            HttpRange range = ranges.get(0);
            // A start beyond the actual content is a client bug (e.g. stale Range from a
            // previous, different file), not a seek to honor as partial content.
            return range.getRangeStart(contentLength) < contentLength ? range : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
