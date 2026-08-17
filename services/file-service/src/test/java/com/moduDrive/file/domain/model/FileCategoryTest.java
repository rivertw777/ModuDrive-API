package com.moduDrive.file.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileCategoryTest {

    @Nested
    @DisplayName("확장자가 카테고리에 속할 때")
    class WhenExtensionBelongsToCategory {

        @Test
        void matchesReturnsTrue() {
            assertThat(FileCategory.IMAGE.matches("photo.PNG")).isTrue();
            assertThat(FileCategory.VIDEO.matches("clip.mp4")).isTrue();
            assertThat(FileCategory.DOCUMENT.matches("report.docx")).isTrue();
            assertThat(FileCategory.AUDIO.matches("song.mp3")).isTrue();
        }
    }

    @Nested
    @DisplayName("확장자가 카테고리에 속하지 않을 때")
    class WhenExtensionDoesNotBelongToCategory {

        @Test
        void matchesReturnsFalse() {
            assertThat(FileCategory.IMAGE.matches("report.pdf")).isFalse();
        }

        @Test
        void extensionlessFileReturnsFalse() {
            assertThat(FileCategory.IMAGE.matches("README")).isFalse();
        }

        @Test
        void trailingDotReturnsFalse() {
            assertThat(FileCategory.IMAGE.matches("weird.")).isFalse();
        }
    }

    @Nested
    @DisplayName("of()로 카테고리를 판별할 때")
    class WhenResolvingCategory {

        @Test
        void knownExtensionResolvesToMatchingCategory() {
            assertThat(FileCategory.of("photo.png")).isEqualTo(FileCategory.IMAGE);
        }

        @Test
        void unknownExtensionResolvesToOther() {
            assertThat(FileCategory.of("archive.zip")).isEqualTo(FileCategory.OTHER);
            assertThat(FileCategory.of("README")).isEqualTo(FileCategory.OTHER);
        }
    }
}
