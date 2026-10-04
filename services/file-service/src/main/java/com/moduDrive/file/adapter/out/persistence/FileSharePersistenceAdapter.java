package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@PersistenceAdapter
@RequiredArgsConstructor
class FileSharePersistenceAdapter implements SaveFileSharePort, FindFileSharePort, DeleteFileSharePort {

    private final SpringDataFileShareRepository fileShareRepository;
    private final FileMapper fileMapper;

    @Override
    public FileShare saveFileShare(FileShare fileShare) {
        if (fileShare.getId() == null) {
            FileShareJpaEntity entity = new FileShareJpaEntity(
                    fileShare.getFileId(), fileShare.getOwnerId(),
                    fileShare.getSharedWithUserId(), fileShare.getRole(),
                    fileShare.getToken(), fileShare.getGranteeEmail()
            );
            try {
                return fileMapper.mapFileShareToDomain(fileShareRepository.save(entity));
            } catch (DataIntegrityViolationException e) {
                // The app-layer existsBy check in ShareFileService is best-effort against a
                // concurrent duplicate invite; the DB unique constraint is what actually closes
                // that race, so translate its violation into the same business error instead of
                // letting a raw constraint-violation message leak out as a 500.
                throw new BusinessException(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
            }
        }

        FileShareJpaEntity entity = fileShareRepository.findById(fileShare.getId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND));
        entity.applyGrantedRole(fileShare.getRole());
        // Only a pending→claimed transition (entity still null, incoming now set) should touch
        // sharedWithUserId here — an ordinary role update on an already-granted share must not
        // re-run applyClaim's side effect of clearing granteeEmail (a no-op for those rows, but
        // the guard keeps this branch doing only what its caller — ClaimPendingFileSharesService
        // — asks of it). ClaimPendingFileSharesService pre-checks for a colliding grant before
        // calling this, so no DataIntegrityViolationException is expected here.
        if (entity.getSharedWithUserId() == null && fileShare.getSharedWithUserId() != null) {
            entity.applyClaim(fileShare.getSharedWithUserId());
        }

        return fileMapper.mapFileShareToDomain(fileShareRepository.save(entity));
    }

    @Override
    public void deleteFileShare(FileShareId shareId) {
        fileShareRepository.deleteByIdBulk(shareId.value());
    }

    @Override
    public boolean existsByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId) {
        return fileShareRepository.existsByFileIdAndSharedWithUserId(fileId.value(), sharedWithUserId);
    }

    @Override
    public Optional<FileShare> findByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId) {
        return fileShareRepository.findByFileIdAndSharedWithUserId(fileId.value(), sharedWithUserId)
                .map(fileMapper::mapFileShareToDomain);
    }

    @Override
    public boolean existsByFileIdAndGranteeEmail(FileId fileId, String granteeEmail) {
        return fileShareRepository.existsByFileIdAndGranteeEmail(fileId.value(), granteeEmail);
    }

    @Override
    public Optional<FileShare> findByFileIdAndGranteeEmail(FileId fileId, String granteeEmail) {
        return fileShareRepository.findByFileIdAndGranteeEmail(fileId.value(), granteeEmail)
                .map(fileMapper::mapFileShareToDomain);
    }

    @Override
    public Optional<FileShare> findByToken(UUID token) {
        return fileShareRepository.findByToken(token)
                .map(fileMapper::mapFileShareToDomain);
    }

    @Override
    public Optional<FileShare> findByShareId(FileShareId shareId) {
        return fileShareRepository.findById(shareId.value())
                .map(fileMapper::mapFileShareToDomain);
    }

    @Override
    public List<FileShare> findByFileId(FileId fileId) {
        return fileShareRepository.findByFileId(fileId.value())
                .stream()
                .map(fileMapper::mapFileShareToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByFileIdIn(List<FileId> fileIds) {
        return fileShareRepository.existsByFileIdIn(fileIds.stream().map(FileId::value).toList());
    }

    @Override
    public List<FileShare> findBySharedWithUserId(UUID sharedWithUserId) {
        return fileShareRepository.findBySharedWithUserIdOrderByCreatedAtDesc(sharedWithUserId)
                .stream()
                .map(fileMapper::mapFileShareToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<FileShare> findPendingByGranteeEmail(String granteeEmail) {
        return fileShareRepository.findByGranteeEmailAndSharedWithUserIdIsNull(granteeEmail)
                .stream()
                .map(fileMapper::mapFileShareToDomain)
                .collect(Collectors.toList());
    }
}
