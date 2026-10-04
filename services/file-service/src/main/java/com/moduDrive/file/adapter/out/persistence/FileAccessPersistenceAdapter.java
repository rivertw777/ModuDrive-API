package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.file.application.port.out.FindFileAccessPort;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.domain.model.FileAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@PersistenceAdapter
@RequiredArgsConstructor
class FileAccessPersistenceAdapter implements SaveFileAccessPort, FindFileAccessPort {

    private final SpringDataFileAccessRepository fileAccessRepository;
    private final FileMapper fileMapper;

    @Override
    public void recordAccesses(UUID userId, List<UUID> fileIds, LocalDateTime accessedAt) {
        Map<UUID, FileAccessJpaEntity> existing = fileAccessRepository.findByUserIdAndFileIdIn(userId, fileIds)
                .stream()
                .collect(Collectors.toMap(FileAccessJpaEntity::getFileId, Function.identity()));
        fileAccessRepository.saveAll(fileIds.stream()
                .map(fileId -> {
                    FileAccessJpaEntity entity = existing.get(fileId);
                    if (entity == null) {
                        return new FileAccessJpaEntity(userId, fileId, accessedAt);
                    }
                    entity.touch(accessedAt);
                    return entity;
                })
                .toList());
    }

    @Override
    public void recordAccess(FileAccess fileAccess) {
        FileAccessJpaEntity entity = fileAccessRepository
                .findByUserIdAndFileId(fileAccess.getUserId(), fileAccess.getFileId())
                .map(existing -> {
                    existing.touch(fileAccess.getAccessedAt());
                    return existing;
                })
                .orElseGet(() -> new FileAccessJpaEntity(
                        fileAccess.getUserId(), fileAccess.getFileId(), fileAccess.getAccessedAt()));
        // saveAndFlush (not save): forces the uk_file_access_user_file violation from a
        // concurrent insert-race to surface here, synchronously, instead of at commit time
        // after the caller (RecordFileAccessService) has already returned — that's what lets
        // its try/catch actually catch it instead of the exception leaking into the response.
        fileAccessRepository.saveAndFlush(entity);
    }

    @Override
    public List<FileAccess> findByUserIdOrderByAccessedAtDesc(UUID userId, int limit) {
        return fileAccessRepository.findByUserIdOrderByAccessedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(fileMapper::mapFileAccessToDomain)
                .collect(Collectors.toList());
    }
}
