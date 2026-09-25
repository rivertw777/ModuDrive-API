package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveNamespacePort;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CreateNamespaceServiceTest {

    private static final long TEST_QUOTA_BYTES = 21474836480L;

    @Mock
    private FindNamespacePort findNamespacePort;
    @Mock
    private SaveNamespacePort saveNamespacePort;
    private CreateNamespaceService createNamespaceService;

    private final UUID userId = UUID.randomUUID();
    private final CreateNamespaceCommand command =
            new CreateNamespaceCommand(new NamespaceUserId(userId));

    @BeforeEach
    void setUp() {
        createNamespaceService = new CreateNamespaceService(findNamespacePort, saveNamespacePort, TEST_QUOTA_BYTES);
    }

    @Nested
    @DisplayName("네임스페이스가 존재하지 않을 때")
    class WhenNamespaceDoesNotExist {

        @Test
        void createsAndReturnsNamespace() {
            given(findNamespacePort.findByUserId(command.getUserId())).willReturn(Optional.empty());
            given(saveNamespacePort.saveNamespace(any(Namespace.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Namespace result = createNamespaceService.createNamespace(command);

            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getRootPath()).isEqualTo("/" + userId);
            assertThat(result.getQuotaBytes()).isEqualTo(TEST_QUOTA_BYTES);
            then(saveNamespacePort).should().saveNamespace(any(Namespace.class));
        }
    }

    @Nested
    @DisplayName("네임스페이스가 이미 존재할 때")
    class WhenNamespaceAlreadyExists {

        @Test
        @DisplayName("새로 만들지 않고 기존 네임스페이스를 반환한다 (가입 이벤트 재전달)")
        void returnsTheExistingNamespace() {
            Namespace existing = Namespace.create(command.getUserId(), new Namespace.NamespaceQuotaBytes(TEST_QUOTA_BYTES));
            given(findNamespacePort.findByUserId(command.getUserId())).willReturn(Optional.of(existing));

            Namespace result = createNamespaceService.createNamespace(command);

            assertThat(result).isSameAs(existing);
            then(saveNamespacePort).shouldHaveNoInteractions();
        }
    }
}
