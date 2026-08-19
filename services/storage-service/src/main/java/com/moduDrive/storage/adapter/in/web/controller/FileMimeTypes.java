package com.moduDrive.storage.adapter.in.web.controller;

import org.springframework.http.MediaType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Extension &rarr; MIME type for the inline preview endpoints. Mirrors file-service's
 * {@code FileCategory.java} IMAGE/AUDIO/VIDEO sets plus DOCUMENT's txt. This map only decides
 * whether the browser recognizes the response as playable media at all — the heap/size concern
 * that used to keep video out of it is handled server-side by
 * {@link com.moduDrive.storage.application.service.BlockAssembler#requireWithinInlinePreviewLimit},
 * not by this map. {@code svg} is the deliberate exception: it stays octet-stream because
 * it's active content (executes {@code <script>} when rendered as a top-level document, unlike
 * an {@code <img>}-embedded one), so no safe inline preview exists for it regardless of size.
 */
final class FileMimeTypes {

    private static final Map<String, MediaType> BY_EXTENSION = Map.ofEntries(
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("webp", MediaType.valueOf("image/webp")),
            Map.entry("bmp", MediaType.valueOf("image/bmp")),
            Map.entry("mp3", MediaType.valueOf("audio/mpeg")),
            Map.entry("wav", MediaType.valueOf("audio/wav")),
            Map.entry("flac", MediaType.valueOf("audio/flac")),
            Map.entry("aac", MediaType.valueOf("audio/aac")),
            Map.entry("m4a", MediaType.valueOf("audio/mp4")),
            Map.entry("mp4", MediaType.valueOf("video/mp4")),
            Map.entry("webm", MediaType.valueOf("video/webm")),
            Map.entry("mov", MediaType.valueOf("video/quicktime")),
            Map.entry("avi", MediaType.valueOf("video/x-msvideo")),
            Map.entry("mkv", MediaType.valueOf("video/x-matroska")),
            Map.entry("txt", MediaType.valueOf("text/plain;charset=UTF-8"))
    );

    private FileMimeTypes() {
    }

    static MediaType contentType(String fileName) {
        String ext = extension(fileName);
        return ext == null ? MediaType.APPLICATION_OCTET_STREAM
                : BY_EXTENSION.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }

    /** RFC 6266 {@code filename*}: plain {@code filename=} can't carry non-ASCII (Korean file
     * names are the common case here) correctly across browsers, so both are sent — the ASCII
     * fallback for user agents that ignore filename*, the UTF-8 percent-encoded form for the rest. */
    static String inlineDisposition(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String asciiFallback = fileName.replaceAll("[^\\x20-\\x7E]", "_");
        return "inline; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
