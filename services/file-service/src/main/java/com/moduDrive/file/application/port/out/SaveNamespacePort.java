package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.Namespace;

public interface SaveNamespacePort {

    Namespace saveNamespace(Namespace namespace);
}
