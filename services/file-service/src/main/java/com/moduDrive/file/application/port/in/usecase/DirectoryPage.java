package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.domain.model.File;

import java.util.List;

/**
 * One page of a directory listing. {@code nextCursor} is an opaque token to pass back as the
 * {@code cursor} param for the following page — null when {@code hasNext} is false.
 */
public record DirectoryPage(List<File> content, String nextCursor, boolean hasNext) {
}
