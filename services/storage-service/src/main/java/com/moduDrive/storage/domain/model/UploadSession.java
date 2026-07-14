package com.moduDrive.storage.domain.model;

import lombok.Getter;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

@Getter
public class UploadSession {

    private final UUID sessionId;
    private final UUID fileId;
    private final long ownerId;
    private final int totalChunks;
    private final ConcurrentMap<Integer, byte[]> chunks;
    private boolean completed;

    private UploadSession(UUID sessionId, UUID fileId, long ownerId, int totalChunks) {
        this.sessionId = sessionId;
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.totalChunks = totalChunks;
        this.chunks = new ConcurrentHashMap<>();
        this.completed = false;
    }

    public static UploadSession create(UUID fileId, long ownerId, int totalChunks) {
        return new UploadSession(UUID.randomUUID(), fileId, ownerId, totalChunks);
    }

    public void addChunk(int chunkIndex, byte[] data) {
        chunks.put(chunkIndex, data);
    }

    public boolean isAllChunksReceived() {
        return IntStream.range(0, totalChunks).allMatch(chunks::containsKey);
    }

    public void markCompleted() {
        this.completed = true;
    }
}
