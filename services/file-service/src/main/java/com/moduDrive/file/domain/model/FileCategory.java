package com.moduDrive.file.domain.model;

import java.util.Arrays;
import java.util.Set;

public enum FileCategory {

    IMAGE(Set.of("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")),
    VIDEO(Set.of("mp4", "mov", "avi", "mkv", "webm")),
    DOCUMENT(Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "hwp")),
    AUDIO(Set.of("mp3", "wav", "flac", "aac", "m4a")),
    OTHER(Set.of());

    private final Set<String> extensions;

    FileCategory(Set<String> extensions) {
        this.extensions = extensions;
    }

    boolean matches(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return false;
        }
        return extensions.contains(fileName.substring(dotIndex + 1).toLowerCase());
    }

    /** OTHER when the extension belongs to no category. */
    public static FileCategory of(String fileName) {
        return Arrays.stream(values())
                .filter(category -> category != OTHER && category.matches(fileName))
                .findFirst()
                .orElse(OTHER);
    }
}
