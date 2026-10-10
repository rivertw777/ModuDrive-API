package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.exception.ExceptionCase;
import com.moduDrive.common.infrastructure.resilience4j.CircuitBreakerExceptionCase;
import com.moduDrive.file.application.port.in.command.CommitDirectoryCommand;
import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase.DirectoryResult;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase.CommitResult;
import com.moduDrive.file.application.port.out.FindCommittedBlocksPort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReferenceBlocksPort;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileVersionPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceQuotaBytes;
import com.moduDrive.file.domain.model.Namespace.NamespaceRootPath;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import com.moduDrive.file.fixture.FileVersionTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.moduDrive.file.fixture.FileVersionTestFixture.HASH_A;
import static com.moduDrive.file.fixture.FileVersionTestFixture.HASH_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CommitFileUploadServiceTest {

    private static final int BLOCK = FileVersion.BLOCK_SIZE;
    private static final String HASH_C = "c".repeat(64);

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @Mock private SaveFileVersionPort saveFileVersionPort;
    @Mock private LockCommittedBlocksPort lockCommittedBlocksPort;
    @Mock private FindUploadedBlocksPort findUploadedBlocksPort;
    @Mock private ReferenceBlocksPort referenceBlocksPort;
    @Mock private SaveFileAccessPort saveFileAccessPort;
    @Mock private FindCommittedBlocksPort findCommittedBlocksPort;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private CommitFileUploadService commitFileUploadService;

    /** The fixture's versions are owned by this user. */
    private final UUID ownerId = FileVersionTestFixture.OWNER_ID;
    private final UUID uploadId = UUID.randomUUID();
    private final Namespace namespace = Namespace.withId(new NamespaceId(UUID.randomUUID()),
            new NamespaceUserId(ownerId), new NamespaceRootPath("/"), new NamespaceQuotaBytes(21474836480L));
    /** Already at /사진/a.bin, so a commit there is a new version of it. */
    private final File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
            new FileName("a.bin"), new FilePath("/사진"), new FileOwnerId(ownerId), null, null,
            FileStatus.UPLOADED, new FileIsDirectory(false));

    /** Two full blocks plus a 10-byte tail: [A, B, A]. */
    private final long size = 2L * BLOCK + 10;
    private final List<String> blocklist = List.of(HASH_A, HASH_B, HASH_C);

    private CommitFileUploadCommand command(long fileSize, List<String> hashes) {
        return new CommitFileUploadCommand(ownerId, "/사진", "a.bin", uploadId, fileSize, hashes);
    }

    /** The owner's committed blocks, as both the unlocked look and the lock see them. Lenient: a
     * commit that answers needBlocks never takes the lock. Runs the transaction inline. */
    private void givenCommitted(Map<String, Integer> sizeByHash) {
        // Lenient: an empty file has no blocks to look up.
        lenient().when(findCommittedBlocksPort.findCommittedHashes(eq(ownerId), anyCollection())).thenReturn(sizeByHash.keySet());
        lenient().when(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection())).thenReturn(sizeByHash);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private void givenNamespace() {
        given(findNamespacePort.findByUserId(new NamespaceUserId(ownerId))).willReturn(Optional.of(namespace));
    }

    /** Lenient: a commit that answers needBlocks never looks for the file. */
    private void fileExists() {
        givenNamespace();
        given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty());
        lenient().when(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/사진"), eq("a.bin")))
                .thenReturn(Optional.of(file));
    }

    private CommitResult commitOne(CommitFileUploadCommand command) {
        return commitFileUploadService.commit(List.of(command)).getFirst();
    }

    /** What the file failed with — only it, not the request. */
    private ExceptionCase commitError(CommitFileUploadCommand command) {
        CommitResult result = commitOne(command);
        assertThat(result.version()).isNull();
        return result.error();
    }

    @Nested
    @DisplayName("서버에 없는 블록이 있을 때")
    class WhenBlocksAreMissing {

        @Test
        @DisplayName("올라온 적도 없는 블록만 needBlocks로 돌려주고 아무것도 바꾸지 않는다")
        void answersOnlyTheBlocksNeitherCommittedNorUploaded() {
            fileExists();
            givenCommitted(Map.of(HASH_A, BLOCK));
            given(findUploadedBlocksPort.findUploadedBlocks(ownerId, List.of(HASH_B, HASH_C)))
                    .willReturn(Map.of(HASH_B, BLOCK));

            CommitResult result = commitOne(command(size, blocklist));

            assertThat(result.needBlocks()).containsExactly(HASH_C);
            assertThat(result.version()).isNull();
            then(referenceBlocksPort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
            then(saveFilePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("빠진 블록을 답할 때는 트랜잭션을 열지 않는다 — storage-service가 느려도 DB를 붙잡지 않는다")
        void asksOutsideAnyTransaction() {
            fileExists();
            givenCommitted(Map.of());
            given(findUploadedBlocksPort.findUploadedBlocks(eq(ownerId), anyCollection())).willReturn(Map.of());

            commitOne(command(size, blocklist));

            then(transactionTemplate).shouldHaveNoInteractions();
            then(lockCommittedBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("잠그기 전에 정리 작업이 지운 블록은 needBlocks로 돌려준다")
        void answersABlockPurgedBeforeTheLock() {
            fileExists();
            given(findCommittedBlocksPort.findCommittedHashes(eq(ownerId), anyCollection())).willReturn(Set.of(HASH_A, HASH_B, HASH_C));
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection()))
                    .willReturn(Map.of(HASH_A, BLOCK, HASH_B, BLOCK));
            given(transactionTemplate.execute(any())).willAnswer(inv ->
                    inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));

            CommitResult result = commitOne(command(size, blocklist));

            assertThat(result.needBlocks()).containsExactly(HASH_C);
            then(referenceBlocksPort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
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
            givenCommitted(Map.of(HASH_A, BLOCK));
            given(findUploadedBlocksPort.findUploadedBlocks(ownerId, List.of(HASH_B)))
                    .willReturn(Map.of(HASH_B, BLOCK));
            FileVersion saved = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 3L * BLOCK, repeated);
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class))).willReturn(saved);

            CommitResult result = commitOne(command(3L * BLOCK, repeated));

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
            then(saveFileAccessPort).should().recordAccesses(eq(ownerId), eq(List.of(file.getId())), any());
        }

        @Test
        @DisplayName("모두 이미 커밋된 블록이면 storage-service에 묻지 않는다")
        void doesNotAskStorageWhenEveryBlockIsCommitted() {
            fileExists();
            givenCommitted(Map.of(HASH_A, BLOCK, HASH_B, BLOCK, HASH_C, 10));
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class)))
                    .willReturn(FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), size, blocklist));

            CommitResult result = commitOne(command(size, blocklist));

            assertThat(result.needBlocks()).isEmpty();
            then(findUploadedBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("현재 버전과 내용이 같으면 새 버전 없이 현재 버전을 돌려준다")
        void makesNoNewVersionForUnchangedContent() {
            FileVersion current = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), size, blocklist);
            file.markUploaded(current.getId(), size);
            fileExists();
            givenCommitted(Map.of(HASH_A, BLOCK, HASH_B, BLOCK, HASH_C, 10));
            given(findFileVersionsPort.findAllByIds(List.of(current.getId()))).willReturn(List.of(current));

            CommitResult result = commitOne(command(size, blocklist));

            assertThat(result.version()).isEqualTo(current);
            then(referenceBlocksPort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
            then(saveFileAccessPort).should().recordAccesses(eq(ownerId), eq(List.of(file.getId())), any());
        }

        @Test
        @DisplayName("빈 파일은 블록 없이 버전을 만든다")
        void commitsAnEmptyFileWithNoBlocks() {
            fileExists();
            givenCommitted(Map.of());
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class)))
                    .willReturn(FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 0L, List.of()));

            CommitResult result = commitOne(command(0L, List.of()));

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
            givenNamespace();
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.of(done));

            CommitResult result = commitOne(command(size, blocklist));

            assertThat(result.version()).isEqualTo(done);
            then(lockCommittedBlocksPort).shouldHaveNoInteractions();
            then(referenceBlocksPort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("같은 uploadId인데 내용이 다르면 거절한다")
        void rejectsTheSameUploadIdWithOtherContent() {
            FileVersion done = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), size, blocklist);
            givenNamespace();
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.of(done));

            assertThat(commitError(command(size, List.of(HASH_A, HASH_A, HASH_C))))
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
        }

        @Test
        @DisplayName("동시에 보낸 같은 commit에 져서 자리 충돌이 나면, 이긴 쪽 버전을 돌려준다")
        void answersWithTheWinnersVersionAfterLosingTheRace() {
            fileExists();
            givenCommitted(Map.of(HASH_A, BLOCK, HASH_B, BLOCK, HASH_C, 10));
            FileVersion winner = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), size, blocklist);
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty(), Optional.of(winner));
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class)))
                    .willThrow(new DataIntegrityViolationException("uk_file_version_upload_id"));

            assertThat(commitOne(command(size, blocklist)).version()).isEqualTo(winner);
        }

        @Test
        @DisplayName("한 묶음에서 같은 uploadId를 다른 내용에 쓰면, 진 쪽은 이긴 쪽 버전이 아니라 거절로 답한다")
        void rejectsALoserWhoseContentDiffersFromTheWinner() {
            fileExists();
            givenCommitted(Map.of(HASH_A, BLOCK, HASH_B, BLOCK, HASH_C, 10));
            FileVersion winner = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 10L, List.of(HASH_A));
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty(), Optional.of(winner));
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class)))
                    .willThrow(new DataIntegrityViolationException("uk_file_version_upload_id"));

            assertThat(commitError(command(size, blocklist))).isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
        }

        @Test
        @DisplayName("다른 사용자의 uploadId면 거절한다")
        void rejectsAnUploadIdOfAnotherUser() {
            FileVersion other = FileVersionTestFixture.aVersion(UUID.randomUUID(), UUID.randomUUID(), size, blocklist);
            UUID stranger = UUID.randomUUID();
            given(findNamespacePort.findByUserId(new NamespaceUserId(stranger))).willReturn(Optional.of(namespace));
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.of(other));

            assertThat(commitError(new CommitFileUploadCommand(stranger, "/사진", "a.bin", uploadId, size, blocklist)))
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

            assertThat(commitError(command(size, List.of(HASH_A, HASH_B))))
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
            then(lockCommittedBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실제로 받은 블록 크기 합이 선언한 크기와 다르면 거절한다")
        void rejectsSizesThatDoNotAddUp() {
            fileExists();
            givenCommitted(Map.of(HASH_A, BLOCK, HASH_B, BLOCK, HASH_C, 9));

            assertThat(commitError(command(size, blocklist)))
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
            then(referenceBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("마지막이 아닌 블록이 블록 크기보다 작으면 거절한다")
        void rejectsAShortBlockBeforeTheLast() {
            fileExists();
            // Sizes still add up (BLOCK - 1 + BLOCK + 11), but the first block is short.
            givenCommitted(Map.of(HASH_A, BLOCK - 1, HASH_B, BLOCK, HASH_C, 11));

            assertThat(commitError(command(size, blocklist)))
                    .isEqualTo(FileExceptionCase.INVALID_BLOCKLIST);
        }

        @Test
        @DisplayName("5GB를 넘으면 거절한다")
        void rejectsAFileOverTheLimit() {
            fileExists();

            assertThat(commitError(command(UploadBatchService.MAX_FILE_SIZE_BYTES + 1, blocklist)))
                    .isEqualTo(FileExceptionCase.FILE_TOO_LARGE);
        }
    }

    @Nested
    @DisplayName("그 자리에 파일이 없을 때")
    class WhenNothingIsThereYet {

        @Test
        @DisplayName("없는 상위 폴더를 위에서부터 만들고 파일 행을 새로 만든다")
        void createsMissingFoldersAndTheFile() {
            givenNamespace();
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty());
            givenCommitted(Map.of());
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/사진/2024"), eq("a.bin")))
                    .willReturn(Optional.empty());
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("사진")))
                    .willReturn(Optional.of(File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                            new FileName("사진"), new FilePath("/"), new FileOwnerId(ownerId), null, null,
                            FileStatus.UPLOADED, new FileIsDirectory(true))));
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/사진"), eq("2024")))
                    .willReturn(Optional.empty());
            UUID newId = UUID.randomUUID();
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> {
                File f = inv.getArgument(0);
                return f.getId() != null || f.isDirectory() ? f : File.withId(new FileId(newId),
                        new FileNamespaceId(f.getNamespaceId()), new FileName(f.getName()), new FilePath(f.getPath()),
                        new FileOwnerId(f.getOwnerId()), null, null, f.getStatus(), new FileIsDirectory(false));
            });
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class)))
                    .willReturn(FileVersionTestFixture.aVersion(UUID.randomUUID(), newId, 0L, List.of()));

            commitOne(new CommitFileUploadCommand(ownerId, "/사진/2024", "a.bin", uploadId, 0L, List.of()));

            ArgumentCaptor<File> saved = ArgumentCaptor.forClass(File.class);
            then(saveFilePort).should(times(3)).saveFile(saved.capture());
            assertThat(saved.getAllValues())
                    .extracting(File::getPath, File::getName, File::isDirectory)
                    .containsExactly(
                            tuple("/사진", "2024", true),
                            tuple("/사진/2024", "a.bin", false),
                            tuple("/사진/2024", "a.bin", false));
            assertThat(saved.getAllValues().get(2).getStatus()).isEqualTo(FileStatus.UPLOADED);
        }

        @Test
        @DisplayName("빠진 블록이 있으면 폴더도 파일도 만들지 않는다")
        void createsNothingWhileBlocksAreMissing() {
            givenNamespace();
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty());
            givenCommitted(Map.of());
            given(findUploadedBlocksPort.findUploadedBlocks(eq(ownerId), any())).willReturn(Map.of());

            CommitResult result = commitOne(
                    new CommitFileUploadCommand(ownerId, "/새폴더", "a.bin", uploadId, 10L, List.of(HASH_A)));

            assertThat(result.needBlocks()).containsExactly(HASH_A);
            then(findFilePort).shouldHaveNoInteractions();
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("올릴 수 없는 자리일 때")
    class WhenTheTargetIsNotUploadable {

        @Test
        @DisplayName("네임스페이스가 없으면 NAMESPACE_NOT_FOUND")
        void rejectsAMissingNamespace() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> commitFileUploadService.commit(List.of(command(size, blocklist))));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.NAMESPACE_NOT_FOUND);
        }

        @Test
        @DisplayName("경로가 정규형이 아니거나 이름이 규칙에 어긋나면 블록을 보기 전에 거절한다")
        void rejectsABadPathOrName() {
            givenNamespace();
            String[][] targets = {{"//사진", "a.bin"}, {"/사진/", "a.bin"}, {"사진", "a.bin"}, {"/사진", ".."}, {"/사진", "a/b"}};
            for (String[] target : targets) {
                assertThat(commitError(new CommitFileUploadCommand(ownerId, target[0], target[1], uploadId, size, blocklist)))
                        .isEqualTo(FileExceptionCase.INVALID_BATCH_ITEM);
            }
            then(lockCommittedBlocksPort).should(never()).lockCommittedBlocks(any(), anyCollection());
        }

        @Test
        @DisplayName("그 자리에 폴더가 있으면 FILE_ALREADY_EXISTS")
        void rejectsAFolderAtTheTarget() {
            givenNamespace();
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty());
            givenCommitted(Map.of());
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/사진"), eq("a.bin")))
                    .willReturn(Optional.of(File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                            new FileName("a.bin"), new FilePath("/사진"), new FileOwnerId(ownerId), null, null,
                            FileStatus.UPLOADED, new FileIsDirectory(true))));

            assertThat(commitError(command(0L, List.of())))
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_EXISTS);
            then(referenceBlocksPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("상위 경로 중간에 파일이 있으면 FILE_ALREADY_EXISTS")
        void rejectsAFileInTheParentPath() {
            givenNamespace();
            given(findFileVersionsPort.findByUploadId(uploadId)).willReturn(Optional.empty());
            givenCommitted(Map.of());
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/사진"), eq("a.bin")))
                    .willReturn(Optional.empty());
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("사진")))
                    .willReturn(Optional.of(File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                            new FileName("사진"), new FilePath("/"), new FileOwnerId(ownerId), null, null,
                            FileStatus.UPLOADED, new FileIsDirectory(false))));

            assertThat(commitError(command(0L, List.of())))
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_EXISTS);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("여러 파일을 한 번에 commit할 때")
    class WhenAGroupIsCommitted {

        @Test
        @DisplayName("블록 조회는 묶음 전체에 한 번씩, 결과는 파일마다 요청 순서대로 따로 낸다")
        void looksUpOnceAndAnswersEachFileOnItsOwn() {
            fileExists();
            given(findCommittedBlocksPort.findCommittedHashes(eq(ownerId), anyCollection())).willReturn(Set.of(HASH_A));
            given(findUploadedBlocksPort.findUploadedBlocks(ownerId, List.of(HASH_B))).willReturn(Map.of());
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection())).willReturn(Map.of(HASH_A, 10));
            given(transactionTemplate.execute(any())).willAnswer(inv ->
                    inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
            FileVersion saved = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 10L, List.of(HASH_A));
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class))).willReturn(saved);

            List<CommitResult> results = commitFileUploadService.commit(List.of(
                    new CommitFileUploadCommand(ownerId, "/사진", "a.bin", uploadId, 10L, List.of(HASH_A)),
                    new CommitFileUploadCommand(ownerId, "/사진", "..", UUID.randomUUID(), 10L, List.of(HASH_A)),
                    new CommitFileUploadCommand(ownerId, "/사진", "b.bin", UUID.randomUUID(), 10L, List.of(HASH_B))));

            assertThat(results.get(0).version()).isEqualTo(saved);
            assertThat(results.get(1).error()).isEqualTo(FileExceptionCase.INVALID_BATCH_ITEM);
            assertThat(results.get(2).needBlocks()).containsExactly(HASH_B);
            then(findCommittedBlocksPort).should(times(1)).findCommittedHashes(eq(ownerId), anyCollection());
            then(findUploadedBlocksPort).should(times(1)).findUploadedBlocks(eq(ownerId), anyCollection());
        }

        @Test
        @DisplayName("storage-service가 죽어 있으면 커밋된 블록만 쓰는 파일은 버전을 만들고, 나머지 파일만 실패한다")
        void commitsWhatItCanWhileStorageIsDown() {
            fileExists();
            given(findCommittedBlocksPort.findCommittedHashes(eq(ownerId), anyCollection())).willReturn(Set.of(HASH_A));
            given(findUploadedBlocksPort.findUploadedBlocks(eq(ownerId), anyCollection()))
                    .willThrow(new BusinessException(CircuitBreakerExceptionCase.SERVICE_IS_OPEN));
            given(lockCommittedBlocksPort.lockCommittedBlocks(eq(ownerId), anyCollection())).willReturn(Map.of(HASH_A, 10));
            given(transactionTemplate.execute(any())).willAnswer(inv ->
                    inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
            FileVersion saved = FileVersionTestFixture.aVersion(UUID.randomUUID(), file.getId(), 10L, List.of(HASH_A));
            given(saveFileVersionPort.saveFileVersion(any(FileVersion.class))).willReturn(saved);

            List<CommitResult> results = commitFileUploadService.commit(List.of(
                    new CommitFileUploadCommand(ownerId, "/사진", "a.bin", uploadId, 10L, List.of(HASH_A)),
                    new CommitFileUploadCommand(ownerId, "/사진", "b.bin", UUID.randomUUID(), 10L, List.of(HASH_B))));

            assertThat(results.get(0).version()).isEqualTo(saved);
            assertThat(results.get(1).error()).isEqualTo(CircuitBreakerExceptionCase.SERVICE_IS_OPEN);
            assertThat(results.get(1).needBlocks()).isNull();
        }

        @Test
        @DisplayName("해시 합이 1,280개를 넘으면 요청 전체를 거절한다")
        void rejectsAGroupOverTheHashCap() {
            givenNamespace();
            List<String> full = Collections.nCopies(1280, HASH_A);
            CommitFileUploadCommand big = new CommitFileUploadCommand(ownerId, "/사진", "a.bin", uploadId, 1280L * BLOCK, full);
            CommitFileUploadCommand small = new CommitFileUploadCommand(ownerId, "/사진", "b.bin", UUID.randomUUID(), 10L, List.of(HASH_A));

            Throwable thrown = catchThrowable(() -> commitFileUploadService.commit(List.of(big, small)));

            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.COMMIT_TOO_LARGE);
            then(findCommittedBlocksPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("빈 폴더를 commit할 때")
    class WhenDirectoriesAreCommitted {

        private File folder(String path, String name) {
            return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName(name), new FilePath(path), new FileOwnerId(ownerId), null, null,
                    FileStatus.UPLOADED, new FileIsDirectory(true));
        }

        @BeforeEach
        void setUp() {
            givenNamespace();
            lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                    inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        }

        @Test
        @DisplayName("없는 폴더를 상위부터 만들고 마지막 폴더의 ID를 돌려준다")
        void createsMissingFoldersTopDown() {
            File photos = folder("/", "사진");
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("사진"))).willReturn(Optional.of(photos));
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/사진"), eq("v1.2"))).willReturn(Optional.empty());
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/사진/v1.2"), eq("빈폴더"))).willReturn(Optional.empty());
            File created = folder("/사진/v1.2", "빈폴더");
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv ->
                    "빈폴더".equals(inv.<File>getArgument(0).getName()) ? created : inv.getArgument(0));

            List<DirectoryResult> results = commitFileUploadService.commitDirectories(
                    List.of(new CommitDirectoryCommand(ownerId, "/사진/v1.2", "빈폴더")));

            assertThat(results).containsExactly(DirectoryResult.created(created.getId()));
            ArgumentCaptor<File> saved = ArgumentCaptor.forClass(File.class);
            then(saveFilePort).should(times(2)).saveFile(saved.capture());
            assertThat(saved.getAllValues())
                    .extracting(File::getPath, File::getName, File::isDirectory)
                    .containsExactly(tuple("/사진", "v1.2", true), tuple("/사진/v1.2", "빈폴더", true));
        }

        @Test
        @DisplayName("다른 commit이 같은 폴더를 먼저 만들었으면 다시 찾아 그 폴더를 돌려준다")
        void findsAFolderAnotherCommitCreatedFirst() {
            File raced = folder("/", "사진");
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("사진")))
                    .willReturn(Optional.empty(), Optional.of(raced));
            given(saveFilePort.saveFile(any(File.class)))
                    .willThrow(new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS));

            List<DirectoryResult> results = commitFileUploadService.commitDirectories(
                    List.of(new CommitDirectoryCommand(ownerId, "/", "사진")));

            assertThat(results).containsExactly(DirectoryResult.created(raced.getId()));
        }

        @Test
        @DisplayName("이미 있는 폴더는 그대로 돌려주고, 파일이 막고 있거나 이름이 틀린 폴더만 실패한다")
        void answersEachFolderOnItsOwn() {
            File existing = folder("/", "사진");
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("사진"))).willReturn(Optional.of(existing));
            given(findFilePort.lockActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("a.bin"))).willReturn(Optional.of(
                    File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()), new FileName("a.bin"),
                            new FilePath("/"), new FileOwnerId(ownerId), null, null, FileStatus.UPLOADED,
                            new FileIsDirectory(false))));

            List<DirectoryResult> results = commitFileUploadService.commitDirectories(List.of(
                    new CommitDirectoryCommand(ownerId, "/", "사진"),
                    new CommitDirectoryCommand(ownerId, "/", "a.bin"),
                    new CommitDirectoryCommand(ownerId, "/", "..")));

            assertThat(results).containsExactly(
                    DirectoryResult.created(existing.getId()),
                    DirectoryResult.failed(FileExceptionCase.FILE_ALREADY_EXISTS),
                    DirectoryResult.failed(FileExceptionCase.INVALID_BATCH_ITEM));
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
