package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PruneFileVersionsServiceTest {

    @Mock private FindFileVersionsPort findFileVersionsPort;
    @Mock private FileVersionPruner fileVersionPruner;
    @InjectMocks private PruneFileVersionsService pruneFileVersionsService;

    private static final UUID FIRST_PAGE = new UUID(0, 0);

    @Nested
    @DisplayName("후보 파일이 한 묶음을 꽉 채울 때")
    class WhenAPageIsFull {

        @Test
        @DisplayName("마지막 파일 id 다음부터 다음 묶음을 읽는다")
        void readsTheNextPageAfterTheLastFileId() {
            List<UUID> page = IntStream.range(0, PruneFileVersionsService.BATCH_SIZE)
                    .mapToObj(i -> UUID.randomUUID()).toList();
            given(findFileVersionsPort.findFileIdsWithVersionsToPrune(any(), anyInt(), eq(FIRST_PAGE), anyInt()))
                    .willReturn(page);
            given(findFileVersionsPort.findFileIdsWithVersionsToPrune(any(), anyInt(), eq(page.getLast()), anyInt()))
                    .willReturn(List.of());

            pruneFileVersionsService.pruneFileVersions();

            then(fileVersionPruner).should().prune(eq(new FileId(page.getFirst())), any(), eq(PruneFileVersionsService.KEEP));
            then(findFileVersionsPort).should().findFileIdsWithVersionsToPrune(any(), anyInt(), eq(page.getLast()), anyInt());
        }
    }

    @Nested
    @DisplayName("한 파일 정리가 실패할 때")
    class WhenOneFileFails {

        @Test
        @DisplayName("나머지 파일은 계속 정리한다")
        void keepsPruningTheOthers() {
            UUID failing = UUID.randomUUID();
            UUID next = UUID.randomUUID();
            given(findFileVersionsPort.findFileIdsWithVersionsToPrune(any(), anyInt(), eq(FIRST_PAGE), anyInt()))
                    .willReturn(List.of(failing, next));
            given(fileVersionPruner.prune(eq(new FileId(failing)), any(), anyInt()))
                    .willThrow(new IllegalStateException("deadlock"));

            assertThatCode(() -> pruneFileVersionsService.pruneFileVersions()).doesNotThrowAnyException();

            then(fileVersionPruner).should().prune(eq(new FileId(next)), any(), anyInt());
        }
    }
}
