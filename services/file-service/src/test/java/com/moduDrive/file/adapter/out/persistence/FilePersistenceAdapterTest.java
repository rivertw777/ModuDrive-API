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
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest
// AuditingConfig is a third-party auto-configuration, so the @DataJpaTest slice drops it —
// without it @CreatedDate never fires and createdAt reads back null, unlike production.
@Import({FilePersistenceAdapter.class, FileMapper.class, AuditingConfig.class})
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
        @DisplayName("새 파일 여러 개를 한 번에 저장하면 입력 순서대로 id가 붙어 돌아온다")
        void savesNewFilesInBulkInInputOrder() {
            var result = filePersistenceAdapter.saveNewFiles(List.of(
                    File.createDirectory(new FileNamespaceId(namespaceIdValue), new FileName("사진"),
                            new FilePath("/"), new FileOwnerId(UUID.randomUUID())),
                    File.create(new FileNamespaceId(namespaceIdValue), new FileName("a.jpg"),
                            new FilePath("/사진"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false))));

            assertThat(result).extracting(File::getName).containsExactly("사진", "a.jpg");
            assertThat(result).allSatisfy(file -> assertThat(file.getId()).isNotNull());
            assertThat(springDataFileRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("한 번에 저장하는 중 활성 파일과 자리가 겹치면 비즈니스 예외로 변환된다")
        void translatesASlotConflictInABulkSave() {
            save("/1/docs", "report.pdf");

            Throwable thrown = catchThrowable(() -> filePersistenceAdapter.saveNewFiles(List.of(
                    File.create(new FileNamespaceId(namespaceIdValue), new FileName("other.pdf"),
                            new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)),
                    File.create(new FileNamespaceId(namespaceIdValue), new FileName("report.pdf"),
                            new FilePath("/1/docs"), new FileOwnerId(UUID.randomUUID()), new FileIsDirectory(false)))));

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
        @DisplayName("사용자 단위 조회는 최근 공유받은 순으로 반환한다")
        void listsSharesForUserMostRecentFirst() {
            UUID granteeId = UUID.randomUUID();
            UUID olderFileId = UUID.randomUUID();
            UUID newerFileId = UUID.randomUUID();

            filePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(olderFileId), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));
            filePersistenceAdapter.saveFileShare(FileShare.create(
                    new FileShareFileId(newerFileId), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareSharedWithUserId(granteeId), new FileShareRole(Role.VIEWER)));

            assertThat(filePersistenceAdapter.findBySharedWithUserId(granteeId))
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

            FileShare created = filePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            assertThat(created.getSharedWithUserId()).isNull();
            assertThat(created.getToken()).isNotNull();
            assertThat(filePersistenceAdapter.existsByFileIdAndGranteeEmail(fileId, "guest@example.com")).isTrue();
            // Revoking a guest's grants has to follow them across ancestor directories, so the row
            // itself — not just its presence — has to be reachable by email (see RevokeFileShareService).
            assertThat(filePersistenceAdapter.findByFileIdAndGranteeEmail(fileId, "guest@example.com"))
                    .get().extracting(FileShare::getId).isEqualTo(created.getId());
            assertThat(filePersistenceAdapter.findByToken(created.getToken()))
                    .get().extracting(FileShare::getGranteeEmail).isEqualTo("guest@example.com");
        }

        @Test
        @DisplayName("다른 이메일로는 게스트 공유가 조회되지 않는다")
        void findByFileIdAndGranteeEmailMissesADifferentEmail() {
            UUID fileIdValue = UUID.randomUUID();
            filePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            assertThat(filePersistenceAdapter.findByFileIdAndGranteeEmail(new FileId(fileIdValue), "someone-else@example.com"))
                    .isEmpty();
        }

        @Test
        @DisplayName("대기 중인 게스트 공유를 클레임하면 회원 공유로 저장되고 더 이상 대기 목록에 없다")
        void claimsAPendingGuestShareAndPersistsIt() {
            UUID fileIdValue = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            FileShare created = filePersistenceAdapter.saveFileShare(FileShare.createPending(
                    new FileShareFileId(fileIdValue), new FileShareOwnerId(UUID.randomUUID()),
                    new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER)));

            UUID token = created.getToken();
            created.claim(memberId);
            filePersistenceAdapter.saveFileShare(created);

            assertThat(filePersistenceAdapter.findPendingByGranteeEmail("guest@example.com")).isEmpty();
            assertThat(filePersistenceAdapter.findByFileIdAndSharedWithUserId(new FileId(fileIdValue), memberId))
                    .get().extracting(FileShare::getRole).isEqualTo(Role.VIEWER);
            // token survives the claim so the emailed /public link still resolves
            assertThat(filePersistenceAdapter.findByToken(token)).isPresent();
            // claim() clears granteeEmail (RevokeFileShareService.findGranteeShareByOwnIdentity
            // depends on this row no longer being reachable by its old email once claimed).
            assertThat(filePersistenceAdapter.findByFileIdAndGranteeEmail(new FileId(fileIdValue), "guest@example.com"))
                    .isEmpty();
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
        @DisplayName("초대 시각이 TTL 컷오프보다 이전이면 토큰으로 더 이상 조회되지 않는다")
        void expiredGuestInviteTokenIsNoLongerFound() {
            UUID fileIdValue = UUID.randomUUID();
            UUID token = UUID.randomUUID();
            springDataFileShareRepository.save(
                    new FileShareJpaEntity(fileIdValue, UUID.randomUUID(), null, Role.VIEWER, token, "guest@example.com"));

            // createdAt is auditing-managed (set to "now" on insert) — rather than fight that to
            // backdate a row, exercise the same query the adapter runs with a cutoff on each side
            // of "now", which is exactly what distinguishes an expired invite from a live one.
            assertThat(springDataFileShareRepository.findByTokenAndCreatedAtAfter(token, LocalDateTime.now().minusDays(1)))
                    .isPresent();
            assertThat(springDataFileShareRepository.findByTokenAndCreatedAtAfter(token, LocalDateTime.now().plusDays(1)))
                    .isEmpty();
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
    @DisplayName("버전을 id 여러 개로 한 번에 찾을 때")
    class WhenFindingVersionsByIds {

        @Test
        @DisplayName("요청한 id의 버전만 돌려준다")
        void returnsOnlyTheRequestedVersions() {
            UUID fileIdValue = UUID.randomUUID();
            UUID v1 = springDataFileVersionRepository.save(new FileVersionJpaEntity(fileIdValue, 10L, 1, "s3://b/v1")).getId();
            springDataFileVersionRepository.save(new FileVersionJpaEntity(fileIdValue, 20L, 2, "s3://b/v2"));

            assertThat(filePersistenceAdapter.findAllByIds(java.util.List.of(v1)))
                    .extracting(com.moduDrive.file.domain.model.FileVersion::getS3Path)
                    .containsExactly("s3://b/v1");
        }
    }

    @Nested
    @DisplayName("파일을 영구 삭제할 때")
    class WhenDeletingAFile {

        @Test
        @DisplayName("파일 행과 함께 그 파일의 버전 행도 모두 지워진다")
        void deletesFileVersionsToo() {
            FileJpaEntity saved = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "report.pdf", "/1", UUID.randomUUID(), FileStatus.DELETED, false));
            UUID fileIdValue = saved.getId();
            springDataFileVersionRepository.save(new FileVersionJpaEntity(fileIdValue, 10L, 1, "s3://b/v1"));
            springDataFileVersionRepository.save(new FileVersionJpaEntity(fileIdValue, 20L, 2, "s3://b/v2"));

            filePersistenceAdapter.deleteFile(new FileId(fileIdValue));

            assertThat(springDataFileVersionRepository.findByFileIdOrderByCreatedAtDesc(
                    fileIdValue, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
            assertThat(springDataFileRepository.findById(fileIdValue)).isEmpty();
        }

        @Test
        @DisplayName("파일 행과 함께 그 파일의 공유 행도 모두 지워진다 (고아 방지)")
        void deletesFileSharesToo() {
            FileJpaEntity saved = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "report.pdf", "/1", UUID.randomUUID(), FileStatus.DELETED, false));
            UUID fileIdValue = saved.getId();
            springDataFileShareRepository.save(
                    new FileShareJpaEntity(fileIdValue, UUID.randomUUID(), UUID.randomUUID(), Role.VIEWER));
            springDataFileShareRepository.save(
                    new FileShareJpaEntity(fileIdValue, UUID.randomUUID(), UUID.randomUUID(), Role.EDITOR));

            filePersistenceAdapter.deleteFile(new FileId(fileIdValue));

            assertThat(springDataFileShareRepository.findByFileId(fileIdValue)).isEmpty();
            assertThat(springDataFileRepository.findById(fileIdValue)).isEmpty();
        }

        @Test
        @DisplayName("파일 행과 함께 그 파일의 즐겨찾기 행도 모두 지워진다 (고아 방지)")
        void deletesFileFavoritesToo() {
            UUID fileIdValue = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "report.pdf", "/1", UUID.randomUUID(), FileStatus.DELETED, false)).getId();
            springDataFileFavoriteRepository.save(new FileFavoriteJpaEntity(UUID.randomUUID(), fileIdValue));
            springDataFileFavoriteRepository.save(new FileFavoriteJpaEntity(UUID.randomUUID(), fileIdValue));

            filePersistenceAdapter.deleteFile(new FileId(fileIdValue));

            assertThat(springDataFileFavoriteRepository.count()).isZero();
            assertThat(springDataFileRepository.findById(fileIdValue)).isEmpty();
        }

        @Test
        @DisplayName("purge는 파일 행을 tombstone(deleted_at)으로 남기고 버전/공유/즐겨찾기만 지운다")
        void purgeKeepsTheRowAsATombstoneAndClearsAttachments() {
            UUID fileIdValue = springDataFileRepository.save(new FileJpaEntity(
                    namespaceIdValue, "report.pdf", "/1", UUID.randomUUID(), FileStatus.TRASHED, false)).getId();
            springDataFileVersionRepository.save(new FileVersionJpaEntity(fileIdValue, 10L, 1, "s3://b/v1"));
            springDataFileShareRepository.save(
                    new FileShareJpaEntity(fileIdValue, UUID.randomUUID(), UUID.randomUUID(), Role.VIEWER));
            springDataFileFavoriteRepository.save(new FileFavoriteJpaEntity(UUID.randomUUID(), fileIdValue));
            UUID deletedBy = UUID.randomUUID();

            filePersistenceAdapter.purgeFile(new FileId(fileIdValue), deletedBy);
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

        @Test
        @DisplayName("여러 파일을 한 번에 기록하면 없던 행은 만들고 있던 행은 시각만 갱신한다")
        void recordsManyAtOnceUpsertingExistingRows() {
            UUID userId = UUID.randomUUID();
            UUID seenFileId = UUID.randomUUID();
            UUID newFileId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();
            filePersistenceAdapter.recordAccess(FileAccess.of(
                    new FileAccessUserId(userId), new FileAccessFileId(seenFileId), now.minusDays(1)));

            filePersistenceAdapter.recordAccesses(userId, List.of(seenFileId, newFileId), now);
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
            saveEntry("/1", "q.txt", false, FileStatus.PENDING);
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
}
