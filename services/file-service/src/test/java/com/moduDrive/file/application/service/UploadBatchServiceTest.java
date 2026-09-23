package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand.ConflictResolution;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand.Item;
import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase.UploadedItem;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileNamespaceId;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceQuotaBytes;
import com.moduDrive.file.domain.model.Namespace.NamespaceRootPath;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UploadBatchServiceTest {

    @Mock
    private FindNamespacePort findNamespacePort;
    @Mock
    private FindFilePort findFilePort;
    @Mock
    private SaveFilePort saveFilePort;
    @Mock
    private SaveFileAccessPort saveFileAccessPort;
    @Mock
    private FileAccessGuard fileAccessGuard;
    @InjectMocks
    private UploadBatchService uploadBatchService;

    private final UUID userId = UUID.randomUUID();
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()),
            new NamespaceUserId(userId),
            new NamespaceRootPath("/"),
            new NamespaceQuotaBytes(21474836480L));

    private UploadBatchCommand command(String target, List<Item> items) {
        return command(target, items, Map.of());
    }

    private UploadBatchCommand command(String target, List<Item> items, Map<String, ConflictResolution> resolutions) {
        return new UploadBatchCommand(userId, new FilePath(target), items, resolutions);
    }

    private static Item file(String relativePath) {
        return new Item(relativePath, false, 100L);
    }

    private static Item folder(String relativePath) {
        return new Item(relativePath, true, null);
    }

    private File existing(String name, String path, boolean directory) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                new FileName(name), new FilePath(path), new FileOwnerId(userId),
                null, new File.FileSize(directory ? 0L : 1024L), FileStatus.UPLOADED, new FileIsDirectory(directory));
    }

    private void givenNamespace() {
        given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
    }

    private void givenExistingInTarget(String target, File... files) {
        given(findFilePort.findByNamespaceIdAndPath(any(), eq(target))).willReturn(List.of(files));
    }

    /** Hands back what was saved, with ids assigned the way the DB would. Lenient: a batch only
     * hits saveFile when it replaces something and saveNewFiles when it creates something. */
    private void givenSaveReturnsArgument() {
        lenient().when(saveFilePort.saveFile(any(File.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveFilePort.saveNewFiles(anyList())).thenAnswer(inv -> inv.<List<File>>getArgument(0).stream()
                .map(f -> File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(f.getNamespaceId()),
                        new FileName(f.getName()), new FilePath(f.getPath()), new FileOwnerId(f.getOwnerId()),
                        null, null, f.getStatus(), new FileIsDirectory(f.isDirectory())))
                .toList());
    }

    private static void assertFails(Throwable thrown, FileExceptionCase expected) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("대상 폴더에 이름이 겹치는 항목이 없을 때")
    class WhenNothingConflicts {

        @Test
        @DisplayName("중간 폴더까지 부모부터 순서대로 만들고, 폴더는 UPLOADED·파일은 PENDING이다")
        void createsTheWholeTreeParentsFirst() {
            givenNamespace();
            givenExistingInTarget("/");
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(
                    file("사진/2024/a.jpg"), folder("사진/빈폴더"), file("보고서.pdf"))));

            assertThat(result)
                    .extracting(UploadedItem::relativePath, i -> i.file().getPath(), i -> i.file().getName(),
                            i -> i.file().getStatus(), UploadedItem::replaced)
                    .containsExactly(
                            tuple("사진", "/", "사진", FileStatus.UPLOADED, false),
                            tuple("사진/2024", "/사진", "2024", FileStatus.UPLOADED, false),
                            tuple("사진/2024/a.jpg", "/사진/2024", "a.jpg", FileStatus.PENDING, false),
                            tuple("사진/빈폴더", "/사진", "빈폴더", FileStatus.UPLOADED, false),
                            tuple("보고서.pdf", "/", "보고서.pdf", FileStatus.PENDING, false));
        }

        @Test
        @DisplayName("최근 문서함에는 폴더를 빼고 파일만 한 번에 기록한다")
        void recordsAccessForFilesOnlyInOnePass() {
            givenNamespace();
            givenExistingInTarget("/");
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(
                    file("사진/a.jpg"), folder("빈폴더"), file("b.txt"))));

            List<UUID> fileIds = result.stream()
                    .filter(item -> !item.file().isDirectory())
                    .map(item -> item.file().getId())
                    .toList();
            assertThat(fileIds).hasSize(2);
            then(saveFileAccessPort).should().recordAccesses(eq(userId), eq(fileIds), any());
        }

        @Test
        @DisplayName("폴더만 올리면 최근 문서함에 기록하지 않는다")
        void recordsNothingForFoldersOnly() {
            givenNamespace();
            givenExistingInTarget("/");
            givenSaveReturnsArgument();

            uploadBatchService.uploadBatch(command("/", List.of(folder("빈폴더"))));

            then(saveFileAccessPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("하위 항목보다 뒤에 명시된 폴더도 한 번만 만든다")
        void createsAFolderListedAfterItsChildOnlyOnce() {
            givenNamespace();
            givenExistingInTarget("/");
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(
                    file("사진/a.jpg"), folder("사진"))));

            assertThat(result).extracting(UploadedItem::relativePath).containsExactly("사진", "사진/a.jpg");
        }

        @Test
        @DisplayName("대상이 하위 폴더면 그 아래 경로로 만든다")
        void createsUnderANestedTargetFolder() {
            givenNamespace();
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("업무")))
                    .willReturn(Optional.of(existing("업무", "/", true)));
            givenExistingInTarget("/업무");
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/업무", List.of(file("사진/a.jpg"))));

            assertThat(result)
                    .extracting(i -> i.file().getPath())
                    .containsExactly("/업무", "/업무/사진");
        }

        @Test
        @DisplayName("0바이트 파일도 만든다")
        void acceptsAnEmptyFile() {
            givenNamespace();
            givenExistingInTarget("/");
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(
                    new Item(".gitkeep", false, 0L))));

            assertThat(result).extracting(UploadedItem::relativePath).containsExactly(".gitkeep");
        }
    }

    @Nested
    @DisplayName("최상위 폴더나 종류가 다른 항목과 이름이 겹칠 때")
    class WhenTopLevelNameClashesWithoutAsking {

        @Test
        @DisplayName("폴더끼리 겹치면 묻지 않고 번호를 붙이고, 하위 항목도 바뀐 폴더 아래로 간다")
        void numbersAFolderAndMovesItsChildrenUnderTheNewName() {
            givenNamespace();
            givenExistingInTarget("/", existing("사진", "/", true));
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(file("사진/a.jpg"))));

            assertThat(result)
                    .extracting(UploadedItem::relativePath, i -> i.file().getPath(), i -> i.file().getName())
                    .containsExactly(
                            tuple("사진", "/", "사진 (1)"),
                            tuple("사진/a.jpg", "/사진 (1)", "a.jpg"));
        }

        @Test
        @DisplayName("번호 붙인 이름도 이미 있으면 다음 번호를 쓴다")
        void skipsNumbersAlreadyTaken() {
            givenNamespace();
            givenExistingInTarget("/", existing("사진", "/", true), existing("사진 (1)", "/", true));
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(folder("사진"))));

            assertThat(result).extracting(i -> i.file().getName()).containsExactly("사진 (2)");
        }

        @Test
        @DisplayName("파일과 폴더처럼 종류가 다르면 묻지 않고 번호를 붙인다")
        void numbersAFileClashingWithAFolder() {
            givenNamespace();
            givenExistingInTarget("/", existing("자료", "/", true));
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(file("자료"))));

            assertThat(result).extracting(i -> i.file().getName()).containsExactly("자료 (1)");
            then(fileAccessGuard).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("번호는 같은 배치에서 사용자가 고른 다른 이름을 피해서 붙인다")
        void numberingNeverTakesANameAnotherEntryOfTheBatchAskedFor() {
            givenNamespace();
            givenExistingInTarget("/", existing("사진", "/", true));
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(
                    folder("사진"), folder("사진 (1)"))));

            assertThat(result).extracting(i -> i.file().getName()).containsExactly("사진 (2)", "사진 (1)");
        }
    }

    @Nested
    @DisplayName("최상위 파일이 같은 이름의 파일과 겹칠 때")
    class WhenTopLevelFileClashesWithAFile {

        private final File report = existing("보고서.pdf", "/", false);
        private final File notes = existing("a.txt", "/", false);

        @Test
        @DisplayName("선택이 없으면 겹친 이름을 전부 모아 409로 돌려주고 아무것도 만들지 않는다")
        void rejectsWithEveryConflictingName() {
            givenNamespace();
            givenExistingInTarget("/", report, notes);

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/", List.of(
                    file("보고서.pdf"), file("a.txt"), file("new.txt")))));

            assertFails(thrown, FileExceptionCase.FILE_BATCH_CONFLICT);
            assertThat(((BusinessException) thrown).getData())
                    .isEqualTo(Map.of("conflicts", List.of("보고서.pdf", "a.txt")));
            then(saveFilePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("대체를 고르면 같은 파일을 PENDING으로 되돌려 새 버전을 받는다")
        void replaceReusesTheExistingFile() {
            givenNamespace();
            givenExistingInTarget("/", report);
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(file("보고서.pdf")),
                    Map.of("보고서.pdf", ConflictResolution.REPLACE)));

            assertThat(result).singleElement().satisfies(item -> {
                assertThat(item.replaced()).isTrue();
                assertThat(item.file().getId()).isEqualTo(report.getId());
                assertThat(item.file().getStatus()).isEqualTo(FileStatus.PENDING);
            });
            then(fileAccessGuard).should().requireOwner(report, userId);
        }

        @Test
        @DisplayName("대체하려는 파일의 소유자가 아니면 거부하고 아무것도 만들지 않는다")
        void replaceIsDeniedForANonOwner() {
            givenNamespace();
            givenExistingInTarget("/", report);
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(report, userId);

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/",
                    List.of(file("보고서.pdf")), Map.of("보고서.pdf", ConflictResolution.REPLACE))));

            assertFails(thrown, FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFilePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("둘 다 유지를 고르면 확장자 앞에 번호를 붙인 새 파일로 만든다")
        void keepBothCreatesANumberedFile() {
            givenNamespace();
            givenExistingInTarget("/", report);
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/", List.of(file("보고서.pdf")),
                    Map.of("보고서.pdf", ConflictResolution.KEEP_BOTH)));

            assertThat(result).singleElement().satisfies(item -> {
                assertThat(item.replaced()).isFalse();
                assertThat(item.file().getName()).isEqualTo("보고서 (1).pdf");
                assertThat(item.file().getId()).isNotEqualTo(report.getId());
            });
        }

        @Test
        @DisplayName("건너뛰기를 고르면 그 파일만 빼고 나머지는 만든다")
        void skipLeavesOnlyThatFileOut() {
            givenNamespace();
            givenExistingInTarget("/", report);
            givenSaveReturnsArgument();

            List<UploadedItem> result = uploadBatchService.uploadBatch(command("/",
                    List.of(file("보고서.pdf"), file("a.txt")), Map.of("보고서.pdf", ConflictResolution.SKIP)));

            assertThat(result).extracting(UploadedItem::relativePath).containsExactly("a.txt");
        }
    }

    @Nested
    @DisplayName("대상 폴더가 없을 때")
    class WhenTargetFolderIsMissing {

        @Test
        @DisplayName("대상 경로 표기가 정규형이 아니면 실제 폴더가 있어도 없는 것으로 본다")
        void rejectsANonCanonicalTargetPath() {
            for (String target : new String[]{"//업무", "/업무/", "업무", "/업무//사진"}) {
                givenNamespace();

                Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command(target, List.of(file("a.txt")))));

                assertFails(thrown, FileExceptionCase.DIRECTORY_NOT_FOUND);
            }
            then(findFilePort).shouldHaveNoInteractions();
        }

        @Test
        void rejectsAMissingFolder() {
            givenNamespace();
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("업무")))
                    .willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/업무", List.of(file("a.txt")))));

            assertFails(thrown, FileExceptionCase.DIRECTORY_NOT_FOUND);
            then(saveFilePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("그 경로가 폴더가 아니라 파일이면 없는 것으로 본다")
        void rejectsAFileAsTarget() {
            givenNamespace();
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), eq("/"), eq("업무")))
                    .willReturn(Optional.of(existing("업무", "/", false)));

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/업무", List.of(file("a.txt")))));

            assertFails(thrown, FileExceptionCase.DIRECTORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("항목이 잘못됐을 때")
    class WhenAnItemIsInvalid {

        private void assertRejected(List<Item> items) {
            givenNamespace();

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/", items)));

            assertFails(thrown, FileExceptionCase.INVALID_BATCH_ITEM);
            then(saveFilePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("경로 조각이 이름 규칙을 어기면 거부한다")
        void rejectsAnInvalidPathSegment() {
            for (String path : new String[]{"../x.txt", "a/../b.txt", "a//b.txt", "/a.txt", "a/", "a\\b.txt"}) {
                assertRejected(List.of(file(path)));
            }
        }

        @Test
        void rejectsADuplicatePath() {
            assertRejected(List.of(file("a.txt"), file("a.txt")));
        }

        @Test
        @DisplayName("파일로 적은 경로를 다른 항목의 폴더로 쓰면 거부한다")
        void rejectsAFileUsedAsAFolder() {
            assertRejected(List.of(file("a.txt"), file("a.txt/b.txt")));
        }

        @Test
        @DisplayName("다른 항목의 폴더로 쓰인 경로를 파일로 적으면 거부한다")
        void rejectsAFolderListedAsAFile() {
            assertRejected(List.of(file("a/b.txt"), file("a")));
        }

        @Test
        @DisplayName("만들어질 경로가 컬럼 길이(255자)를 넘으면 거부한다")
        void rejectsAParentPathLongerThanTheColumn() {
            givenNamespace();
            givenExistingInTarget("/");
            givenSaveReturnsArgument();
            String deep = "d".repeat(200) + "/" + "e".repeat(60) + "/a.txt"; // parent "/ddd…/eee…" = 262자

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/", List.of(file(deep)))));

            assertFails(thrown, FileExceptionCase.INVALID_BATCH_ITEM);
        }

        @Test
        @DisplayName("번호를 붙여 이름이 255자를 넘게 되면 거부한다")
        void rejectsANameThatTheNumberPushesOverTheColumn() {
            String longName = "n".repeat(253);
            givenNamespace();
            givenExistingInTarget("/", existing(longName, "/", true));

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/", List.of(folder(longName)))));

            assertFails(thrown, FileExceptionCase.INVALID_BATCH_ITEM);
            then(saveFilePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("파일 크기가 없거나 음수거나 5GB를 넘으면 거부한다")
        void rejectsAMissingOrOutOfRangeSize() {
            for (Long size : new Long[]{null, -1L, UploadBatchService.MAX_FILE_SIZE_BYTES + 1}) {
                assertRejected(List.of(new Item("a.txt", false, size)));
            }
        }
    }

    @Nested
    @DisplayName("네임스페이스가 없을 때")
    class WhenNamespaceNotFound {

        @Test
        void throwsBusinessException() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> uploadBatchService.uploadBatch(command("/", List.of(file("a.txt")))));

            assertFails(thrown, FileExceptionCase.NAMESPACE_NOT_FOUND);
        }
    }
}
