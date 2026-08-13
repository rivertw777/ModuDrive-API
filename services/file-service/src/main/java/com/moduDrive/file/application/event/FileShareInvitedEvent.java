package com.moduDrive.file.application.event;

import com.moduDrive.file.domain.model.Role;

import java.util.UUID;

public record FileShareInvitedEvent(UUID fileId, UUID granterId, UUID granteeId, Role role) {}
