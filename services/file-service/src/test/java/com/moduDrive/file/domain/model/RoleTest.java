package com.moduDrive.file.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Nested
    @DisplayName("EDITOR 권한을 가졌을 때")
    class WhenRoleIsEditor {

        @Test
        void satisfiesBothEditorAndViewer() {
            assertThat(Role.EDITOR.satisfies(Role.EDITOR)).isTrue();
            assertThat(Role.EDITOR.satisfies(Role.VIEWER)).isTrue();
        }
    }

    @Nested
    @DisplayName("VIEWER 권한을 가졌을 때")
    class WhenRoleIsViewer {

        @Test
        void satisfiesViewerButNotEditor() {
            assertThat(Role.VIEWER.satisfies(Role.VIEWER)).isTrue();
            assertThat(Role.VIEWER.satisfies(Role.EDITOR)).isFalse();
        }
    }
}
