package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveNamespacePort;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CreateNamespaceServiceTest {

    @Mock
    private FindNamespacePort findNamespacePort;
    @Mock
    private SaveNamespacePort saveNamespacePort;
    @InjectMocks
    private CreateNamespaceService createNamespaceService;

    private final CreateNamespaceCommand command =
            new CreateNamespaceCommand(new NamespaceUserId(1L));

    @Nested
    @DisplayName("네임스페이스가 존재하지 않을 때")
    class WhenNamespaceDoesNotExist {

        @Test
        void createsAndReturnsNamespace() {
            given(findNamespacePort.existsByUserId(command.getUserId())).willReturn(false);
            given(saveNamespacePort.saveNamespace(any(Namespace.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Namespace result = createNamespaceService.createNamespace(command);

            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getRootPath()).isEqualTo("/1");
            then(saveNamespacePort).should().saveNamespace(any(Namespace.class));
        }
    }

    @Nested
    @DisplayName("네임스페이스가 이미 존재할 때")
    class WhenNamespaceAlreadyExists {

        @Test
        void throwsBusinessException() {
            given(findNamespacePort.existsByUserId(command.getUserId())).willReturn(true);

            Throwable thrown = catchThrowable(() -> createNamespaceService.createNamespace(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.NAMESPACE_ALREADY_EXISTS);
            then(saveNamespacePort).shouldHaveNoInteractions();
        }
    }
}
