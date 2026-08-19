package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.in.command.IssueStreamTokenCommand;
import com.moduDrive.storage.application.port.out.StreamTokenPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IssueStreamTokenServiceTest {

    @Mock private StreamTokenPort streamTokenPort;
    @InjectMocks private IssueStreamTokenService issueStreamTokenService;

    @Test
    void delegatesToTheTokenStoreAndReturnsItsToken() {
        UUID fileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(streamTokenPort.issue(fileId, userId)).willReturn("tok-1");

        String token = issueStreamTokenService.issue(new IssueStreamTokenCommand(fileId.toString(), userId));

        assertThat(token).isEqualTo("tok-1");
    }
}
