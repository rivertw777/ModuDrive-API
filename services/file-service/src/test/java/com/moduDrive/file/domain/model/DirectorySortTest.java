package com.moduDrive.file.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DirectorySortTest {

    @ParameterizedTest
    @CsvSource({
            "name, asc, NAME_ASC",
            "name, desc, NAME_DESC",
            "date, asc, MODIFIED_ASC",
            "modified, desc, MODIFIED_DESC",
            "updatedAt, DESC, MODIFIED_DESC",
    })
    void parsesFieldAndDirectionParams(String field, String direction, DirectorySort expected) {
        assertThat(DirectorySort.from(field, direction)).isEqualTo(expected);
    }

    @Test
    void unknownFieldFallsBackToNameAndUnknownDirectionToAscending() {
        assertThat(DirectorySort.from("whatever", "sideways")).isEqualTo(DirectorySort.NAME_ASC);
        assertThat(DirectorySort.from(null, null)).isEqualTo(DirectorySort.NAME_ASC);
    }

    @Test
    void sizeSortIsNotSupportedAndFallsBackToName() {
        assertThat(DirectorySort.from("size", "asc")).isEqualTo(DirectorySort.NAME_ASC);
        assertThat(DirectorySort.from("size", "desc")).isEqualTo(DirectorySort.NAME_DESC);
    }
}
