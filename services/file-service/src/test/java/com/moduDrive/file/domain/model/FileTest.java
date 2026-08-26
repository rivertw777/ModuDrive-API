package com.moduDrive.file.domain.model;

import com.moduDrive.file.domain.model.File.FileName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTest {

    @Nested
    @DisplayName("파일/디렉토리 이름을 만들 때")
    class WhenCreatingAFileName {

        @Test
        void acceptsAnOrdinaryNameWithDotsInIt() {
            // "." itself is rejected, but a dot as part of a real filename must still work.
            FileName name = new FileName("report.v2.pdf");

            assertThat(name.value()).isEqualTo("report.v2.pdf");
        }

        @Test
        @DisplayName("경로 구분자나 특수 경로 세그먼트가 들어가면 거부한다 (#210)")
        void rejectsPathSeparatorsAndSpecialSegments() {
            for (String invalid : new String[]{"a/b", "a\\b", ".", "..", "", "   "}) {
                assertThatThrownBy(() -> new FileName(invalid))
                        .as("name '%s' should be rejected", invalid)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> new FileName(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
