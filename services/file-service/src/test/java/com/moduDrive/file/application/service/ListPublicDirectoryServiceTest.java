package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListPublicDirectoryCommand;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ListPublicDirectoryServiceTest {

    @Mock private PublicFileResolver publicFileResolver;
    @InjectMocks private ListPublicDirectoryService listPublicDirectoryService;

    private final String fileId = UUID.randomUUID().toString();
    private final String key = UUID.randomUUID().toString();

    @Test
    void delegatesToTheResolverWithFileIdAndKey() {
        File child = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                new FileName("a.txt"), new FilePath("/shared"), new FileOwnerId(UUID.randomUUID()),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
        given(publicFileResolver.resolveChildren(fileId, key)).willReturn(List.of(child));

        List<File> result = listPublicDirectoryService.listPublicDirectory(
                new ListPublicDirectoryCommand(fileId, key));

        assertThat(result).containsExactly(child);
    }

    @Test
    void propagatesFileNotFoundFromTheResolver() {
        willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                .given(publicFileResolver).resolveChildren(fileId, key);

        Throwable thrown = catchThrowable(() -> listPublicDirectoryService.listPublicDirectory(
                new ListPublicDirectoryCommand(fileId, key)));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
    }
}
