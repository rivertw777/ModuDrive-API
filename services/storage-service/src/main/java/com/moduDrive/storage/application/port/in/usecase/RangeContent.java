package com.moduDrive.storage.application.port.in.usecase;

/** One slice of a file for a {@code Range} request: {@code data} is bytes
 * {@code [start, start + data.length)} of a file {@code totalLength} long. */
public record RangeContent(byte[] data, long start, long totalLength) {
}
