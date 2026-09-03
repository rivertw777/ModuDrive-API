package com.moduDrive.file.domain.model;

/**
 * How a directory listing is ordered. Directories always come before files regardless of this
 * value — it only picks the secondary key applied within each group. The set is a fixed
 * whitelist because each value has to map to an indexed, keyset-scrollable column.
 *
 * <p>Size sort is deliberately absent: {@code file_size} is null for every directory row (and for
 * a still-uploading file), and a keyset scroll degenerates on a null key — pages then drop and
 * repeat rows. Re-add it together with a non-null {@code file_size} (0 for directories) + a backfill.
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
