package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The seeder is instantiated by hand rather than {@code @Import}ed: as an {@code ApplicationRunner}
 * it would fire during context startup and commit rows outside the test's rollback, leaving every
 * case here running against already-seeded tables. */
@DataJpaTest
class RolePermissionSeederTest {

    @Autowired private SpringDataFileRoleRepository fileRoleRepository;
    @Autowired private SpringDataFilePermissionRepository filePermissionRepository;
    @Autowired private SpringDataFileRolePermissionRepository fileRolePermissionRepository;

    private RolePermissionSeeder seeder() {
        return new RolePermissionSeeder(fileRoleRepository, filePermissionRepository, fileRolePermissionRepository);
    }

    private Map<Role, Set<Permission>> storedMatrix() {
        Map<UUID, Role> roleById = fileRoleRepository.findAll().stream()
                .collect(Collectors.toMap(FileRoleJpaEntity::getId, FileRoleJpaEntity::getRoleName));
        Map<UUID, Permission> permissionById = filePermissionRepository.findAll().stream()
                .collect(Collectors.toMap(FilePermissionJpaEntity::getId, FilePermissionJpaEntity::getPermissionName));
        return fileRolePermissionRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        grant -> roleById.get(grant.getFileRoleId()),
                        Collectors.mapping(grant -> permissionById.get(grant.getFilePermissionId()), Collectors.toSet())));
    }

    @Nested
    @DisplayName("테이블이 비어 있을 때")
    class WhenTableIsEmpty {

        @Test
        void seedsTheDocumentedMatrix() {
            seeder().run(null);

            assertThat(storedMatrix()).containsOnly(
                    Map.entry(Role.VIEWER, Set.of(Permission.READ, Permission.DOWNLOAD)),
                    Map.entry(Role.EDITOR, Set.of(Permission.READ, Permission.DOWNLOAD, Permission.RENAME)));
        }
    }

    @Nested
    @DisplayName("이미 행이 존재할 때")
    class WhenTableAlreadyHasRows {

        @Test
        void leavesExistingRowsUntouched() {
            fileRoleRepository.saveAndFlush(new FileRoleJpaEntity(Role.VIEWER));

            seeder().run(null);

            assertThat(fileRoleRepository.count()).isEqualTo(1);
        }

        @Test
        void isIdempotentAcrossRepeatedBoots() {
            RolePermissionSeeder seeder = seeder();
            seeder.run(null);
            fileRoleRepository.flush();
            long afterFirstBoot = fileRoleRepository.count();

            seeder.run(null);

            assertThat(fileRoleRepository.count()).isEqualTo(afterFirstBoot);
        }
    }
}
