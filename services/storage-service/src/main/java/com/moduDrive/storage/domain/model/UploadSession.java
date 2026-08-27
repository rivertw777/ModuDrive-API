package com.moduDrive.storage.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Getter
public class UploadSession {

    private final UUID sessionId;
    private final UUID fileId;
    private final UUID ownerId;
    private final int totalChunks;
    private final ConcurrentMap<Integer, byte[]> chunks;
    private final AtomicLong totalBytes;
    private final Instant createdAt;
    private boolean completed;

    private UploadSession(UUID sessionId, UUID fileId, UUID ownerId, int totalChunks) {
        this.sessionId = sessionId;
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.totalChunks = totalChunks;
        this.chunks = new ConcurrentHashMap<>();
        this.totalBytes = new AtomicLong(0);
        this.createdAt = Instant.now();
        this.completed = false;
    }

    public static UploadSession create(UUID fileId, UUID ownerId, int totalChunks) {
        return new UploadSession(UUID.randomUUID(), fileId, ownerId, totalChunks);
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public void addChunk(int chunkIndex, byte[] data) {
        byte[] previous = chunks.put(chunkIndex, data);
        totalBytes.addAndGet(data.length - (previous == null ? 0 : previous.length));
    }

    public boolean isAllChunksReceived() {
        return IntStream.range(0, totalChunks).allMatch(chunks::containsKey);
    }

    public void markCompleted() {
        this.completed = true;
    }
}
