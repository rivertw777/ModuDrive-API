package com.moduDrive.common.infrastructure.messaging.outbox;

import java.util.UUID;

record OutboxTestEvent(UUID id, String name) {
}
