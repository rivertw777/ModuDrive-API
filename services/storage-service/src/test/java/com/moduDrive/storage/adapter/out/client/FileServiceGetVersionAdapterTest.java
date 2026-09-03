package com.moduDrive.storage.adapter.out.client;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
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
class FileServiceGetVersionAdapterTest {

    @Mock private FileServiceFeignClient feignClient;
    @InjectMocks private FileServiceGetVersionAdapter adapter;

    private final UUID fileId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("파일에 여러 버전이 있을 때")
    class WhenFileHasVersions {

        @Test
        void returnsEveryVersionAsALocation() {
            FileVersionDto v1 = new FileVersionDto(UUID.randomUUID(), fileId, 10L, 2, "path/v1");
            FileVersionDto v2 = new FileVersionDto(UUID.randomUUID(), fileId, 20L, 4, "path/v2");
            given(feignClient.getAllFileVersions(anyString(), anyString()))
                    .willReturn(ApiResponse.success(List.of(v1, v2)));

            List<VersionLocation> result = adapter.getAllVersions(fileId, userId);

            assertThat(result).containsExactly(
                    new VersionLocation("path/v1", 2),
                    new VersionLocation("path/v2", 4));
        }
    }

    @Nested
    @DisplayName("버전이 없을 때")
    class WhenNoVersionsExist {

        @Test
        void returnsEmptyList() {
            given(feignClient.getAllFileVersions(anyString(), anyString()))
                    .willReturn(ApiResponse.success(List.of()));

            assertThat(adapter.getAllVersions(fileId, userId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("최신 버전을 조회할 때")
    class WhenResolvingLatestVersion {

        @Test
        void mapsTheLatestVersionAndForwardsMarkAccessed() {
            given(feignClient.getFileRevisions(anyString(), anyString(), anyInt(), anyBoolean()))
                    .willReturn(ApiResponse.success(List.of(
                            new FileVersionDto(UUID.randomUUID(), fileId, 10L, 3, "path/v1"))));

            VersionLocation result = adapter.getLatestVersion(fileId, userId, true);

            assertThat(result).isEqualTo(new VersionLocation("path/v1", 3));
            then(feignClient).should().getFileRevisions(fileId.toString(), userId.toString(), 1, true);
        }
    }

    @Nested
    @DisplayName("최신 버전을 조회했는데 파일에 버전이 없을 때")
    class WhenLatestVersionMissing {

        @Test
        void throwsFileNotFoundInStorage() {
            given(feignClient.getFileRevisions(anyString(), anyString(), anyInt(), anyBoolean()))
                    .willReturn(ApiResponse.success(List.of()));

            Throwable thrown = catchThrowable(() -> adapter.getLatestVersion(fileId, userId, false));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
        }
    }
}
