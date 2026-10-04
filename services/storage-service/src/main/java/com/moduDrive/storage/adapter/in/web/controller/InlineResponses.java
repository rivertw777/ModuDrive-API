package com.moduDrive.storage.adapter.in.web.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

/** The inline (preview) response both view endpoints send — see {@link DownloadFileController#viewFile}
 * and {@link PublicDownloadFileController#viewPublicFile}. */
final class InlineResponses {

    private InlineResponses() {
    }

    /** No Range header: the full body, exactly as before. With one: the requested byte slice as
     * a {@code 206} — the file is already fully assembled in memory by the time this runs (see
     * {@link com.moduDrive.storage.application.service.DownloadFileService}), so slicing the
     * array is all a "region" means here; there's no lazily-read {@link org.springframework.core.io.Resource}
     * underneath to justify {@code ResourceRegion}/{@code ResourceRegionHttpMessageConverter}.
     * This only saves wire bytes and lets the browser seek, not server-side memory. A malformed
     * Range is treated as no Range rather than rejected, since a botched seek shouldn't break
     * plain playback. */
    static ResponseEntity<byte[]> inline(byte[] data, String fileName, String rangeHeader) {
        MediaType contentType = FileMimeTypes.contentType(fileName);
        String disposition = FileMimeTypes.inlineDisposition(fileName);
        HttpRange range = parseFirstRange(rangeHeader, data.length);

        if (range == null) {
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(data);
        }

        long start = range.getRangeStart(data.length);
        long end = range.getRangeEnd(data.length);
        byte[] slice = Arrays.copyOfRange(data, (int) start, (int) end + 1);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + data.length)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(slice);
    }

    private static HttpRange parseFirstRange(String rangeHeader, int contentLength) {
        if (rangeHeader == null) {
            return null;
        }
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            // A multi-range request answered with only the first slice would report 206 success
            // while silently dropping the rest — treating it as no-range returns the whole body
            // instead, which the client can always re-seek against.
            if (ranges.size() != 1) {
                return null;
            }
            HttpRange range = ranges.get(0);
            // A start beyond the actual content is a client bug (e.g. stale Range from a
            // previous, different file), not a seek to honor as partial content.
            return range.getRangeStart(contentLength) < contentLength ? range : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
