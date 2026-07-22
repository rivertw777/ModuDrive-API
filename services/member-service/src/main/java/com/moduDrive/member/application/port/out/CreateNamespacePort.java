package com.moduDrive.member.application.port.out;

import java.util.UUID;

public interface CreateNamespacePort {

    void createNamespace(UUID userId);
}
