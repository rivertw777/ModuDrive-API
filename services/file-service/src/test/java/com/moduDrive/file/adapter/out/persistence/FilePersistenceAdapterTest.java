package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileAccess;
import com.moduDrive.file.domain.model.FileAccess.FileAccessFileId;
import com.moduDrive.file.domain.model.FileAccess.FileAccessUserId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest
@Import({FilePersistenceAdapter.class, FileMapper.class, RolePermissionPersistenceAdapter.class})
class FilePersistenceAdapterTest {

    @Autowired
    private FilePersistenceAdapter filePersistenceAdapter;
    @Autowired
    private SpringDataFileRepository springDataFileRepository;
    @Autowired
    private SpringDataFileAccessRepository springDataFileAccessRepository;
    @Autowired
    private SpringDataFileShareRepository springDataFileShareRepository;
    @Autowired
    private SpringDataFileRoleRepository springDataFileRoleRepository;

    private final UUID namespaceIdValue = UUID.randomUUID();
    private final NamespaceId namespaceId = new NamespaceId(namespaceIdValue);
    private UUID viewerRoleId;
    private UUID editorRoleId;

    @BeforeEach
    void seedRoles() {
        viewerRoleId = springDataFileRoleRepository.saveAndFlush(new FileRoleJpaEntity(Role.VIEWER)).getId();
        editorRoleId = springDataFileRoleRepository.saveAndFlush(new FileRoleJpaEntity(Role.EDITOR)).getId();
    }

    private void save(String path, String name) {
        save(path, name, FileStatus.UPLOADED);
    }

    private void save(String path, String name, FileStatus status) {
        springDataFileRepository.save(new FileJpaEntity(
                namespaceIdValue, name, path, UUID.randomUUID(), status, false));
    }

    @Nested
    @DisplayName("경로 접두사로 하위 항목을 조회할 때")
    class WhenFindingSubtreeByPathPrefix {

        @Test
        @DisplayName("접두사로 시작하지만 형제인 경로는 매치하지 않는다 (/foo vs /foo2)")
        void doesNotMatchSiblingWithOverlappingPrefix() {
            save("/foo", "a.txt");
            save("/foo/bar", "b.txt");
            save("/foo2", "c.txt");

            var result = filePersistenceAdapter.findByNamespaceIdAndPathStartingWith(namespaceId, "/foo");

            assertThat(result).extracting(File::getPath).containsExactlyInAnyOrder("/foo", "/foo/bar");
        }

        @Test
        @DisplayName("디렉토리 이름의 LIKE 와일드카드 문자(%, _)를 리터럴로 취급한다")
        void treatsLikeWildcardsInNameAsLiterals() {
            save("/1/%", "a.txt");
            save("/1/photos", "b.txt");
            save("/1/_", "c.txt");
            save("/1/x", "d.txt");

            var result = filePersistenceAdapter.findByNamespaceIdAndPathStartingWith(namespaceId, "/1/%");

            assertThat(result).extracting(File::getPath).containsExactly("/1/%");
        }
    }

    @Nested
    @DisplayName("이름/경로로 활성 파일 충돌을 조회할 때")
    class WhenFindingByPathAndName {

        @Test
        @DisplayName("같은 네임스페이스/경로/이름의 활성 파일을 찾는다")
        void findsTheColldingRow() {
            save("/1/docs", "report.pdf");

            var result = filePersistenceAdapter.findActiveByNamespaceIdAndPathAndName(namespaceId, "/1/docs", "report.pdf");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("이름이 다르면 찾지 못한다")
        void findsNothingForADifferentName() {
            save("/1/docs", "report.pdf");

            var result = filePersistenceAdapter.findActiveByNamespaceIdAndPathAndName(namespaceId, "/1/docs", "other.pdf");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("휴지통(DELETED)에 있는 동일 이름 파일은 충돌로 잡히지 않는다")
        void doesNotFindADeletedFileAtTheSameSlot() {
            save("/1/docs", "report.pdf", FileStatus.DELETED);

            var result = filePersistenceAdapter.findActiveByNamespaceIdAndPathAndName(namespaceId, "/1/docs", "report.pdf");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("같은 자리에 두 번째 활성 파일을 저장하면 DB 유니크 제약 위반이 비즈니스 예외로 변환된다")
        void translatesUniqueConstraintViolationToBusinessException() {
            filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("report.pdf"),
                    new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));

            Throwable thrown = catchThrowable(() -> filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("report.pdf"),
                    new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false))));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("휴지통에 같은 자리의 파일이 있어도 새 활성 파일 저장은 유니크 제약에 걸리지 않는다")
        void doesNotCollideWithADeletedFileAtTheSameSlot() {
            save("/1/docs", "report.pdf", FileStatus.DELETED);

            File saved = filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("report.pdf"),
                    new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("update 경로(rename/move/restore)로 다른 활성 파일의 슬롯을 밟아도 비즈니스 예외로 변환된다 — 원본 SQL 예외가 새지 않는다")
        void translatesUniqueConstraintViolationOnUpdateToo() {
            filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("report.pdf"),
                    new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));
            File other = filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("draft.pdf"),
                    new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));
            other.rename(new FileName("report.pdf"));

            Throwable thrown = catchThrowable(() -> filePersistenceAdapter.saveFile(other));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("공유 범위와 링크 토큰을 저장할 때")
    class WhenPersistingLinkSharing {

        @Test
        @DisplayName("LINK로 전환한 파일은 토큰으로 다시 조회된다")
        void roundTripsAccessScopeAndLinkToken() {
            File saved = filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("public.pdf"),
                    new FilePath("/1"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));
            UUID token = UUID.randomUUID();
            saved.enableLinkSharing(token, Role.EDITOR);
            filePersistenceAdapter.saveFile(saved);

            var result = filePersistenceAdapter.findByLinkToken(token);

            assertThat(result).isPresent();
            assertThat(result.get().getAccessScope()).isEqualTo(ShareScope.LINK);
            assertThat(result.get().getLinkToken()).isEqualTo(token);
            assertThat(result.get().getLinkRole()).isEqualTo(Role.EDITOR);
        }

        @Test
        @DisplayName("RESTRICTED로 되돌리면 토큰으로 더 이상 조회되지 않는다")
        void clearsTokenOnRestricted() {
            File saved = filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("was-public.pdf"),
                    new FilePath("/1"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));
            UUID token = UUID.randomUUID();
            saved.enableLinkSharing(token, Role.VIEWER);
            File linked = filePersistenceAdapter.saveFile(saved);
            linked.disableLinkSharing();
            filePersistenceAdapter.saveFile(linked);

            assertThat(filePersistenceAdapter.findByLinkToken(token)).isEmpty();
        }
    }

    @Nested
    @DisplayName("파일 공유를 저장하고 조회할 때")
    class WhenPersistingFileShares {

        @Test
        @DisplayName("파일 단위와 사용자 단위로 조회되고, 역할 변경이 새 행을 만들지 않는다")
        void savesUpdatesAndQueriesShares() {
            UUID fileIdValue = UUID.randomUUID();
            UUID ownerIdValue = UUID.randomUUID();
            UUID granteeId = UUID.randomUUID();
            FileId fileId = new FileId(fileIdValue);

            FileShare created = filePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(ownerIdValue),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));

            created.changeRole(new FileShareRole(Role.EDITOR));
            filePersistenceAdapter.saveFileShare(created);

            assertThat(filePersistenceAdapter.findByFileId(fileId)).hasSize(1);
            assertThat(filePersistenceAdapter.findByFileIdAndSharedWithUserId(fileId, granteeId))
                    .get().extracting(FileShare::getRole).isEqualTo(Role.EDITOR);
            assertThat(filePersistenceAdapter.existsByFileIdAndSharedWithUserId(fileId, granteeId)).isTrue();
        }

        @Test
        @DisplayName("같은 파일-사용자 조합은 DB 유니크 제약으로 두 번 저장되지 않는다")
        void rejectsDuplicateShareRowAtTheDatabaseLevel() {
            UUID fileIdValue = UUID.randomUUID();
            UUID ownerIdValue = UUID.randomUUID();
            UUID granteeId = UUID.randomUUID();
            springDataFileShareRepository.saveAndFlush(
                    new FileShareJpaEntity(fileIdValue, ownerIdValue, granteeId, viewerRoleId));

            // The app-layer existsBy check can't see a concurrent insert; uk_file_share_file_user is
            // what actually rejects the second row, so assert the constraint exists, not the check.
            Throwable thrown = catchThrowable(() -> springDataFileShareRepository.saveAndFlush(
                    new FileShareJpaEntity(fileIdValue, ownerIdValue, granteeId, editorRoleId)));

            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("비회원 이메일 초대는 자기 토큰을 가진 RESTRICTED 공유로 저장되고 토큰으로 조회된다")
        void savesAndFindsAPendingGuestShareByItsOwnToken() {
            UUID fileIdValue = UUID.randomUUID();
            FileId fileId = new FileId(fileIdValue);

            FileShare created = filePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            assertThat(created.getSharedWithUserId()).isNull();
            assertThat(created.getToken()).isNotNull();
            assertThat(filePersistenceAdapter.existsByFileIdAndGranteeEmail(fileId, "guest@example.com")).isTrue();
            assertThat(filePersistenceAdapter.findByToken(created.getToken()))
                    .get().extracting(FileShare::getGranteeEmail).isEqualTo("guest@example.com");
        }

        @Test
        @DisplayName("대기 중인 게스트 공유를 클레임하면 회원 공유로 저장되고 더 이상 대기 목록에 없다")
        void claimsAPendingGuestShareAndPersistsIt() {
            UUID fileIdValue = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            FileShare created = filePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            created.claim(memberId);
            filePersistenceAdapter.saveFileShare(created);

            assertThat(filePersistenceAdapter.findPendingByGranteeEmail("guest@example.com")).isEmpty();
            assertThat(filePersistenceAdapter.findByFileIdAndSharedWithUserId(new FileId(fileIdValue), memberId))
                    .get().extracting(FileShare::getRole).isEqualTo(Role.VIEWER);
        }

        @Test
        @DisplayName("다른 파일의 대기 공유는 클레임 대상에서 제외된다")
        void findsPendingSharesOnlyForTheGivenEmail() {
            filePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));
            filePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("someone-else@example.com"), new FileShareRole(Role.VIEWER)));

            assertThat(filePersistenceAdapter.findPendingByGranteeEmail("guest@example.com")).hasSize(1);
        }

        @Test
        @DisplayName("공유를 해제하면 행이 사라진다")
        void deletesShare() {
            UUID fileIdValue = UUID.randomUUID();
            UUID granteeId = UUID.randomUUID();
            FileShare created = filePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));

            filePersistenceAdapter.deleteFileShare(new FileShareId(created.getId()));

            assertThat(filePersistenceAdapter.findByFileId(new FileId(fileIdValue))).isEmpty();
        }
    }

    @Nested
    @DisplayName("파일 접근을 기록할 때")
    class WhenRecordingFileAccess {

        @Test
        @DisplayName("같은 사용자-파일 조합을 다시 접근하면 새 행을 만들지 않고 시각만 갱신한다")
        void upsertsInsteadOfDuplicating() {
            UUID userId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            LocalDateTime firstAccess = LocalDateTime.now().minusMinutes(5);
            LocalDateTime secondAccess = LocalDateTime.now();

            filePersistenceAdapter.recordAccess(
                    FileAccess.of(new FileAccessUserId(userId), new FileAccessFileId(fileId), firstAccess));
            filePersistenceAdapter.recordAccess(
                    FileAccess.of(new FileAccessUserId(userId), new FileAccessFileId(fileId), secondAccess));

            var rows = springDataFileAccessRepository.findByUserIdOrderByAccessedAtDesc(
                    userId, org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getAccessedAt()).isEqualToIgnoringNanos(secondAccess);
        }

        @Test
        @DisplayName("최근 접근한 순서대로, limit 개수만큼 조회한다")
        void listsMostRecentFirstUpToLimit() {
            UUID userId = UUID.randomUUID();
            UUID olderFileId = UUID.randomUUID();
            UUID newerFileId = UUID.randomUUID();

            filePersistenceAdapter.recordAccess(FileAccess.of(
                    new FileAccessUserId(userId), new FileAccessFileId(olderFileId),
                    LocalDateTime.now().minusMinutes(10)));
            filePersistenceAdapter.recordAccess(FileAccess.of(
                    new FileAccessUserId(userId), new FileAccessFileId(newerFileId), LocalDateTime.now()));

            var result = filePersistenceAdapter.findByUserIdOrderByAccessedAtDesc(userId, 1);

            assertThat(result).extracting(FileAccess::getFileId).containsExactly(newerFileId);
        }
    }
}
