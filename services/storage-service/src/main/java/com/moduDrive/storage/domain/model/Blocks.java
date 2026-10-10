package com.moduDrive.storage.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/** Spec 001: a block is stored once per owner under the SHA-256 of its raw bytes. */
public final class Blocks {

    /** How long an uploaded block waits to be committed. Past it, a commit asks for the block
     * again and the uncommitted-upload sweep deletes it. */
    public static final Duration UPLOAD_TTL = Duration.ofHours(24);

    private Blocks() {
    }

    public static String key(UUID ownerId, String hash) {
        return "blocks/" + ownerId + "/" + hash;
    }

    /** Lowercase hex, 64 characters — the form clients send and keys use. */
    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
