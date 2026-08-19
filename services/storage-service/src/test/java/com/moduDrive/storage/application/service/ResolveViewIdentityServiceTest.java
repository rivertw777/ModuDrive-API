package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.ResolveViewIdentityCommand;
import com.moduDrive.storage.application.port.out.StreamTokenPort;
import com.moduDrive.storage.application.port.out.StreamTokenTarget;
import com.moduDrive.storage.exception.StorageExceptionCase;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ResolveViewIdentityServiceTest {

    @Mock private StreamTokenPort streamTokenPort;
    @InjectMocks private ResolveViewIdentityService resolveViewIdentityService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("헤더로 전달된 사용자 ID가 있을 때")
    class WhenHeaderUserIdIsPresent {

        @Test
        void returnsItWithoutTouchingTheTokenStore() {
            UUID resolved = resolveViewIdentityService.resolve(
                    new ResolveViewIdentityCommand(fileId.toString(), userId, null));

            assertThat(resolved).isEqualTo(userId);
        }

        @Test
        void takesPrecedenceOverAStreamTokenPresentOnTheSameRequest() {
            UUID resolved = resolveViewIdentityService.resolve(
                    new ResolveViewIdentityCommand(fileId.toString(), userId, "tok-1"));

            assertThat(resolved).isEqualTo(userId);
            then(streamTokenPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("스트림 토큰만 전달됐을 때")
    class WhenOnlyAStreamTokenIsPresent {

        @Test
        void returnsTheUserIdTheTokenWasIssuedFor() {
            given(streamTokenPort.resolve("tok-1"))
                    .willReturn(Optional.of(new StreamTokenTarget(fileId, userId)));

            UUID resolved = resolveViewIdentityService.resolve(
                    new ResolveViewIdentityCommand(fileId.toString(), null, "tok-1"));

            assertThat(resolved).isEqualTo(userId);
        }

        @Test
        void rejectsATokenIssuedForADifferentFile() {
            given(streamTokenPort.resolve("tok-1"))
                    .willReturn(Optional.of(new StreamTokenTarget(UUID.randomUUID(), userId)));

            Throwable thrown = catchThrowable(() -> resolveViewIdentityService.resolve(
                    new ResolveViewIdentityCommand(fileId.toString(), null, "tok-1")));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.UNAUTHENTICATED_VIEW_REQUEST);
        }

        @Test
        void rejectsAnUnknownOrExpiredToken() {
            given(streamTokenPort.resolve("tok-1")).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> resolveViewIdentityService.resolve(
                    new ResolveViewIdentityCommand(fileId.toString(), null, "tok-1")));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.UNAUTHENTICATED_VIEW_REQUEST);
        }
    }

    @Nested
    @DisplayName("아무 credential도 없을 때")
    class WhenNeitherIsPresent {

        @Test
        void rejectsTheRequest() {
            Throwable thrown = catchThrowable(() -> resolveViewIdentityService.resolve(
                    new ResolveViewIdentityCommand(fileId.toString(), null, null)));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.UNAUTHENTICATED_VIEW_REQUEST);
        }
    }
}
