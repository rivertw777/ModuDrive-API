package com.moduDrive.file.domain.model;

/**
 * How a directory listing is ordered within each group; directories always come before files.
 * Each value must map to a non-null, keyset-scrollable column.
 *
 * <p>No size sort: {@code file_size} can be null, and a keyset scroll on a null key drops and
 * repeats rows.
 */
public enum DirectorySort {

    NAME_ASC,
    NAME_DESC,
    MODIFIED_ASC,
    MODIFIED_DESC;

    /** Parses the {@code sort}/{@code direction} request params; unknown field falls back to name. */
    public static DirectorySort from(String field, String direction) {
        boolean desc = "desc".equalsIgnoreCase(direction);
        return switch (field == null ? "name" : field.toLowerCase()) {
            case "date", "modified", "updatedat" -> desc ? MODIFIED_DESC : MODIFIED_ASC;
            default -> desc ? NAME_DESC : NAME_ASC;
        };
    }
}
