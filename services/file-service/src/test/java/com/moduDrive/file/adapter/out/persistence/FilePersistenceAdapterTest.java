package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.infrastructure.jpa.config.AuditingConfig;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest
// The @DataJpaTest slice drops AuditingConfig; without it @CreatedDate never fires and createdAt reads back null.
@Import({FilePersistenceAdapter.class, FileSharePersistenceAdapter.class, FileAccessPersistenceAdapter.class,
        FileMapper.class, AuditingConfig.class})
class FilePersistenceAdapterTest {

    @Autowired
    private FilePersistenceAdapter filePersistenceAdapter;
    @Autowired
    private FileSharePersistenceAdapter fileSharePersistenceAdapter;
    @Autowired
    private FileAccessPersistenceAdapter fileAccessPersistenceAdapter;
    @Autowired
    private SpringDataFileRepository springDataFileRepository;
    @Autowired
    private SpringDataFileAccessRepository springDataFileAccessRepository;
    @Autowired
    private SpringDataFileShareRepository springDataFileShareRepository;
    @Autowired
    private SpringDataFileVersionRepository springDataFileVersionRepository;
    @Autowired
    private SpringDataFileFavoriteRepository springDataFileFavoriteRepository;
    @Autowired
    private EntityManager entityManager;

    private final UUID namespaceIdValue = UUID.randomUUID();
    private final NamespaceId namespaceId = new NamespaceId(namespaceIdValue);

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
        @DisplayName("휴지통(TRASHED)에 있는 동일 이름 파일은 충돌로 잡히지 않는다")
        void doesNotFindATrashedFileAtTheSameSlot() {
            save("/1/docs", "report.pdf", FileStatus.TRASHED);

            var result = filePersistenceAdapter.findActiveByNamespaceIdAndPathAndName(namespaceId, "/1/docs", "report.pdf");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("퍼지된(DELETED) 동일 이름 파일도 충돌로 잡히지 않는다")
        void doesNotFindAPurgedFileAtTheSameSlot() {
            save("/1/docs", "report.pdf", FileStatus.DELETED);

            var result = filePersistenceAdapter.findActiveByNamespaceIdAndPathAndName(namespaceId, "/1/docs", "report.pdf");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("여러 이름을 한 번에 찾으면, 그 경로의 활성 파일 중 그 이름들만 돌려준다")
        void findsManyNamesOfOneFolderAtOnce() {
            save("/1/docs", "a.pdf");
            save("/1/docs", "b.pdf");
            save("/1/docs", "c.pdf");
            save("/1/docs", "d.pdf", FileStatus.TRASHED);
            save("/1/other", "a.pdf");

            var result = filePersistenceAdapter.findActiveByNamespaceIdAndPathAndNames(
                    namespaceId, "/1/docs", List.of("a.pdf", "b.pdf", "d.pdf", "x.pdf"));

            assertThat(result).extracting(File::getPath, File::getName)
                    .containsExactlyInAnyOrder(tuple("/1/docs", "a.pdf"), tuple("/1/docs", "b.pdf"));
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
        void doesNotCollideWithATrashedFileAtTheSameSlot() {
            save("/1/docs", "report.pdf", FileStatus.TRASHED);

            File saved = filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("report.pdf"),
                    new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("퍼지된 파일이 같은 자리에 있어도 새 활성 파일 저장은 유니크 제약에 걸리지 않는다")
        void doesNotCollideWithAPurgedFileAtTheSameSlot() {
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
    @DisplayName("공유 범위를 저장할 때")
    class WhenPersistingLinkSharing {

        @Test
        @DisplayName("LINK로 전환한 파일은 scope/뷰어 role이 그대로 다시 조회된다")
        void roundTripsAccessScopeAndLinkRole() {
            File saved = filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("public.pdf"),
                    new FilePath("/1"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));
            saved.enableLinkSharing();
            File linked = filePersistenceAdapter.saveFile(saved);

            var result = filePersistenceAdapter.findById(new File.FileId(linked.getId()));

            assertThat(result).isPresent();
            assertThat(result.get().getAccessScope()).isEqualTo(ShareScope.LINK);
            assertThat(result.get().getLinkRole()).isEqualTo(Role.VIEWER);
        }

        @Test
        @DisplayName("RESTRICTED로 되돌리면 scope/role이 함께 사라진다")
        void clearsScopeOnRestricted() {
            File saved = filePersistenceAdapter.saveFile(File.create(
                    new FileNamespaceId(namespaceIdValue), new FileName("was-public.pdf"),
                    new FilePath("/1"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)));
            saved.enableLinkSharing();
            File linked = filePersistenceAdapter.saveFile(saved);
            linked.disableLinkSharing();
            filePersistenceAdapter.saveFile(linked);

            var result = filePersistenceAdapter.findById(new File.FileId(linked.getId()));

            assertThat(result).isPresent();
            assertThat(result.get().getAccessScope()).isEqualTo(ShareScope.RESTRICTED);
            assertThat(result.get().getLinkRole()).isNull();
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

            FileShare created = fileSharePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(ownerIdValue),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));

            created.changeRole(new FileShareRole(Role.EDITOR));
            fileSharePersistenceAdapter.saveFileShare(created);

            assertThat(fileSharePersistenceAdapter.findByFileId(fileId)).hasSize(1);
            assertThat(fileSharePersistenceAdapter.findByFileIdAndSharedWithUserId(fileId, granteeId))
                    .get().extracting(FileShare::getRole).isEqualTo(Role.EDITOR);
            assertThat(fileSharePersistenceAdapter.existsByFileIdAndSharedWithUserId(fileId, granteeId)).isTrue();
        }

        @Test
        @DisplayName("사용자 단위 조회는 최근 공유받은 순으로 반환한다")
        void listsSharesForUserMostRecentFirst() {
            UUID granteeId = UUID.randomUUID();
            UUID olderFileId = UUID.randomUUID();
            UUID newerFileId = UUID.randomUUID();

            fileSharePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(olderFileId), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));
            fileSharePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(newerFileId), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));

            assertThat(fileSharePersistenceAdapter.findBySharedWithUserId(granteeId))
                    .extracting(FileShare::getFileId)
                    .containsExactly(newerFileId, olderFileId);
        }

        @Test
        @DisplayName("같은 파일-사용자 조합은 DB 유니크 제약으로 두 번 저장되지 않는다")
        void rejectsDuplicateShareRowAtTheDatabaseLevel() {
            UUID fileIdValue = UUID.randomUUID();
            UUID ownerIdValue = UUID.randomUUID();
            UUID granteeId = UUID.randomUUID();
            springDataFileShareRepository.saveAndFlush(
                    new FileShareJpaEntity(fileIdValue, ownerIdValue, granteeId, Role.VIEWER));

            // The app-layer existsBy check can't see a concurrent insert; uk_file_share_file_user is
            // what actually rejects the second row, so assert the constraint exists, not the check.
            Throwable thrown = catchThrowable(() -> springDataFileShareRepository.saveAndFlush(
                    new FileShareJpaEntity(fileIdValue, ownerIdValue, granteeId, Role.EDITOR)));

            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("비회원 이메일 초대는 자기 토큰을 가진 RESTRICTED 공유로 저장되고 토큰으로 조회된다")
        void savesAndFindsAPendingGuestShareByItsOwnToken() {
            UUID fileIdValue = UUID.randomUUID();
            FileId fileId = new FileId(fileIdValue);

            FileShare created = fileSharePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            assertThat(created.getSharedWithUserId()).isNull();
            assertThat(created.getToken()).isNotNull();
            assertThat(fileSharePersistenceAdapter.existsByFileIdAndGranteeEmail(fileId, "guest@example.com")).isTrue();
            // The row itself, not just its presence, must be reachable by email (see RevokeFileShareService).
            assertThat(fileSharePersistenceAdapter.findByFileIdAndGranteeEmail(fileId, "guest@example.com"))
                    .get().extracting(FileShare::getId).isEqualTo(created.getId());
            assertThat(fileSharePersistenceAdapter.findByToken(created.getToken()))
                    .get().extracting(FileShare::getGranteeEmail).isEqualTo("guest@example.com");
        }

        @Test
        @DisplayName("다른 이메일로는 게스트 공유가 조회되지 않는다")
        void findByFileIdAndGranteeEmailMissesADifferentEmail() {
            UUID fileIdValue = UUID.randomUUID();
            fileSharePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            assertThat(fileSharePersistenceAdapter.findByFileIdAndGranteeEmail(new FileId(fileIdValue), "someone-else@example.com"))
                    .isEmpty();
        }

        @Test
        @DisplayName("대기 중인 게스트 공유를 클레임하면 회원 공유로 저장되고 더 이상 대기 목록에 없다")
        void claimsAPendingGuestShareAndPersistsIt() {
            UUID fileIdValue = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            FileShare created = fileSharePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            UUID token = created.getToken();
            created.claim(memberId);
            fileSharePersistenceAdapter.saveFileShare(created);

            assertThat(fileSharePersistenceAdapter.findPendingByGranteeEmail("guest@example.com")).isEmpty();
            assertThat(fileSharePersistenceAdapter.findByFileIdAndSharedWithUserId(new FileId(fileIdValue), memberId))
                    .get().extracting(FileShare::getRole).isEqualTo(Role.VIEWER);
            // the token survives the claim so the emailed /public link still resolves
            assertThat(fileSharePersistenceAdapter.findByToken(token)).isPresent();
            // claim() clears granteeEmail, so the row is no longer reachable by its old email (RevokeFileShareService relies on this)
            assertThat(fileSharePersistenceAdapter.findByFileIdAndGranteeEmail(new FileId(fileIdValue), "guest@example.com"))
                    .isEmpty();
        }

        @Test
        @DisplayName("다른 파일의 대기 공유는 클레임 대상에서 제외된다")
        void findsPendingSharesOnlyForTheGivenEmail() {
            fileSharePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));
            fileSharePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("someone-else@example.com"), new FileShareRole(Role.VIEWER)));

            assertThat(fileSharePersistenceAdapter.findPendingByGranteeEmail("guest@example.com")).hasSize(1);
        }

        @Test
        @DisplayName("초대 토큰은 기한 없이 토큰으로 조회된다")
        void guestInviteTokenIsFoundWithoutExpiry() {
            UUID token = UUID.randomUUID();
            springDataFileShareRepository.save(
                    new FileShareJpaEntity(UUID.randomUUID(), UUID.randomUUID(), null, Role.VIEWER, token, "guest@example.com"));

            assertThat(fileSharePersistenceAdapter.findByToken(token)).isPresent();
            assertThat(fileSharePersistenceAdapter.findByToken(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("여러 파일에 대한 한 사용자의 공유를 한 번에 조회한다")
        void findsAUsersGrantsOnSeveralFilesAtOnce() {
            UUID grantee = UUID.randomUUID();
            UUID parent = UUID.randomUUID();
            UUID child = UUID.randomUUID();
            UUID unrelated = UUID.randomUUID();
            springDataFileShareRepository.save(new FileShareJpaEntity(parent, UUID.randomUUID(), grantee, Role.VIEWER));
            springDataFileShareRepository.save(new FileShareJpaEntity(child, UUID.randomUUID(), grantee, Role.EDITOR));
            springDataFileShareRepository.save(new FileShareJpaEntity(unrelated, UUID.randomUUID(), grantee, Role.EDITOR));
            springDataFileShareRepository.save(new FileShareJpaEntity(parent, UUID.randomUUID(), UUID.randomUUID(), Role.EDITOR));

            List<FileShare> grants = fileSharePersistenceAdapter.findBySharedWithUserIdAndFileIdIn(
                    grantee, List.of(new FileId(parent), new FileId(child)));

            assertThat(grants).extracting(FileShare::getFileId).containsExactlyInAnyOrder(parent, child);
        }

        @Test
        @DisplayName("공유를 해제하면 행이 사라진다")
        void deletesShare() {
            UUID fileIdValue = UUID.randomUUID();
            UUID granteeId = UUID.randomUUID();
            FileShare created = fileSharePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));

            fileSharePersistenceAdapter.deleteFileShare(new FileShareId(created.getId()));

            assertThat(fileSharePersistenceAdapter.findByFileId(new FileId(fileIdValue))).isEmpty();
        }
    }

    @Nested
    @DisplayName("버전을 id 여러 개로 한 번에 찾을 때")
    class WhenFindingVersionsByIds {

        @Test
        @DisplayName("요청한 id의 버전만 블록 목록과 함께 돌려준다")
        void returnsOnlyTheRequestedVersions() {
            UUID fileIdValue = UUID.randomUUID();
            UUID v1 = springDataFileVersionRepository.save(version(fileIdValue, UUID.randomUUID(), List.of("h1", "h2", "h1"))).getId();
            springDataFileVersionRepository.save(version(fileIdValue, UUID.randomUUID(), List.of("h3")));

            assertThat(filePersistenceAdapter.findAllByIds(List.of(v1)))
                    .extracting(com.moduDrive.file.domain.model.FileVersion::getHashes)
                    .containsExactly(List.of("h1", "h2", "h1"));
        }
    }

    @Nested
    @DisplayName("버전을 uploadId로 찾을 때")
    class WhenFindingVersionByUploadId {

        @Test
        @DisplayName("그 uploadId의 버전을 돌려준다")
        void returnsTheVersion() {
            UUID uploadId = UUID.randomUUID();
            springDataFileVersionRepository.save(version(UUID.randomUUID(), uploadId, List.of("h1")));

            assertThat(filePersistenceAdapter.findByUploadId(uploadId))
                    .get().extracting(com.moduDrive.file.domain.model.FileVersion::getUploadId).isEqualTo(uploadId);
            assertThat(filePersistenceAdapter.findByUploadId(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("여러 uploadId를 한 번에 찾으면, 버전이 있는 것만 돌려준다")
        void returnsTheVersionsOfManyUploadIds() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            springDataFileVersionRepository.save(version(UUID.randomUUID(), first, List.of("h1")));
            springDataFileVersionRepository.save(version(UUID.randomUUID(), second, List.of("h2", "h3")));
            springDataFileVersionRepository.save(version(UUID.randomUUID(), UUID.randomUUID(), List.of("h4")));

            assertThat(filePersistenceAdapter.findAllByUploadIds(List.of(first, second, UUID.randomUUID())))
                    .extracting(com.moduDrive.file.domain.model.FileVersion::getUploadId,
                            com.moduDrive.file.domain.model.FileVersion::getHashes)
                    .containsExactlyInAnyOrder(tuple(first, List.of("h1")), tuple(second, List.of("h2", "h3")));
        }

        @Test
        @DisplayName("같은 uploadId로 두 번째 버전은 저장되지 않는다")
        void rejectsASecondVersionWithTheSameUploadId() {
            UUID fileIdValue = UUID.randomUUID();
            UUID uploadId = UUID.randomUUID();
            springDataFileVersionRepository.saveAndFlush(version(fileIdValue, uploadId, List.of("h1")));

            // Two concurrent commits both miss findByUploadId; uk_file_version_upload_id rejects the second.
            Throwable thrown = catchThrowable(() -> springDataFileVersionRepository.saveAndFlush(
                    version(fileIdValue, uploadId, List.of("h1"))));

            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("파일을 영구 삭제할 때")
    class WhenDeletingAFile {

        @Test
        @DisplayName("purge는 파일 행을 tombstone(deleted_at)으로 남기고 버전/공유/즐겨찾기만 지운다")
        void purgeKeepsTheRowAsATombstoneAndClearsAttachments() {
            UUID fileIdValue = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "report.pdf", "/1", UUID.randomUUID(), FileStatus.TRASHED, false)).getId();
            springDataFileVersionRepository.save(version(fileIdValue, UUID.randomUUID(), List.of("h1")));
            springDataFileShareRepository.save(
                    new FileShareJpaEntity(fileIdValue, UUID.randomUUID(), UUID.randomUUID(), Role.VIEWER));
            springDataFileFavoriteRepository.save(new FileFavoriteJpaEntity(UUID.randomUUID(), fileIdValue));
            UUID deletedBy = UUID.randomUUID();

            filePersistenceAdapter.purgeFile(new FileId(fileIdValue), deletedBy);
            entityManager.flush(); // what the transaction's commit does
            entityManager.clear();

            FileJpaEntity tombstone = springDataFileRepository.findById(fileIdValue).orElseThrow();
            assertThat(tombstone.getDeletedAt()).isNotNull();
            assertThat(tombstone.getDeletedBy()).isEqualTo(deletedBy);
            assertThat(tombstone.getStatus()).isEqualTo(FileStatus.DELETED);
            assertThat(springDataFileVersionRepository.findByFileIdOrderByCreatedAtDesc(
                    fileIdValue, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
            assertThat(springDataFileShareRepository.findByFileId(fileIdValue)).isEmpty();
            assertThat(springDataFileFavoriteRepository.count()).isZero();
        }

        @Test
        @DisplayName("그사이 복원돼 휴지통에 없는 파일은 purge가 버전/공유/즐겨찾기를 지우지 않는다")
        void purgeLeavesAFileRestoredMeanwhileUntouched() {
            UUID fileIdValue = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "report.pdf", "/1", UUID.randomUUID(), FileStatus.UPLOADED, false)).getId();
            springDataFileVersionRepository.save(version(fileIdValue, UUID.randomUUID(), List.of("h1")));

            filePersistenceAdapter.purgeFile(new FileId(fileIdValue), UUID.randomUUID());
            entityManager.flush();
            entityManager.clear();

            assertThat(springDataFileRepository.findById(fileIdValue).orElseThrow().getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(springDataFileVersionRepository.findByFileIdOrderByCreatedAtDesc(
                    fileIdValue, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("여러 파일을 한 번에 영구 삭제할 때")
    class WhenPurgingSeveralFiles {

        @Test
        @DisplayName("전부 tombstone으로 만들고 그 파일들의 버전·블록 목록·공유·즐겨찾기만 지운다")
        void tombstonesAllAndClearsOnlyTheirAttachments() {
            UUID a = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "a.txt", "/1", UUID.randomUUID(), FileStatus.TRASHED, false)).getId();
            UUID b = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "b.txt", "/1", UUID.randomUUID(), FileStatus.TRASHED, false)).getId();
            UUID other = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "c.txt", "/1", UUID.randomUUID(), FileStatus.TRASHED, false)).getId();
            for (UUID id : List.of(a, b, other)) {
                springDataFileVersionRepository.save(version(id, UUID.randomUUID(), List.of("h1", "h2")));
                springDataFileShareRepository.save(new FileShareJpaEntity(id, UUID.randomUUID(), UUID.randomUUID(), Role.VIEWER));
                springDataFileFavoriteRepository.save(new FileFavoriteJpaEntity(UUID.randomUUID(), id));
            }
            UUID deletedBy = UUID.randomUUID();

            filePersistenceAdapter.purgeFiles(List.of(new FileId(a), new FileId(b)), deletedBy);
            entityManager.flush();
            entityManager.clear();

            assertThat(springDataFileRepository.findAllById(List.of(a, b)))
                    .allSatisfy(f -> {
                        assertThat(f.getStatus()).isEqualTo(FileStatus.DELETED);
                        assertThat(f.getDeletedBy()).isEqualTo(deletedBy);
                    });
            assertThat(springDataFileRepository.findById(other).orElseThrow().getStatus()).isEqualTo(FileStatus.TRASHED);
            assertThat(springDataFileVersionRepository.findAll()).extracting(FileVersionJpaEntity::getFileId).containsExactly(other);
            assertThat(count("select count(*) from file_version_block")).isEqualTo(2);
            assertThat(springDataFileShareRepository.findAll()).extracting(FileShareJpaEntity::getFileId).containsExactly(other);
            assertThat(springDataFileFavoriteRepository.findAll()).extracting(FileFavoriteJpaEntity::getFileId).containsExactly(other);
        }

        @Test
        @DisplayName("섞여 들어온 휴지통 밖 파일은 tombstone도 안 되고 버전도 지워지지 않는다")
        void leavesARestoredFileInTheBatchUntouched() {
            UUID trashed = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "a.txt", "/1", UUID.randomUUID(), FileStatus.TRASHED, false)).getId();
            UUID restored = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "b.txt", "/1", UUID.randomUUID(), FileStatus.UPLOADED, false)).getId();
            springDataFileVersionRepository.save(version(trashed, UUID.randomUUID(), List.of("h1")));
            springDataFileVersionRepository.save(version(restored, UUID.randomUUID(), List.of("h2")));

            filePersistenceAdapter.purgeFiles(List.of(new FileId(trashed), new FileId(restored)), null);
            entityManager.flush();
            entityManager.clear();

            assertThat(springDataFileRepository.findById(restored).orElseThrow().getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(springDataFileVersionRepository.findAll()).extracting(FileVersionJpaEntity::getFileId).containsExactly(restored);
        }
    }

    @Nested
    @DisplayName("디렉토리 하위 전체를 한 번에 바꿀 때")
    class WhenRewritingASubtree {

        private final LocalDateTime trashedAt = LocalDateTime.of(2026, 10, 1, 9, 0);

        private UUID row(String path, String name, FileStatus status) {
            UUID id = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, name, path, UUID.randomUUID(), status, false)).getId();
            if (status == FileStatus.TRASHED) {
                entityManager.flush();
                entityManager.createNativeQuery("update file set trashed_at = :at where id = :id")
                        .setParameter("at", trashedAt.minusDays(1)).setParameter("id", id).executeUpdate();
            }
            return id;
        }

        private FileJpaEntity reload(UUID id) {
            entityManager.flush();
            entityManager.clear();
            return springDataFileRepository.findById(id).orElseThrow();
        }

        @Test
        @DisplayName("이동하면 직속 자식과 손자까지 경로 접두사가 바뀌고, 접두사만 겹치는 형제는 그대로다")
        void moveRewritesNestedPathsOnly() {
            UUID child = row("/A", "b.txt", FileStatus.UPLOADED);
            UUID grandchild = row("/A/Sub", "c.txt", FileStatus.TRASHED);
            UUID sibling = row("/A2", "d.txt", FileStatus.UPLOADED);

            filePersistenceAdapter.moveSubtree(namespaceId, "/A", "/C/A");

            assertThat(reload(child).getPath()).isEqualTo("/C/A");
            assertThat(reload(grandchild).getPath()).isEqualTo("/C/A/Sub");
            assertThat(reload(sibling).getPath()).isEqualTo("/A2");
        }

        @Test
        @DisplayName("폴더 이름의 LIKE 와일드카드(_)를 글자 그대로 다뤄, 패턴에만 맞는 형제 폴더는 옮기지 않는다")
        void moveTreatsLikeWildcardsLiterally() {
            UUID child = row("/a_b", "x.txt", FileStatus.UPLOADED);
            UUID lookalike = row("/aXb", "y.txt", FileStatus.UPLOADED);

            filePersistenceAdapter.moveSubtree(namespaceId, "/a_b", "/C/a_b");

            assertThat(reload(child).getPath()).isEqualTo("/C/a_b");
            assertThat(reload(lookalike).getPath()).isEqualTo("/aXb");
        }

        @Test
        @DisplayName("이모지가 든 이름도 같은 자리에서 잘라 옮긴다")
        void moveCountsCharactersNotUtf16Units() {
            UUID child = row("/📁/x", "b.txt", FileStatus.UPLOADED);

            filePersistenceAdapter.moveSubtree(namespaceId, "/📁", "/C/📁");

            assertThat(reload(child).getPath()).isEqualTo("/C/📁/x");
        }

        @Test
        @DisplayName("휴지통으로 보내면 살아 있는 항목만 그 시각으로 보내고, 이미 휴지통이거나 퍼지된 항목의 시각은 그대로 둔다")
        void trashLeavesAlreadyRemovedRowsAlone() {
            UUID active = row("/A", "b.txt", FileStatus.UPLOADED);
            UUID alreadyTrashed = row("/A", "c.txt", FileStatus.TRASHED);
            UUID purged = row("/A", "d.txt", FileStatus.DELETED);

            filePersistenceAdapter.trashSubtree(namespaceId, "/A", trashedAt);

            assertThat(reload(active)).satisfies(f -> {
                assertThat(f.getStatus()).isEqualTo(FileStatus.TRASHED);
                assertThat(f.getTrashedAt()).isEqualTo(trashedAt);
                assertThat(f.getActiveSlotName()).isNull();
            });
            assertThat(reload(alreadyTrashed).getTrashedAt()).isEqualTo(trashedAt.minusDays(1));
            assertThat(reload(purged).getStatus()).isEqualTo(FileStatus.DELETED);
        }

        @Test
        @DisplayName("복원하면 휴지통 항목만 되살리고, 퍼지된 tombstone은 되살리지 않는다")
        void restoreRevivesTrashedRowsOnly() {
            UUID trashed = row("/A", "b.txt", FileStatus.TRASHED);
            UUID tombstone = row("/A", "c.txt", FileStatus.DELETED);

            filePersistenceAdapter.restoreSubtree(namespaceId, "/A");

            assertThat(reload(trashed)).satisfies(f -> {
                assertThat(f.getStatus()).isEqualTo(FileStatus.UPLOADED);
                assertThat(f.getTrashedAt()).isNull();
                assertThat(f.getActiveSlotName()).isEqualTo("b.txt");
            });
            assertThat(reload(tombstone).getStatus()).isEqualTo(FileStatus.DELETED);
        }

        @Test
        @DisplayName("복원할 자리를 다른 활성 파일이 차지했으면 비즈니스 예외로 변환된다")
        void restoreOntoATakenSlotIsABusinessError() {
            row("/A", "b.txt", FileStatus.TRASHED);
            row("/A", "b.txt", FileStatus.UPLOADED);

            Throwable thrown = catchThrowable(() -> filePersistenceAdapter.restoreSubtree(namespaceId, "/A"));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) thrown).getExceptionCase()).isEqualTo(FileExceptionCase.FILE_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("파일 위의 폴더들을 찾을 때")
    class WhenFindingAncestorDirectories {

        private UUID directory(String path, String name, FileStatus status) {
            return springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, name, path, UUID.randomUUID(), status, true)).getId();
        }

        @Test
        @DisplayName("경로의 폴더를 루트부터 순서대로 한 번에 돌려주고, 휴지통 폴더와 이름만 같은 다른 자리의 폴더는 뺀다")
        void returnsExactSlotsRootMostFirst() {
            UUID a = directory("/", "a", FileStatus.UPLOADED);
            UUID b = directory("/a", "b", FileStatus.UPLOADED);
            directory("/a", "b", FileStatus.TRASHED);
            UUID c = directory("/a/b", "c", FileStatus.UPLOADED);
            directory("/", "b", FileStatus.UPLOADED); // a "b" at the root is not /a/b
            entityManager.flush();

            List<File> ancestors = filePersistenceAdapter.findActiveAncestorDirectories(namespaceId, "/a/b/c");

            assertThat(ancestors).extracting(File::getId).containsExactly(a, b, c);
        }

        @Test
        @DisplayName("루트 바로 아래 파일은 위에 폴더가 없다")
        void nothingAboveTheRoot() {
            assertThat(filePersistenceAdapter.findActiveAncestorDirectories(namespaceId, "/")).isEmpty();
        }
    }

    private long count(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
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

            fileAccessPersistenceAdapter.recordAccess(
                    FileAccess.of(new FileAccessUserId(userId), new FileAccessFileId(fileId), firstAccess));
            fileAccessPersistenceAdapter.recordAccess(
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

            fileAccessPersistenceAdapter.recordAccess(FileAccess.of(
                    new FileAccessUserId(userId), new FileAccessFileId(olderFileId),
                    LocalDateTime.now().minusMinutes(10)));
            fileAccessPersistenceAdapter.recordAccess(FileAccess.of(
                    new FileAccessUserId(userId), new FileAccessFileId(newerFileId), LocalDateTime.now()));

            var result = fileAccessPersistenceAdapter.findByUserIdOrderByAccessedAtDesc(userId, 1);

            assertThat(result).extracting(FileAccess::getFileId).containsExactly(newerFileId);
        }

        @Test
        @DisplayName("여러 파일을 한 번에 기록하면 없던 행은 만들고 있던 행은 시각만 갱신한다")
        void recordsManyAtOnceUpsertingExistingRows() {
            UUID userId = UUID.randomUUID();
            UUID seenFileId = UUID.randomUUID();
            UUID newFileId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();
            fileAccessPersistenceAdapter.recordAccess(FileAccess.of(
                    new FileAccessUserId(userId), new FileAccessFileId(seenFileId), now.minusDays(1)));

            fileAccessPersistenceAdapter.recordAccesses(userId, List.of(seenFileId, newFileId), now);
            entityManager.flush();

            var rows = springDataFileAccessRepository.findByUserIdOrderByAccessedAtDesc(
                    userId, org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(rows).hasSize(2);
            assertThat(rows).allSatisfy(row -> assertThat(row.getAccessedAt()).isEqualToIgnoringNanos(now));
        }
    }

    @Nested
    @DisplayName("디렉토리를 커서 페이지네이션으로 조회할 때")
    class WhenScrollingADirectoryPage {

        private void saveEntry(String path, String name, boolean directory, FileStatus status) {
            springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, name, path, UUID.randomUUID(), status, directory));
            // Force a DB round trip so the cursor is built from persisted column values (timestamp
            // precision, NULL ordering) rather than the identity-mapped entity — matches production,
            // where the listing runs in its own read-only transaction.
            entityManager.flush();
            entityManager.clear();
        }

        private java.util.List<String> drainNames(com.moduDrive.file.domain.model.DirectorySort sort, int pageSize) {
            java.util.List<String> names = new java.util.ArrayList<>();
            String cursor = null;
            for (int guard = 0; guard < 1000; guard++) {
                var page = filePersistenceAdapter.findDirectoryPage(namespaceId, "/1", sort, cursor, pageSize);
                page.content().forEach(f -> names.add(f.getName()));
                if (!page.hasNext()) {
                    return names;
                }
                cursor = page.nextCursor();
            }
            throw new AssertionError("pagination did not terminate for sort " + sort);
        }

        @Test
        @DisplayName("디렉토리가 파일보다 먼저, 그 다음 이름순 — 페이지를 이어붙이면 전체가 중복/누락 없이 정렬된다")
        void keysetPagesCoverEveryEntryDirectoriesFirstThenByName() {
            saveEntry("/1", "b-dir", true, FileStatus.UPLOADED);
            saveEntry("/1", "a-dir", true, FileStatus.UPLOADED);
            saveEntry("/1", "e.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "c.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "d.txt", false, FileStatus.UPLOADED);

            var names = drainNames(com.moduDrive.file.domain.model.DirectorySort.NAME_ASC, 2);

            assertThat(names).containsExactly("a-dir", "b-dir", "c.txt", "d.txt", "e.txt");
        }

        @Test
        @DisplayName("첫 페이지는 hasNext=true 와 nextCursor 를, 마지막 페이지는 hasNext=false 를 준다")
        void reportsHasNextAndACursorUntilTheLastPage() {
            saveEntry("/1", "a.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "b.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "c.txt", false, FileStatus.UPLOADED);

            var first = filePersistenceAdapter.findDirectoryPage(
                    namespaceId, "/1", com.moduDrive.file.domain.model.DirectorySort.NAME_ASC, null, 2);
            assertThat(first.content()).extracting(File::getName).containsExactly("a.txt", "b.txt");
            assertThat(first.hasNext()).isTrue();
            assertThat(first.nextCursor()).isNotBlank();

            var second = filePersistenceAdapter.findDirectoryPage(
                    namespaceId, "/1", com.moduDrive.file.domain.model.DirectorySort.NAME_ASC, first.nextCursor(), 2);
            assertThat(second.content()).extracting(File::getName).containsExactly("c.txt");
            assertThat(second.hasNext()).isFalse();
            assertThat(second.nextCursor()).isNull();
        }

        @Test
        @DisplayName("NAME_DESC 는 각 그룹 안에서 역순 — 디렉토리는 여전히 먼저")
        void nameDescReversesWithinEachGroup() {
            saveEntry("/1", "a-dir", true, FileStatus.UPLOADED);
            saveEntry("/1", "b-dir", true, FileStatus.UPLOADED);
            saveEntry("/1", "x.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "y.txt", false, FileStatus.UPLOADED);

            var names = drainNames(com.moduDrive.file.domain.model.DirectorySort.NAME_DESC, 10);

            assertThat(names).containsExactly("b-dir", "a-dir", "y.txt", "x.txt");
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(com.moduDrive.file.domain.model.DirectorySort.class)
        @DisplayName("어떤 정렬이든 페이지를 이어붙이면 전체가 중복·누락 없이 나온다")
        void keysetPagingIsExhaustiveForEverySort(com.moduDrive.file.domain.model.DirectorySort sort) {
            saveEntry("/1", "a-dir", true, FileStatus.UPLOADED);
            saveEntry("/1", "b-dir", true, FileStatus.UPLOADED);
            saveEntry("/1", "p.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "q.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "r.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "s.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "t.txt", false, FileStatus.UPLOADED);

            var names = drainNames(sort, 2);

            assertThat(names).containsExactlyInAnyOrder(
                    "a-dir", "b-dir", "p.txt", "q.txt", "r.txt", "s.txt", "t.txt");
        }

        @Test
        @DisplayName("TRASHED/DELETED 항목과 다른 경로/네임스페이스의 항목은 제외된다")
        void excludesRemovedAndOutOfScopeEntries() {
            saveEntry("/1", "keep.txt", false, FileStatus.UPLOADED);
            saveEntry("/1", "trashed.txt", false, FileStatus.TRASHED);
            saveEntry("/1", "purged.txt", false, FileStatus.DELETED);
            saveEntry("/1/sub", "elsewhere.txt", false, FileStatus.UPLOADED);
            springDataFileRepository.save(new FileJpaEntity(
                    UUID.randomUUID(), "other-ns.txt", "/1", UUID.randomUUID(), FileStatus.UPLOADED, false));

            var page = filePersistenceAdapter.findDirectoryPage(
                    namespaceId, "/1", com.moduDrive.file.domain.model.DirectorySort.NAME_ASC, null, 50);

            assertThat(page.content()).extracting(File::getName).containsExactly("keep.txt");
        }
    }

    @Nested
    @DisplayName("보존 기간이 지난 버전을 찾을 때")
    class WhenFindingVersionsToPrune {

        private final LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        /** A file with versions created {@code daysAgo} days back each, oldest first; the last is current. */
        private UUID fileWithVersions(int... daysAgo) {
            FileJpaEntity file = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, UUID.randomUUID() + ".txt", "/", UUID.randomUUID(), FileStatus.UPLOADED, false));
            UUID current = null;
            for (int days : daysAgo) {
                current = springDataFileVersionRepository.saveAndFlush(
                        version(file.getId(), UUID.randomUUID(), List.of("h1"))).getId();
                entityManager.createNativeQuery("update file_version set created_at = :at where id = :id")
                        .setParameter("at", LocalDateTime.now().minusDays(days))
                        .setParameter("id", current)
                        .executeUpdate();
            }
            file.applyChanges(file.getName(), file.getPath(), current, 10L, FileStatus.UPLOADED,
                    ShareScope.RESTRICTED, null, null);
            springDataFileRepository.saveAndFlush(file);
            entityManager.clear();
            return file.getId();
        }

        private List<LocalDateTime> createdAtOf(List<com.moduDrive.file.domain.model.FileVersion> versions) {
            return versions.stream().map(v -> springDataFileVersionRepository.findById(v.getId()).orElseThrow().getCreatedAt()).toList();
        }

        @Test
        @DisplayName("30일이 지난 버전은 고르지만 현재 버전은 오래돼도 남긴다")
        void picksOldVersionsButNeverTheCurrentOne() {
            UUID fileId = fileWithVersions(60, 40, 5, 45);

            var picked = filePersistenceAdapter.findVersionsToPrune(new FileId(fileId), cutoff, 100);

            // 60 and 40 days old go; 5 days stays; the 45-day one is current and kept by the current-version rule.
            assertThat(picked).hasSize(2);
            assertThat(createdAtOf(picked)).allMatch(at -> at.isBefore(cutoff));
        }

        @Test
        @DisplayName("휴지통에 있는 파일의 버전은 고르지 않는다")
        void skipsATrashedFile() {
            UUID fileId = fileWithVersions(60, 1);
            entityManager.createNativeQuery("update file set status = 'TRASHED' where id = :id")
                    .setParameter("id", fileId)
                    .executeUpdate();

            assertThat(filePersistenceAdapter.findVersionsToPrune(new FileId(fileId), cutoff, 100)).isEmpty();
        }

        @Test
        @DisplayName("최근 버전이라도 최신 N개 밖이면 고른다")
        void picksRecentVersionsOutsideTheNewestKeep() {
            UUID fileId = fileWithVersions(3, 2, 1);

            var picked = filePersistenceAdapter.findVersionsToPrune(new FileId(fileId), cutoff, 2);

            assertThat(picked).hasSize(1);
            assertThat(createdAtOf(picked).getFirst()).isBefore(LocalDateTime.now().minusDays(2).plusHours(1));
        }

        @Test
        @DisplayName("버전 하나뿐인 파일과 최근 버전만 있는 파일은 후보에 넣지 않는다")
        void listsOnlyFilesThatMayHaveSomethingToDrop() {
            UUID old = fileWithVersions(60, 1);
            UUID tooMany = fileWithVersions(3, 2, 1);
            fileWithVersions(60);
            fileWithVersions(2, 1);

            List<UUID> candidates = filePersistenceAdapter.findFileIdsWithVersionsToPrune(cutoff, 2, new UUID(0, 0), 10);

            assertThat(candidates).containsExactlyInAnyOrder(old, tooMany);
        }

        @Test
        @DisplayName("후보는 파일 id 순서로 after 다음부터 limit개만 준다")
        void pagesCandidatesByFileId() {
            UUID a = fileWithVersions(60, 1);
            UUID b = fileWithVersions(60, 1);
            // Postgres orders uuids as unsigned bytes; UUID.compareTo is signed — compare the hex text.
            UUID first = a.toString().compareTo(b.toString()) < 0 ? a : b;
            UUID second = first == a ? b : a;

            assertThat(filePersistenceAdapter.findFileIdsWithVersionsToPrune(cutoff, 100, new UUID(0, 0), 1))
                    .containsExactly(first);
            assertThat(filePersistenceAdapter.findFileIdsWithVersionsToPrune(cutoff, 100, first, 10))
                    .containsExactly(second);
        }

        @Test
        @DisplayName("고른 버전을 지우면 블록 목록 행도 같이 지워진다")
        void deletingVersionsDropsTheirBlocklists() {
            UUID fileId = fileWithVersions(60, 1);
            var picked = filePersistenceAdapter.findVersionsToPrune(new FileId(fileId), cutoff, 100);

            filePersistenceAdapter.deleteFileVersions(picked);
            entityManager.flush();

            assertThat(springDataFileVersionRepository.findByFileIdOrderByCreatedAtDesc(fileId, org.springframework.data.domain.Pageable.unpaged())).hasSize(1);
            Number blocklistRows = (Number) entityManager.createNativeQuery(
                            "select count(*) from file_version_block where version_id = :id")
                    .setParameter("id", picked.getFirst().getId())
                    .getSingleResult();
            assertThat(blocklistRows.longValue()).isZero();
        }
    }

    private static FileVersionJpaEntity version(UUID fileId, UUID uploadId, List<String> hashes) {
        return new FileVersionJpaEntity(fileId, UUID.randomUUID(), 10L, uploadId, hashes);
    }
}
