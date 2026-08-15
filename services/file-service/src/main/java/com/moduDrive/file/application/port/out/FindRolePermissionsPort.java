package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;

import java.util.Set;

public interface FindRolePermissionsPort {

    Set<Permission> findByRole(Role role);
}
