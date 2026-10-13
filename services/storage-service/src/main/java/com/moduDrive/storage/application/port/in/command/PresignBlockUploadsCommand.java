package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** Blocks the caller is about to PUT to S3 (spec 001 6): each with the hash and size it says it has. */
@Getter
public class PresignBlockUploadsCommand {

    private final UUID userId;
    private final List<Block> blocks;

    public PresignBlockUploadsCommand(UUID userId, List<Block> blocks) {
        this.userId = userId;
        this.blocks = List.copyOf(blocks);
    }

    public record Block(String hash, int size) {}
}
