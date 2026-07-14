package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListDirectoryCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
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

@ExtendWith(MockitoExtension.class)
class ListDirectoryServiceTest {

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFilePort findFilePort;
    @InjectMocks private ListDirectoryService listDirectoryService;

    private final ListDirectoryCommand command = new ListDirectoryCommand(1L, "/1/docs");
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()), new NamespaceUserId(1L), new NamespaceRootPath("/1"));

    @Nested
    @DisplayName("네임스페이스가 존재할 때")
    class WhenNamespaceExists {

        @Test
        void returnsFileList() {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("report.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(1L), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findByNamespaceIdAndPath(any(), any())).willReturn(List.of(file));

            List<File> result = listDirectoryService.listDirectory(command);

            assertThat(result).hasSize(1);
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
