package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.PrepareArchiveCommand;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import com.moduDrive.storage.application.port.out.ArchiveTokenPort;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort.ArchiveEntry;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class PrepareArchiveServiceTest {

    @Mock private GetArchiveEntriesPort getArchiveEntriesPort;
    @Mock private DownloadQuotaPort downloadQuotaPort;
    @Mock private ArchiveTokenPort archiveTokenPort;
    @InjectMocks private PrepareArchiveService prepareArchiveService;

    private final UUID userId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final List<UUID> picked = List.of(UUID.randomUUID());

    private ArchiveEntry file(String path, long size) {
        return new ArchiveEntry(path, fileId, "s3/" + path, 1, size);
    }

    @Nested
    @DisplayName("제한 안이고 쿼터가 남아 있을 때")
    class WhenAllowed {

        @Test
        void checksEachFilesQuotaAndIssuesAToken() {
            ArchiveRequest request = new ArchiveRequest(userId, null, picked);
            given(getArchiveEntriesPort.getArchiveEntries(request))
                    .willReturn(List.of(new ArchiveEntry("docs/", null, null, 0, 0), file("docs/a.txt", 5)));
            given(archiveTokenPort.issue(request)).willReturn("tok");

            String token = prepareArchiveService.prepare(new PrepareArchiveCommand(userId, null, picked));

            assertThat(token).isEqualTo("tok");
            then(downloadQuotaPort).should().checkWithinQuota(userId.toString(), "s3/docs/a.txt");
        }

        @Test
        void metersAnAnonymousZipOnEachFilesPublicCounter() {
            ArchiveRequest request = new ArchiveRequest(null, "k", picked);
            given(getArchiveEntriesPort.getArchiveEntries(request)).willReturn(List.of(file("a.txt", 5)));

            prepareArchiveService.prepare(new PrepareArchiveCommand(null, "k", picked));

            then(downloadQuotaPort).should().checkWithinQuota("public:" + fileId, "s3/a.txt");
        }
    }

    @Nested
    @DisplayName("한 파일이라도 쿼터를 다 썼을 때")
    class WhenQuotaExceeded {

        @Test
        void issuesNoToken() {
            given(getArchiveEntriesPort.getArchiveEntries(any())).willReturn(List.of(file("a.txt", 5)));
            willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED))
                    .given(downloadQuotaPort).checkWithinQuota(any(), any());

            Throwable thrown = catchThrowable(() -> prepareArchiveService.prepare(new PrepareArchiveCommand(userId, null, picked)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
            then(archiveTokenPort).shouldHaveNoInteractions();
        }
    }
}
