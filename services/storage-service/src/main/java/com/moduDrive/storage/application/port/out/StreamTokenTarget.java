package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public record StreamTokenTarget(UUID fileId, UUID userId) {
}
