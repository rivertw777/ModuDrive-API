package com.moduDrive.storage.adapter.out.client.file;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort.ArchiveEntry;
import com.moduDrive.storage.application.port.out.GetFileVersionPort.VersionLocation;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FileClientAdapterTest {

    @Mock private FileClient fileClient;
    @InjectMocks private FileClientAdapter adapter;

    private final UUID fileId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("최신 버전을 조회할 때")
    class WhenResolvingLatestVersion {

        @Test
        void mapsTheLatestVersionAndForwardsMarkAccessed() {
            given(fileClient.getFileRevisions(anyString(), anyString(), anyInt(), anyBoolean()))
                    .willReturn(ApiResponse.success(List.of(
                            new FileVersionDto(VERSION_ID, fileId, 10L, OWNER_ID, List.of("h1", "h2", "h1")))));

            VersionLocation result = adapter.getLatestVersion(fileId, userId, true);

            assertThat(result).isEqualTo(new VersionLocation(VERSION_ID.toString(), List.of(
                    "blocks/" + OWNER_ID + "/h1", "blocks/" + OWNER_ID + "/h2", "blocks/" + OWNER_ID + "/h1")));
            then(fileClient).should().getFileRevisions(fileId.toString(), userId.toString(), 1, true);
        }
    }

    @Nested
    @DisplayName("최신 버전을 조회했는데 파일에 버전이 없을 때")
    class WhenLatestVersionMissing {

        @Test
        void throwsFileNotFoundInStorage() {
            given(fileClient.getFileRevisions(anyString(), anyString(), anyInt(), anyBoolean()))
                    .willReturn(ApiResponse.success(List.of()));

            Throwable thrown = catchThrowable(() -> adapter.getLatestVersion(fileId, userId, false));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
        }
    }

    @Nested
    @DisplayName("익명 링크로 버전을 조회할 때")
    class WhenResolvingPublicVersion {

        private final String key = UUID.randomUUID().toString();

        @Test
        void forwardsFileIdAndKeyToTheInternalRouteAndMapsTheVersion() {
            given(fileClient.getPublicFileRevisions(anyString(), anyString(), anyInt()))
                    .willReturn(ApiResponse.success(List.of(
                            new FileVersionDto(VERSION_ID, fileId, 42L, OWNER_ID, List.of("h9")))));

            VersionLocation result = adapter.getPublicVersion(fileId.toString(), key);

            assertThat(result).isEqualTo(new VersionLocation(VERSION_ID.toString(), List.of("blocks/" + OWNER_ID + "/h9")));
            then(fileClient).should().getPublicFileRevisions(fileId.toString(), key, 1);
        }

        @Test
        void throwsFileNotFoundInStorageWhenNoVersionComesBack() {
            given(fileClient.getPublicFileRevisions(anyString(), anyString(), anyInt()))
                    .willReturn(ApiResponse.success(List.of()));

            Throwable thrown = catchThrowable(() -> adapter.getPublicVersion(fileId.toString(), key));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
        }
    }

    @Nested
    @DisplayName("zip 구성을 요청할 때")
    class WhenResolvingArchiveEntries {

        @Test
        void callsTheSignedInRouteAndMapsDirectoriesWithoutALocation() {
            List<UUID> ids = List.of(fileId);
            given(fileClient.resolveArchiveEntries(new ResolveArchiveEntriesRequest(userId, ids)))
                    .willReturn(ApiResponse.success(List.of(
                            new ArchiveEntryDto("docs/", null, null, null, null, null),
                            new ArchiveEntryDto("docs/a.txt", fileId, VERSION_ID, OWNER_ID, List.of("h1"), 7L))));

            var entries = adapter.getArchiveEntries(
                    new com.moduDrive.storage.application.port.out.ArchiveRequest(userId, null, ids));

            assertThat(entries).containsExactly(
                    new ArchiveEntry("docs/", null, null, null, 0),
                    new ArchiveEntry("docs/a.txt", fileId, VERSION_ID.toString(), List.of("blocks/" + OWNER_ID + "/h1"), 7));
            assertThat(entries.get(0).isDirectory()).isTrue();
        }

        @Test
        void callsThePublicRouteForAnAnonymousRequest() {
            List<UUID> ids = List.of(fileId);
            given(fileClient.resolvePublicArchiveEntries(new ResolvePublicArchiveEntriesRequest("k", ids)))
                    .willReturn(ApiResponse.success(List.of()));

            var entries = adapter.getArchiveEntries(
                    new com.moduDrive.storage.application.port.out.ArchiveRequest(null, "k", ids));

            assertThat(entries).isEmpty();
            then(fileClient).should(org.mockito.Mockito.never()).resolveArchiveEntries(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("커밋된 블록을 물을 때")
    class WhenAskingForCommittedBlocks {

        @Test
        void returnsWhatFileServiceAnswered() {
            given(fileClient.findCommittedBlocks(new FindCommittedBlocksRequest(OWNER_ID, List.of("h1", "h2"))))
                    .willReturn(ApiResponse.success(List.of("h1")));

            assertThat(adapter.findCommitted(OWNER_ID, List.of("h1", "h2"))).containsExactly("h1");
        }
    }
}
