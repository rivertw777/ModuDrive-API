package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** Several blocks in one request (spec 001 2장 4번): each with the hash the client says it has. */
@Getter
public class UploadBlocksCommand {

    private final UUID userId;
    private final List<Block> blocks;

    public UploadBlocksCommand(UUID userId, List<Block> blocks) {
        this.userId = userId;
        this.blocks = List.copyOf(blocks);
    }

    public record Block(String hash, byte[] data) {}
}
