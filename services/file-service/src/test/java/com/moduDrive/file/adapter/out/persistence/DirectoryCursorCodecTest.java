package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.DirectorySort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectoryCursorCodecTest {

    @Test
    void roundTripsEveryKeysetValueTypeWithItsType() {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("directory", true);
        keys.put("updatedAt", LocalDateTime.of(2026, 9, 3, 12, 30, 45));
        keys.put("id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        KeysetScrollPosition original = ScrollPosition.forward(keys);

        String cursor = DirectoryCursorCodec.encode(original, DirectorySort.MODIFIED_ASC);
        ScrollPosition decoded = DirectoryCursorCodec.decode(cursor, DirectorySort.MODIFIED_ASC);

        assertThat(decoded).isInstanceOf(KeysetScrollPosition.class);
        assertThat(((KeysetScrollPosition) decoded).getKeys())
                .containsExactlyInAnyOrderEntriesOf(keys);
    }

    @Test
    void aBlankCursorIsTheInitialKeysetPosition() {
        assertThat(DirectoryCursorCodec.decode(null, DirectorySort.NAME_ASC)).isEqualTo(ScrollPosition.keyset());
        assertThat(DirectoryCursorCodec.decode("  ", DirectorySort.NAME_ASC)).isEqualTo(ScrollPosition.keyset());
    }

    @Test
    void aTamperedCursorIsRejectedAsBadInput() {
        assertThatThrownBy(() -> DirectoryCursorCodec.decode("not-a-real-cursor", DirectorySort.NAME_ASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCursorReplayedUnderADifferentSortIsRejectedAsBadInput() {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("name", "report.pdf");
        keys.put("id", UUID.randomUUID());
        String nameCursor = DirectoryCursorCodec.encode(ScrollPosition.forward(keys), DirectorySort.NAME_ASC);

        assertThatThrownBy(() -> DirectoryCursorCodec.decode(nameCursor, DirectorySort.MODIFIED_DESC))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
