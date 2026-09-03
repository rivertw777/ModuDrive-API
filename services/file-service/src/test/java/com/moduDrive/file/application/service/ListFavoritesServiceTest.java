package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFavoritesCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.*;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ListFavoritesServiceTest {

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FileFavoritePort fileFavoritePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private ListFavoritesService listFavoritesService;

    @BeforeEach
    void noSharedFavoritesByDefault() {
        lenient().when(fileFavoritePort.favoriteFileIds(any())).thenReturn(Set.of());
    }

    private static final long TEST_QUOTA_BYTES = 21474836480L;

    private final UUID userId = UUID.randomUUID();
    private final ListFavoritesCommand command = new ListFavoritesCommand(userId);
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()), new NamespaceUserId(userId), new NamespaceRootPath("/1"), new NamespaceQuotaBytes(TEST_QUOTA_BYTES));

    @Nested
    @DisplayName("네임스페이스가 존재할 때")
    class WhenNamespaceExists {

        @Test
        void returnsFavoriteFileList() {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("report.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            file.markFavorite(true);

            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findByNamespaceIdAndFavorite(any())).willReturn(List.of(file));

            List<FileView> result = listFavoritesService.listFavorites(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).file().isFavorite()).isTrue();
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
