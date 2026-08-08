package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetStorageUsageCommand;

public interface GetStorageUsageUseCase {

    // ponytail: every member gets the same fixed quota today; promote to a
    // per-member column (member-service) if paid tiers are ever needed.
    long DEFAULT_QUOTA_BYTES = 20L * 1024 * 1024 * 1024;

    long getUsedBytes(GetStorageUsageCommand command);
}
