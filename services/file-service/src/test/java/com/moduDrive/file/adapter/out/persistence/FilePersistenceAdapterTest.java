package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({FilePersistenceAdapter.class, FileMapper.class})
class FilePersistenceAdapterTest {

    @Autowired
    private FilePersistenceAdapter filePersistenceAdapter;
    @Autowired
    private SpringDataFileRepository springDataFileRepository;

    private final UUID namespaceIdValue = UUID.randomUUID();
    private final NamespaceId namespaceId = new NamespaceId(namespaceIdValue);

    private void save(String path, String name) {
        springDataFileRepository.save(new FileJpaEntity(
                namespaceIdValue, name, path, UUID.randomUUID(), FileStatus.UPLOADED, false));
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
}
