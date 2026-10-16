package com.moduDrive.storage.domain.model;

import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/** Spec 001: a block is stored once per owner under the SHA-256 of its raw bytes. */
public final class Blocks {

    /** How long after its upload URL was signed a block can be committed. Past it, a commit asks for
     * the block again and the uncommitted-upload sweep deletes it. */
    public static final Duration UPLOAD_TTL = Duration.ofHours(24);
    /** How long a presigned PUT URL works (spec 001 2-4). */
    public static final Duration UPLOAD_URL_TTL = Duration.ofHours(1);

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private Blocks() {
    }

    public static String key(UUID ownerId, String hash) {
        return "blocks/" + ownerId + "/" + hash;
    }

    /** Lowercase hex, 64 characters — the form clients send and keys use. */
    public static boolean isHash(String hash) {
        return hash != null && HASH.matcher(hash).matches();
    }

    /** The same SHA-256 as S3 takes and reports it ({@code x-amz-checksum-sha256}). */
    public static String sha256Base64(String hash) {
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hash));
    }
}
