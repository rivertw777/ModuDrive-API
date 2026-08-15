package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The adapter is instantiated by hand rather than {@code @Import}ed: its {@code directory} cache
 * is singleton state that would otherwise survive across every {@code @Nested} test method here
 * (each rolled back independently by {@code @DataJpaTest}), so one test's cached ids would leak
 * into the next and any assertion tying a resolved id back to what was just seeded would be
 * testing a stale snapshot instead of the real behavior. */
@DataJpaTest
class RolePermissionPersistenceAdapterTest {

    @Autowired private SpringDataFileRoleRepository fileRoleRepository;
    @Autowired private SpringDataFilePermissionRepository filePermissionRepository;
    @Autowired private SpringDataFileRolePermissionRepository fileRolePermissionRepository;

    private RolePermissionPersistenceAdapter adapter() {
        return new RolePermissionPersistenceAdapter(fileRoleRepository, filePermissionRepository, fileRolePermissionRepository);
    }

    private UUID seedRole(Role role) {
        return fileRoleRepository.saveAndFlush(new FileRoleJpaEntity(role)).getId();
    }

    private UUID seedPermission(Permission permission) {
        return filePermissionRepository.saveAndFlush(new FilePermissionJpaEntity(permission)).getId();
    }

    @Nested
    @DisplayName("매트릭스 행이 저장되어 있을 때")
    class WhenRowsExist {

        private UUID viewerId;
        private UUID editorId;
        private RolePermissionPersistenceAdapter adapter;

        private void seed() {
            viewerId = seedRole(Role.VIEWER);
            editorId = seedRole(Role.EDITOR);
            UUID readId = seedPermission(Permission.READ);
            UUID downloadId = seedPermission(Permission.DOWNLOAD);
            UUID renameId = seedPermission(Permission.RENAME);
            fileRolePermissionRepository.saveAllAndFlush(List.of(
                    new FileRolePermissionJpaEntity(viewerId, readId),
                    new FileRolePermissionJpaEntity(viewerId, downloadId),
                    new FileRolePermissionJpaEntity(editorId, renameId)));
            adapter = adapter();
        }

        @Test
        void returnsEveryPermissionGrantedToTheRole() {
            seed();

            assertThat(adapter.findByRole(Role.VIEWER)).containsExactlyInAnyOrder(Permission.READ, Permission.DOWNLOAD);
        }

        @Test
        void readsTheTablesOnlyOnceAcrossCalls() {
            seed();

            adapter.findByRole(Role.VIEWER);
            fileRolePermissionRepository.deleteAllInBatch();

            // The cached matrix is what answers here — proving the adapter is not querying per call.
            assertThat(adapter.findByRole(Role.EDITOR)).containsExactly(Permission.RENAME);
        }

        @Test
        void resolvesRoleIdsInBothDirections() {
            seed();

            assertThat(adapter.findRoleId(Role.VIEWER)).isEqualTo(viewerId);
            assertThat(adapter.findRole(editorId)).isEqualTo(Role.EDITOR);
        }

        @Test
        void throwsFileRoleNotFoundForARoleAbsentFromTheTable() {
            // Only VIEWER seeded — EDITOR has no row at all, not just no permissions.
            viewerId = seedRole(Role.VIEWER);
            adapter = adapter();

            Throwable thrown = catchThrowable(() -> adapter.findRoleId(Role.EDITOR));

            assertThat(thrown).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("role_id가 알 수 없는 값일 때")
    class WhenRoleIdIsUnknown {

        @Test
        void throwsFileRoleNotFound() {
            seedRole(Role.VIEWER);

            Throwable thrown = catchThrowable(() -> adapter().findRole(UUID.randomUUID()));

            assertThat(thrown).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("애플리케이션 시작 직후, 시더가 아직 실행되지 않았을 때")
    class WhenTablesAreEmpty {

        @Test
        void doesNotMemoizeAnEmptyReadAndPicksUpRowsSeededAfterward() {
            RolePermissionPersistenceAdapter adapter = adapter();

            assertThat(adapter.findByRole(Role.VIEWER)).isEmpty();

            UUID viewerId = seedRole(Role.VIEWER);
            fileRolePermissionRepository.saveAndFlush(
                    new FileRolePermissionJpaEntity(viewerId, seedPermission(Permission.READ)));

            // If the first (empty) read had been cached, this would still return empty.
            assertThat(adapter.findByRole(Role.VIEWER)).containsExactly(Permission.READ);
        }
    }
}
