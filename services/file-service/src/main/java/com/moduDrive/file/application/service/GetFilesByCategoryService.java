package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetFilesByCategoryCommand;
import com.moduDrive.file.application.port.in.usecase.GetFilesByCategoryUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileCategory;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class GetFilesByCategoryService implements GetFilesByCategoryUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final FavoriteEnricher favoriteEnricher;

    @Transactional(readOnly = true)
    @Override
    public List<File> getFilesByCategory(GetFilesByCategoryCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        List<File> inCategory = findFilePort.findByNamespaceId(new NamespaceId(namespace.getId()))
                .stream()
                .filter(file -> FileCategory.of(file.getName()) == command.getCategory())
                .toList();
        return favoriteEnricher.withFavorites(command.getUserId().value(), inCategory);
    }
}
