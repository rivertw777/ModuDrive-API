package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds {@code file_role}, {@code file_permission} and the {@code file_role_permission} grant
 * matrix on boot. Production/dev Postgres gets the same rows from {@code init.sql} instead (this
 * repo has no Flyway/Liquibase); this runner exists so H2-backed tests get the matrix too, since
 * {@code docker-entrypoint-initdb.d} scripts never run against H2. Either path leaves the tables
 * in the same state, so the idempotency check below just needs to hold against both.
 */
@Component
@RequiredArgsConstructor
class RolePermissionSeeder implements ApplicationRunner {

    /** Plain values, not entity instances: JPA writes generated ids into entities it persists, so
     * a shared static list of them would be re-saved as detached rows on any second invocation. */
    private static final Map<Role, Set<Permission>> DEFAULTS = Map.of(
            Role.VIEWER, Set.of(Permission.READ, Permission.DOWNLOAD),
            Role.EDITOR, Set.of(Permission.READ, Permission.DOWNLOAD, Permission.RENAME));

    private final SpringDataFileRoleRepository fileRoleRepository;
    private final SpringDataFilePermissionRepository filePermissionRepository;
    private final SpringDataFileRolePermissionRepository fileRolePermissionRepository;

    /** All-or-nothing on an empty file_role table: once a row exists the matrix is considered
     * owned by whoever edited it, so a partial re-seed can never silently re-grant a revoked
     * permission. {@code @Transactional} makes that true across a failure mid-seed too — a role
     * saved but its permissions not (yet) reached rolls back the whole batch, rather than leaving
     * {@code file_role} non-empty (which the count-check above treats as "already seeded") with an
     * incomplete matrix behind it. */
    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        if (fileRoleRepository.count() > 0) {
            return;
        }

        Map<Role, UUID> roleIds = new EnumMap<>(Role.class);
        for (Role role : DEFAULTS.keySet()) {
            roleIds.put(role, fileRoleRepository.save(new FileRoleJpaEntity(role)).getId());
        }

        Map<Permission, UUID> permissionIds = new EnumMap<>(Permission.class);
        for (Permission permission : Permission.values()) {
            permissionIds.put(permission, filePermissionRepository.save(new FilePermissionJpaEntity(permission)).getId());
        }

        List<FileRolePermissionJpaEntity> rows = DEFAULTS.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(permission -> new FileRolePermissionJpaEntity(
                                roleIds.get(entry.getKey()), permissionIds.get(permission))))
                .toList();
        fileRolePermissionRepository.saveAll(rows);
    }
}
