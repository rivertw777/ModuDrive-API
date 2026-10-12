package com.moduDrive.storage.adapter.in.web.controller;

import org.springframework.http.MediaType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Extension &rarr; MIME type for the inline preview endpoints. Mirrors file-service's
 * {@code FileCategory} IMAGE/AUDIO/VIDEO sets plus txt/md. {@code svg} is left out on purpose: as a
 * top-level document it runs {@code <script>}, so it stays octet-stream.
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
            Map.entry("txt", MediaType.valueOf("text/plain;charset=UTF-8")),
            Map.entry("md", MediaType.valueOf("text/plain;charset=UTF-8"))
    );

    private FileMimeTypes() {
    }

    static MediaType contentType(String fileName) {
        String ext = extension(fileName);
        return ext == null ? MediaType.APPLICATION_OCTET_STREAM
                : BY_EXTENSION.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }

    /** Sends both an ASCII {@code filename=} fallback and the RFC 6266 UTF-8 {@code filename*}, since
     * plain {@code filename=} can't carry non-ASCII (Korean) names reliably. */
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
