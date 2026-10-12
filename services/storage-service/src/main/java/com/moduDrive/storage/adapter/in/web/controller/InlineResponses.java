package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.storage.application.port.in.usecase.RangeContent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The inline (preview) responses both view endpoints send — see {@link DownloadFileController#viewFile}
 * and {@link PublicDownloadFileController#viewPublicFile}. */
final class InlineResponses {

    private static final Pattern SINGLE_RANGE = Pattern.compile("bytes=(\\d{1,18})-(\\d{0,18})");

    private InlineResponses() {
    }

    /** {@code bytes=start-[end]}, the only form a media element sends. Anything else — suffix,
     * multi-range (answering only its first slice would report success while dropping the rest),
     * malformed, or end before start — is treated as no Range and gets the whole body, since a
     * botched seek shouldn't break plain playback. */
    record RequestedRange(long start, Long end) {

        static RequestedRange parse(String header) {
            if (header == null) {
                return null;
            }
            Matcher m = SINGLE_RANGE.matcher(header.trim());
            if (!m.matches()) {
                return null;
            }
            long start = Long.parseLong(m.group(1));
            Long end = m.group(2).isEmpty() ? null : Long.parseLong(m.group(2));
            return end != null && end < start ? null : new RequestedRange(start, end);
        }
    }

    static ResponseEntity<byte[]> whole(byte[] data, String fileName) {
        return headers(ResponseEntity.ok(), fileName).body(data);
    }

    static ResponseEntity<byte[]> partial(RangeContent content, String fileName) {
        long end = content.start() + content.data().length - 1;
        return headers(ResponseEntity.status(HttpStatus.PARTIAL_CONTENT), fileName)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + content.start() + "-" + end + "/" + content.totalLength())
                .body(content.data());
    }

    private static ResponseEntity.BodyBuilder headers(ResponseEntity.BodyBuilder builder, String fileName) {
        return builder
                .contentType(FileMimeTypes.contentType(fileName))
                .header(HttpHeaders.CONTENT_DISPOSITION, FileMimeTypes.inlineDisposition(fileName))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff");
    }
}
