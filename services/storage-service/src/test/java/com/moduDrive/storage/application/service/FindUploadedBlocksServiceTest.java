package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.out.FindScheduledUploadsPort;
import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FindUploadedBlocksServiceTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Mock private FindScheduledUploadsPort findScheduledUploadsPort;
    @Mock private FindUploadedBlocksPort findUploadedBlocksPort;
    @InjectMocks private FindUploadedBlocksService service;

    private final UUID ownerId = UUID.randomUUID();

    @Test
    @DisplayName("24시간 안에 URL을 받은 블록만 S3에 묻고, 그 답을 돌려준다")
    void asksS3OnlyAboutBlocksSignedWithinTheWindow() {
        given(findScheduledUploadsPort.findScheduledSince(eq(ownerId), eq(List.of(HASH_A, HASH_B)), any())).willReturn(Set.of(HASH_A));
        given(findUploadedBlocksPort.findUploaded(ownerId, Set.of(HASH_A))).willReturn(Map.of(HASH_A, 4));
        Instant before = Instant.now();

        assertThat(service.findUploadedBlocks(ownerId, List.of(HASH_A, HASH_B))).containsExactly(Map.entry(HASH_A, 4));

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        then(findScheduledUploadsPort).should().findScheduledSince(eq(ownerId), any(), since.capture());
        assertThat(since.getValue()).isBetween(before.minus(Blocks.UPLOAD_TTL), Instant.now().minus(Blocks.UPLOAD_TTL));
    }

    @Test
    @DisplayName("URL을 받은 블록이 없으면 S3에 묻지 않는다 (새 파일의 첫 commit)")
    void asksS3NothingWhenNoBlockWasSigned() {
        given(findScheduledUploadsPort.findScheduledSince(any(), any(), any())).willReturn(Set.of());

        assertThat(service.findUploadedBlocks(ownerId, List.of(HASH_A))).isEmpty();
        then(findUploadedBlocksPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("해시 형식이 아니면 아무것도 묻지 않고 INVALID_BLOCK")
    void rejectsAMalformedHash() {
        Throwable thrown = catchThrowable(() -> service.findUploadedBlocks(ownerId, List.of(HASH_A, "zz")));

        assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.INVALID_BLOCK);
        then(findScheduledUploadsPort).shouldHaveNoInteractions();
        then(findUploadedBlocksPort).shouldHaveNoInteractions();
    }
}
