package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindRolePermissionsPort;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The whole role/permission reference data — {@code file_role}, {@code file_permission} and the
 * {@code file_role_permission} grant matrix — is read once and held in memory: it is a handful of
 * rows that only change when someone edits the tables by hand, so paying a query per permission
 * check or per share row would buy nothing.
 * <p>
 * Loaded on first use rather than in the constructor because {@link RolePermissionSeeder} is an
 * {@code ApplicationRunner} — it fires after the context is refreshed, so a constructor read would
 * see empty tables on a fresh database and deny everything until the next restart.
 * <p>
 * ponytail: a restart is the way to pick up manual row edits; add a refresh hook only once an
 * admin-facing edit feature actually exists.
 * <p>
 * {@code findRoleId}/{@code findRole} (the {@code file_share.granted_role_id} FK translation) are
 * plain package-private methods, not a formal out port: their only callers are {@link FileMapper}
 * and {@link FilePersistenceAdapter}, both in this same adapter package — routing that through the
 * application layer via a port nothing in application actually calls would be indirection with no
 * seam. {@link FindRolePermissionsPort} stays a real port because {@code FileAccessGuard}, an
 * application-layer service, is a genuine consumer of it.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class RolePermissionPersistenceAdapter implements FindRolePermissionsPort {

    private final SpringDataFileRoleRepository fileRoleRepository;
    private final SpringDataFilePermissionRepository filePermissionRepository;
    private final SpringDataFileRolePermissionRepository fileRolePermissionRepository;

    private volatile Directory directory;

    @Override
    public Set<Permission> findByRole(Role role) {
        return directory().permissionsByRole.getOrDefault(role, Set.of());
    }

    UUID findRoleId(Role role) {
        UUID id = directory().roleIdByRole.get(role);
        if (id == null) {
            throw new BusinessException(FileExceptionCase.FILE_ROLE_NOT_FOUND);
        }
        return id;
    }

    Role findRole(UUID roleId) {
        Role role = directory().roleByRoleId.get(roleId);
        if (role == null) {
            throw new BusinessException(FileExceptionCase.FILE_ROLE_NOT_FOUND);
        }
        return role;
    }

    /** Never memoizes an empty read: the web server can start accepting requests before
     * {@link RolePermissionSeeder} (an {@code ApplicationRunner}) has run, and caching an empty
     * snapshot from that window would deny every permission check for the process's whole
     * lifetime. An empty load retries on the very next call instead. */
    private Directory directory() {
        Directory loaded = directory;
        if (loaded == null || loaded.isEmpty()) {
            loaded = load();
            directory = loaded;
        }
        return loaded;
    }

    /** All roles first, then all permissions, then the junction rows mapped through both — the
     * three-query shape the schema's three tables call for, done once. */
    private Directory load() {
        Map<UUID, Role> roleByRoleId = new HashMap<>();
        Map<Role, UUID> roleIdByRole = new EnumMap<>(Role.class);
        for (FileRoleJpaEntity role : fileRoleRepository.findAll()) {
            roleByRoleId.put(role.getId(), role.getRoleName());
            roleIdByRole.put(role.getRoleName(), role.getId());
        }

        Map<UUID, Permission> permissionByPermissionId = new HashMap<>();
        for (FilePermissionJpaEntity permission : filePermissionRepository.findAll()) {
            permissionByPermissionId.put(permission.getId(), permission.getPermissionName());
        }

        Map<Role, Set<Permission>> permissionsByRole = new EnumMap<>(Role.class);
        for (FileRolePermissionJpaEntity grant : fileRolePermissionRepository.findAll()) {
            Role role = roleByRoleId.get(grant.getFileRoleId());
            Permission permission = permissionByPermissionId.get(grant.getFilePermissionId());
            if (role == null || permission == null) {
                continue;
            }
            permissionsByRole.computeIfAbsent(role, r -> EnumSet.noneOf(Permission.class)).add(permission);
        }

        return new Directory(permissionsByRole, roleIdByRole, roleByRoleId);
    }

    private record Directory(
            Map<Role, Set<Permission>> permissionsByRole,
            Map<Role, UUID> roleIdByRole,
            Map<UUID, Role> roleByRoleId) {

        boolean isEmpty() {
            return roleIdByRole.isEmpty();
        }
    }
}
