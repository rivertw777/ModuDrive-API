package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase.CommitResult;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReferenceBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileVersionPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.exception.FileExceptionCase;
import com.moduDrive.file.fixture.FileVersionTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.moduDrive.file.fixture.FileVersionTestFixture.HASH_A;
import static com.moduDrive.file.fixture.FileVersionTestFixture.HASH_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CommitFileUploadServiceTest {

    private static final int BLOCK = FileVersion.BLOCK_SIZE;
    private static final String HASH_C = "c".repeat(64);

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @Mock private SaveFileVersionPort saveFileVersionPort;
    @Mock private LockCommittedBlocksPort lockCommittedBlocksPort;
    @Mock private FindUploadedBlocksPort findUploadedBlocksPort;
    @Mock private ReferenceBlocksPort referenceBlocksPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private CommitFileUploadService commitFileUploadService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID uploadId = UUID.randomUUID();
    private final File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
            new FileName("a.bin"), new FilePath("/"), new FileOwnerId(ownerId), null, null,
            FileStatus.PENDING, new FileIsDirectory(false));

    /** Two full blocks plus a 10-byte tail: [A, B, A]. */
    private final long size = 2L * BLOCK + 10;
    private final List<String> blocklist = List.of(HASH_A, HASH_B, HASH_C);

    private CommitFileUploadCommand command(long fileSize, List<String> hashes) {
        return new CommitFileUploadCommand(file.getId(), ownerId, uploadId, fileSize, hashes);
    }

    private void fileExists() {
        given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.of(file));
        given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty());
    }

    private BusinessException commitFails(CommitFileUploadCommand command) {
        Throwable thrown = catchThrowable(() -> commitFileUploadService.commit(command));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    @Nested
    @DisplayName("서버에 없는 블록이 있을 때")
    class WhenBlocksAreMissing {

        @Test
        @DisplayName("올라온 적도 없는 블록만 needBlocks로 돌려주고 아무것도 바꾸지 않는다")
        void answersOnlyTheBlocksNeitherCommittedNorUploaded() {
            fileExists();
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection()))
                    .willReturn(Map.of(HASH_A, BLOCK));
            given(findUploadedBlocksPort.findUploadedBlocks(ownerId, List.of(HASH_B, HASH_C)))
                    .willReturn(Map.of(HASH_B, BLOCK));

            CommitResult result = commitFileUploadService.commit(command(size, blocklist));

            assertThat(result.needBlocks()).containsExactly(HASH_C);
            assertThat(result.version()).isNull();
            then(referenceBlocksPort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("모든 블록이 서버에 있을 때")
    class WhenEveryBlockIsStored {

        @Test
        @DisplayName("참조 수를 올리고 버전을 만들어 파일을 업로드 완료로 바꾼다")
        void referencesBlocksAndMakesTheVersion() {
            fileExists();
            List<String> repeated = List.of(HASH_A, HASH_B, HASH_A);
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection()))
                    .willReturn(Map.of(HASH_A, BLOCK));
            given(findUploadedBlocksPort.findUploadedBlocks(ownerId, List.of(HASH_B)))
                    .willReturn(Map.of(HASH_B, BLOCK));
            FileVersion saved = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 3L * BLOCK, repeated);
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class))).willReturn(saved);

            CommitResult result = commitFileUploadService.commit(command(3L * BLOCK, repeated));

            assertThat(result.needBlocks()).isEmpty();
            assertThat(result.version()).isEqualTo(saved);
            then(referenceBlocksPort).should().referenceBlocks(ownerId,
                    Map.of(HASH_A, BLOCK, HASH_B, BLOCK), Map.of(HASH_A, 2, HASH_B, 1));
            ArgumentCaptor<FileVersion> version = ArgumentCaptor.forClass(FileVersion.class);
            then(saveFileVersionPort).should().saveFileVersion(version.capture());
            assertThat(version.getValue().getOwnerId()).isEqualTo(ownerId);
            assertThat(version.getValue().getUploadId()).isEqualTo(uploadId);
            assertThat(version.getValue().getHashes()).isEqualTo(repeated);
            assertThat(file.getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(file.getCurrentVersionId()).isEqualTo(saved.getId());
            then(saveFilePort).should().saveFile(file);
        }

        @Test
        @DisplayName("모두 이미 커밋된 블록이면 storage-service에 묻지 않는다")
        void doesNotAskStorageWhenEveryBlockIsCommitted() {
            fileExists();
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection()))
                    .willReturn(Map.of(HASH_A, BLOCK, HASH_B, BLOCK, HASH_C, 10));
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class)))
                    .willReturn(FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), size, blocklist));

            CommitResult result = commitFileUploadService.commit(command(size, blocklist));

            assertThat(result.needBlocks()).isEmpty();
            then(findUploadedBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("빈 파일은 블록 없이 버전을 만든다")
        void commitsAnEmptyFileWithNoBlocks() {
            fileExists();
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection())).willReturn(Map.of());
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class)))
                    .willReturn(FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 0L, List.of()));

            CommitResult result = commitFileUploadService.commit(command(0L, List.of()));

            assertThat(result.version()).isNotNull();
            assertThat(file.getStatus()).isEqualTo(FileStatus.UPLOADED);
        }
    }

    @Nested
    @DisplayName("응답을 못 받은 commit을 같은 uploadId로 다시 보낼 때")
    class WhenTheUploadIdWasAlreadyCommitted {

        @Test
        @DisplayName("이미 만든 버전을 그대로 돌려주고 아무것도 다시 하지 않는다")
        void returnsTheExistingVersion() {
            FileVersion done = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), size, blocklist);
            given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.of(file));
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.of(done));

            CommitResult result = commitFileUploadService.commit(command(size, blocklist));

            assertThat(result.version()).isEqualTo(done);
            then(lockCommittedBlocksPort).shouldHaveNoInteractions();
            then(referenceBlocksPort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("다른 파일의 uploadId면 거절한다")
        void rejectsAnUploadIdOfAnotherFile() {
            FileVersion other = FileVersionTestFixture.aVersion(UUID.randomUUID(), UUID.randomUUID(), size, blocklist);
            given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.of(file));
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.of(other));

            assertThat(commitFails(command(size, blocklist)).getExceptionCase())
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
        }
    }

    @Nested
    @DisplayName("blocklist가 파일 크기와 맞지 않을 때")
    class WhenTheBlocklistDoesNotFit {

        @Test
        @DisplayName("블록 수가 크기와 다르면 거절한다")
        void rejectsAWrongBlockCount() {
            fileExists();

            assertThat(commitFails(command(size, List.of(HASH_A, HASH_B))).getExceptionCase())
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
            then(lockCommittedBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실제로 받은 블록 크기 합이 선언한 크기와 다르면 거절한다")
        void rejectsSizesThatDoNotAddUp() {
            fileExists();
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection()))
                    .willReturn(Map.of(HASH_A, BLOCK, HASH_B, BLOCK, HASH_C, 9));

            assertThat(commitFails(command(size, blocklist)).getExceptionCase())
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
            then(referenceBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("마지막이 아닌 블록이 블록 크기보다 작으면 거절한다")
        void rejectsAShortBlockBeforeTheLast() {
            fileExists();
            // Sizes still add up (BLOCK - 1 + BLOCK + 11), but the first block is short.
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection()))
                    .willReturn(Map.of(HASH_A, BLOCK - 1, HASH_B, BLOCK, HASH_C, 11));

            assertThat(commitFails(command(size, blocklist)).getExceptionCase())
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
        }

        @Test
        @DisplayName("5GB를 넘으면 거절한다")
        void rejectsAFileOverTheLimit() {
            fileExists();

            assertThat(commitFails(command(UploadBatchService.MAX_FILE_SIZE_BYTES + 1, blocklist)).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_TOO_LARGE);
        }
    }

    @Nested
    @DisplayName("올릴 수 없는 대상일 때")
    class WhenTheTargetIsNotUploadable {

        @Test
        @DisplayName("파일이 없으면 FILE_NOT_FOUND")
        void rejectsAMissingFile() {
            given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.empty());

            assertThat(commitFails(command(size, blocklist)).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자가 아니면 블록을 보기 전에 거절한다")
        void rejectsANonOwner() {
            given(findFilePort.findById(new FileId(file.getId()))).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(file, ownerId);

            assertThat(commitFails(command(size, blocklist)).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(lockCommittedBlocksPort).should(never()).lockCommittedBlocks(any(), anyCollection());
        }

        @Test
        @DisplayName("휴지통에 있거나 영구 삭제된 파일이면 FILE_NOT_FOUND")
        void rejectsARemovedFile() {
            File trashed = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("t.bin"), new FilePath("/"), new FileOwnerId(ownerId), null, null,
                    FileStatus.TRASHED, new FileIsDirectory(false));
            given(findFilePort.findById(new FileId(trashed.getId()))).willReturn(Optional.of(trashed));

            Throwable thrown = catchThrowable(() -> commitFileUploadService.commit(
                    new CommitFileUploadCommand(trashed.getId(), ownerId, uploadId, size, blocklist)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(lockCommittedBlocksPort).should(never()).lockCommittedBlocks(any(), anyCollection());
        }

        @Test
        @DisplayName("폴더에는 commit할 수 없다")
        void rejectsADirectory() {
            File directory = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("d"), new FilePath("/"), new FileOwnerId(ownerId), null, null,
                    FileStatus.UPLOADED, new FileIsDirectory(true));
            given(findFilePort.findById(new FileId(directory.getId()))).willReturn(Optional.of(directory));

            Throwable thrown = catchThrowable(() -> commitFileUploadService.commit(
                    new CommitFileUploadCommand(directory.getId(), ownerId, uploadId, 0L, List.of())));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
        }
    }
}
