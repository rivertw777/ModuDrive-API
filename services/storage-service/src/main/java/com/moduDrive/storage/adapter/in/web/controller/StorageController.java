package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.adapter.in.web.dto.InitResumableUploadRequest;
import com.moduDrive.storage.adapter.in.web.dto.ResumableUploadSessionResponse;
import com.moduDrive.storage.application.port.in.command.CompleteResumableUploadCommand;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;
import com.moduDrive.storage.application.port.in.command.SimpleUploadCommand;
import com.moduDrive.storage.application.port.in.command.UploadChunkCommand;
import com.moduDrive.storage.application.port.in.usecase.CompleteResumableUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.SimpleUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

import java.io.IOException;
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

    @PostMapping("/api/v1/storage/upload")
    public ApiResponse<Void> simpleUpload(
            @RequestParam String fileId,
            @RequestParam MultipartFile file) throws IOException {
        simpleUploadUseCase.simpleUpload(new SimpleUploadCommand(fileId, file.getBytes()));
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

    @GetMapping("/api/v1/storage/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {
        byte[] data = downloadFileUseCase.download(new DownloadFileCommand(fileId));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"")
                .body(data);
    }
}
