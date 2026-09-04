package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListAllFilesCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ListAllFilesServiceTest {

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFilePort findFilePort;
    @Mock private FavoriteEnricher favoriteEnricher;
    @InjectMocks private ListAllFilesService listAllFilesService;

    @BeforeEach
    void passThroughFavoriteEnricher() {
        lenient().when(favoriteEnricher.withFavorites(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    private static final long TEST_QUOTA_BYTES = 21474836480L;

    private final UUID userId = UUID.randomUUID();
    private final ListAllFilesCommand command = new ListAllFilesCommand(userId);
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()), new NamespaceUserId(userId), new NamespaceRootPath("/1"), new NamespaceQuotaBytes(TEST_QUOTA_BYTES));

    @Nested
    @DisplayName("네임스페이스가 존재할 때")
    class WhenNamespaceExists {

        @Test
        void returnsAllFiles() {
            File image = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("photo.png"), new FilePath("/1"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            File document = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("report.pdf"), new FilePath("/1"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findByNamespaceId(any())).willReturn(List.of(image, document));

            List<File> result = listAllFilesService.listAllFiles(command);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(File::getName).containsExactly("photo.png", "report.pdf");
        }
    }

    @Nested
    @DisplayName("네임스페이스가 없을 때")
    class WhenNamespaceNotFound {

        @Test
        void throwsNamespaceNotFound() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> listAllFilesService.listAllFiles(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.NAMESPACE_NOT_FOUND);
        }
    }
}
