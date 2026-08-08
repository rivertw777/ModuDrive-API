package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetStorageUsageCommand;

public interface GetStorageUsageUseCase {

    StorageUsage getStorageUsage(GetStorageUsageCommand command);

    record StorageUsage(long usedBytes, long quotaBytes) {}
}
