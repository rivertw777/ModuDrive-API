package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.CreateDirectoryCommand;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CreateDirectoryServiceTest {

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private SaveFilePort saveFilePort;
    @InjectMocks private CreateDirectoryService createDirectoryService;

    private final CreateDirectoryCommand command = new CreateDirectoryCommand(1L, "docs", "/1", 1L);
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()), new NamespaceUserId(1L), new NamespaceRootPath("/1"));

    @Nested
    @DisplayName("네임스페이스가 존재할 때")
    class WhenNamespaceExists {

        @Test
        void createsDirectory() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = createDirectoryService.createDirectory(command);

            assertThat(result.isDirectory()).isTrue();
            assertThat(result.getName()).isEqualTo("docs");
            then(saveFilePort).should().saveFile(any(File.class));
        }
    }

    @Nested
    @DisplayName("네임스페이스가 없을 때")
    class WhenNamespaceNotFound {

        @Test
        void throwsNamespaceNotFound() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> createDirectoryService.createDirectory(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.NAMESPACE_NOT_FOUND);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
