package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFavoritesCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.*;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ListFavoritesServiceTest {

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFilePort findFilePort;
    @Mock private FileFavoritePort fileFavoritePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private ListFavoritesService listFavoritesService;

    private static final long TEST_QUOTA_BYTES = 21474836480L;

    private final UUID userId = UUID.randomUUID();
    private final ListFavoritesCommand command = new ListFavoritesCommand(userId);
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()), new NamespaceUserId(userId), new NamespaceRootPath("/1"),
            new NamespaceQuotaBytes(TEST_QUOTA_BYTES));

    private File file(UUID id, UUID ownerId, FileStatus status) {
        return File.withId(new FileId(id), new FileNamespaceId(namespace.getId()),
                new FileName("f.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(ownerId), null, null, status, new FileIsDirectory(false));
    }

    @Nested
    @DisplayName("네임스페이스가 존재할 때")
    class WhenNamespaceExists {

        @Test
        @DisplayName("소유 파일과 공유 파일을 file_favorite 순서대로 돌려준다")
        void returnsOwnedAndSharedInFavoriteOrder() {
            UUID sharedId = UUID.randomUUID();
            UUID ownedId = UUID.randomUUID();
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(fileFavoritePort.favoriteFileIds(userId))
                    .willReturn(new LinkedHashSet<>(List.of(sharedId, ownedId)));
            given(findFilePort.findById(new FileId(sharedId)))
                    .willReturn(Optional.of(file(sharedId, UUID.randomUUID(), FileStatus.UPLOADED)));
            given(findFilePort.findById(new FileId(ownedId)))
                    .willReturn(Optional.of(file(ownedId, userId, FileStatus.UPLOADED)));
            // Non-null role = still reachable (a direct or an inherited folder grant).
            given(fileAccessGuard.effectiveRole(any(File.class), eq(userId))).willReturn(Role.EDITOR);

            List<FileView> result = listFavoritesService.listFavorites(command);

            assertThat(result).extracting(v -> v.file().getId()).containsExactly(sharedId, ownedId);
            assertThat(result).allMatch(v -> v.file().isFavorite());
            // shared row carries the caller's role; owned row does not
            assertThat(result.get(0).callerRole()).isEqualTo(Role.EDITOR);
            assertThat(result.get(1).callerRole()).isNull();
        }

        @Test
        @DisplayName("파일이 사라진 별표(고아 행)는 조용히 건너뛴다")
        void skipsOrphanedStarWhoseFileIsGone() {
            UUID goneId = UUID.randomUUID();
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(fileFavoritePort.favoriteFileIds(userId)).willReturn(new LinkedHashSet<>(List.of(goneId)));
            given(findFilePort.findById(new FileId(goneId))).willReturn(Optional.empty());

            assertThat(listFavoritesService.listFavorites(command)).isEmpty();
        }

        @Test
        @DisplayName("더 이상 접근 불가한 파일(공유 해제 등)은 별표가 남아 있어도 제외한다")
        void dropsStarredFileNoLongerReachable() {
            UUID sharedId = UUID.randomUUID();
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(fileFavoritePort.favoriteFileIds(userId)).willReturn(new LinkedHashSet<>(List.of(sharedId)));
            given(findFilePort.findById(new FileId(sharedId)))
                    .willReturn(Optional.of(file(sharedId, UUID.randomUUID(), FileStatus.UPLOADED)));
            given(fileAccessGuard.effectiveRole(any(File.class), eq(userId))).willReturn(null);

            assertThat(listFavoritesService.listFavorites(command)).isEmpty();
        }

        @Test
        @DisplayName("휴지통에 있는 별표 파일은 제외한다")
        void dropsDeletedStarredFile() {
            UUID ownedId = UUID.randomUUID();
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(fileFavoritePort.favoriteFileIds(userId)).willReturn(new LinkedHashSet<>(List.of(ownedId)));
            given(findFilePort.findById(new FileId(ownedId)))
                    .willReturn(Optional.of(file(ownedId, userId, FileStatus.DELETED)));

            assertThat(listFavoritesService.listFavorites(command)).isEmpty();
        }
    }

    @Nested
    @DisplayName("네임스페이스가 없을 때")
    class WhenNamespaceNotFound {

        @Test
        void throwsNamespaceNotFound() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> listFavoritesService.listFavorites(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.NAMESPACE_NOT_FOUND);
        }
    }
}
