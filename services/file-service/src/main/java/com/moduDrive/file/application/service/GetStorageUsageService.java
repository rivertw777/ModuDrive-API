package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetStorageUsageCommand;
import com.moduDrive.file.application.port.in.usecase.GetStorageUsageUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class GetStorageUsageService implements GetStorageUsageUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;

    @Transactional(readOnly = true)
    @Override
    public StorageUsage getStorageUsage(GetStorageUsageCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        long usedBytes = findFilePort.sumFileSizeByNamespaceId(new NamespaceId(namespace.getId()));
        return new StorageUsage(usedBytes, namespace.getQuotaBytes());
    }
}
