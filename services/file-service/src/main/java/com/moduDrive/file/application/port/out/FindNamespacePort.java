package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;

import java.util.Optional;

public interface FindNamespacePort {

    Optional<Namespace> findByUserId(NamespaceUserId userId);
}
