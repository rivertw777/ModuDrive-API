package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.usecase.OpenArchiveUseCase.Archive;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import com.moduDrive.storage.application.port.out.ArchiveTokenPort;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort.ArchiveEntry;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class OpenArchiveServiceTest {

    @Mock private ArchiveTokenPort archiveTokenPort;
    @Mock private GetArchiveEntriesPort getArchiveEntriesPort;
    @Mock private RetrieveBlocksPort retrieveBlocksPort;
    @Mock private DownloadQuotaPort downloadQuotaPort;
    @InjectMocks private OpenArchiveService openArchiveService;

    private final UUID userId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final ArchiveRequest request = new ArchiveRequest(userId, null, List.of(UUID.randomUUID()));

    @Nested
    @DisplayName("유효한 토큰일 때")
    class WhenTokenValid {

        @Test
        void streamsAZipWithFoldersAndFileContentsAndMetersEachFile() throws Exception {
            given(archiveTokenPort.redeem("tok")).willReturn(Optional.of(request));
            given(getArchiveEntriesPort.getArchiveEntries(request)).willReturn(List.of(
                    new ArchiveEntry("사진/", null, null, 0, 0),
                    new ArchiveEntry("사진/빈폴더/", null, null, 0, 0),
                    new ArchiveEntry("사진/a.txt", fileId, "s3/a", 2, 5)));
            willAnswer(invocation -> {
                OutputStream out = invocation.getArgument(2);
                out.write("he".getBytes());
                out.write("llo".getBytes());
                return null;
            }).given(retrieveBlocksPort).streamBlocks(eq("s3/a"), eq(2), any());

            Archive archive = openArchiveService.open("tok");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            archive.writer().writeTo(out);

            assertThat(archive.fileName()).isEqualTo("사진.zip");
            assertThat(unzip(out.toByteArray())).containsExactly(
                    Map.entry("사진/", ""), Map.entry("사진/빈폴더/", ""), Map.entry("사진/a.txt", "hello"));
            then(downloadQuotaPort).should().recordUsage(userId.toString(), "s3/a", 5);
        }

        @Test
        void namesASeveralItemZipWithATimestamp() {
            given(archiveTokenPort.redeem("tok")).willReturn(Optional.of(request));
            given(getArchiveEntriesPort.getArchiveEntries(request)).willReturn(List.of(
                    new ArchiveEntry("a.txt", fileId, "s3/a", 1, 1),
                    new ArchiveEntry("docs/", null, null, 0, 0)));

            Archive archive = openArchiveService.open("tok");

            assertThat(archive.fileName()).matches("ModuDrive-\\d{8}-\\d{6}\\.zip");
            then(retrieveBlocksPort).should(org.mockito.Mockito.never()).streamBlocks(any(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("없거나 이미 쓴 토큰일 때")
    class WhenTokenInvalid {

        @Test
        void throwsBeforeResolvingAnything() {
            given(archiveTokenPort.redeem("used")).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> openArchiveService.open("used"));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(StorageExceptionCase.ARCHIVE_TOKEN_INVALID);
            then(getArchiveEntriesPort).shouldHaveNoInteractions();
        }
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                entries.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
