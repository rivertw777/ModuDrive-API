package com.moduDrive.file.domain.model;

import com.moduDrive.file.domain.model.File.FileName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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

    @Nested
    @DisplayName("이름 충돌로 번호를 붙일 때")
    class WhenNumberingAConflictingName {

        @Test
        void putsTheNumberBeforeAFileExtension() {
            assertThat(new FileName("report.v2.pdf").numbered(1, false).value()).isEqualTo("report.v2 (1).pdf");
        }

        @Test
        void putsTheNumberLastForAFileWithoutExtension() {
            assertThat(new FileName("README").numbered(2, false).value()).isEqualTo("README (2)");
        }

        @Test
        @DisplayName("점으로 시작하는 파일은 확장자가 아니므로 끝에 붙인다")
        void putsTheNumberLastForADotfile() {
            assertThat(new FileName(".env").numbered(1, false).value()).isEqualTo(".env (1)");
        }

        @Test
        @DisplayName("폴더는 이름에 점이 있어도 끝에 붙인다")
        void putsTheNumberLastForADirectory() {
            assertThat(new FileName("v1.2").numbered(1, true).value()).isEqualTo("v1.2 (1)");
        }
    }

    @Nested
    @DisplayName("링크 공유를 켤 때")
    class WhenEnablingLinkSharing {

        @Test
        @DisplayName("역할은 언제나 뷰어로 고정된다 — 편집자 링크는 표현할 수 없다 (#318)")
        void alwaysGrantsViewerOnly() {
            File file = File.create(
                    new File.FileNamespaceId(UUID.randomUUID()),
                    new FileName("public.pdf"),
                    new File.FilePath("/"),
                    new File.FileOwnerId(UUID.randomUUID()),
                    new File.FileIsDirectory(false));

            file.enableLinkSharing();

            assertThat(file.getAccessScope()).isEqualTo(ShareScope.LINK);
            assertThat(file.getLinkRole()).isEqualTo(Role.VIEWER);
        }
    }
}
