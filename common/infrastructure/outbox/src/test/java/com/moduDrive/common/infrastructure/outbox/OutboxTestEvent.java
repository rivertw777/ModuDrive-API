package com.moduDrive.common.infrastructure.outbox;

import java.util.UUID;

record OutboxTestEvent(UUID id, String name) {
}
