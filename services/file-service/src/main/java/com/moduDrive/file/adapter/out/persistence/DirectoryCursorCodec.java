package com.moduDrive.file.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.file.domain.model.DirectorySort;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A keyset {@link ScrollPosition} as an opaque URL-safe token. Each key is stored as
 * {@code [typeTag, stringValue]}: a bare JSON value would lose the type Hibernate needs to rebuild
 * the keyset predicate (a UUID, a {@code LocalDateTime}).
 *
 * <p>The token carries the {@link DirectorySort} it was minted under: replayed under another sort
 * it would fail mid-query with a 500, so {@link #decode} rejects it as bad input.
 */
final class DirectoryCursorCodec {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, List<String>>> SHAPE = new TypeReference<>() {};
    private static final String SORT_KEY = "_sort";
    private static final int MAX_CURSOR_LENGTH = 512;

    private DirectoryCursorCodec() {
    }

    static ScrollPosition decode(String cursor, DirectorySort sort) {
        if (cursor == null || cursor.isBlank()) {
            return ScrollPosition.keyset();
        }
        if (cursor.length() > MAX_CURSOR_LENGTH) {
            throw new IllegalArgumentException("invalid directory cursor");
        }
        Map<String, List<String>> raw;
        try {
            raw = JSON.readValue(Base64.getUrlDecoder().decode(cursor), SHAPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid directory cursor", e);
        }
        List<String> taggedSort = raw.remove(SORT_KEY);
        if (taggedSort == null || taggedSort.size() != 2 || !sort.name().equals(taggedSort.get(1))) {
            throw new IllegalArgumentException("directory cursor does not match the requested sort");
        }
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("invalid directory cursor");
        }
        Map<String, Object> keys = new LinkedHashMap<>();
        try {
            raw.forEach((key, tagged) -> keys.put(key, revive(tagged.get(0), tagged.get(1))));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid directory cursor", e);
        }
        return ScrollPosition.forward(keys);
    }

    static String encode(ScrollPosition position, DirectorySort sort) {
        if (!(position instanceof KeysetScrollPosition keyset)) {
            throw new IllegalStateException("expected a keyset scroll position, got " + position);
        }
        Map<String, List<String>> raw = new LinkedHashMap<>();
        keyset.getKeys().forEach((key, value) -> raw.put(key, tag(value)));
        raw.put(SORT_KEY, List.of("string", sort.name()));
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(JSON.writeValueAsBytes(raw));
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode directory cursor", e);
        }
    }

    private static List<String> tag(Object value) {
        return switch (value) {
            case null -> List.of("null", "");
            case Boolean b -> List.of("bool", b.toString());
            case UUID u -> List.of("uuid", u.toString());
            case LocalDateTime dt -> List.of("datetime", dt.toString());
            default -> List.of("string", value.toString());
        };
    }

    private static Object revive(String type, String value) {
        return switch (type) {
            case "null" -> null;
            case "bool" -> Boolean.parseBoolean(value);
            case "uuid" -> UUID.fromString(value);
            case "datetime" -> LocalDateTime.parse(value);
            default -> value;
        };
    }
}
