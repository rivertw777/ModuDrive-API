package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.Namespace.NamespaceId;

public interface UpdateNamespaceUsagePort {

    /** Adds {@code delta} to the drive's usage unless that would go past its quota; false then, and
     * nothing changes. A delta of zero or less always applies. Locks the namespace row until the
     * transaction ends, so call it last in a transaction (after the block and file locks). */
    boolean addUsedBytes(NamespaceId namespaceId, long delta);
}
