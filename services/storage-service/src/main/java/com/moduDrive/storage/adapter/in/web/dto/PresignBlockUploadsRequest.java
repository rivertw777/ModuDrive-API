package com.moduDrive.storage.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Hash format, size and count are the use case's to check (spec 001 6-2). */
public record PresignBlockUploadsRequest(@NotNull List<@Valid @NotNull Block> blocks) {

    public record Block(@NotNull String hash, int size) {}
}
