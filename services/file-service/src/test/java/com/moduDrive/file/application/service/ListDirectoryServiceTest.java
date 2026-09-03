package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.DirectoryPage;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.DirectorySort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.*;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ListDirectoryServiceTest {

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFilePort findFilePort;
    @InjectMocks private ListDirectoryService listDirectoryService;

    private static final long TEST_QUOTA_BYTES = 21474836480L;

    private final UUID userId = UUID.randomUUID();
    private final ListDirectoryCommand command =
            new ListDirectoryCommand(userId, "/1/docs", DirectorySort.NAME_ASC, "cursor-abc", 50);
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()), new NamespaceUserId(userId), new NamespaceRootPath("/1"), new NamespaceQuotaBytes(TEST_QUOTA_BYTES));

    @Nested
    @DisplayName("네임스페이스가 존재할 때")
    class WhenNamespaceExists {

        @Test
        void returnsThePageAndForwardsCursorSortAndLimitToThePort() {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("report.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            DirectoryPage page = new DirectoryPage(List.of(file), "next-cursor", true);

            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findDirectoryPage(any(), eq("/1/docs"), eq(DirectorySort.NAME_ASC), eq("cursor-abc"), eq(50)))
                    .willReturn(page);

            DirectoryPage result = listDirectoryService.listDirectory(command);

            assertThat(result.content()).hasSize(1);
            assertThat(result.nextCursor()).isEqualTo("next-cursor");
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        void scopesTheLookupToTheCallersNamespace() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findDirectoryPage(any(), any(), any(), any(), anyInt()))
                    .willReturn(new DirectoryPage(List.of(), null, false));

            listDirectoryService.listDirectory(command);

            ArgumentCaptor<NamespaceId> captor = ArgumentCaptor.forClass(NamespaceId.class);
            then(findFilePort).should().findDirectoryPage(captor.capture(), any(), any(), any(), anyInt());
            assertThat(captor.getValue().value()).isEqualTo(namespace.getId());
        }
    }

    @Nested
    @DisplayName("네임스페이스가 없을 때")
    class WhenNamespaceNotFound {

        @Test
        void throwsNamespaceNotFound() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> listDirectoryService.listDirectory(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.NAMESPACE_NOT_FOUND);
        }
    }
}
