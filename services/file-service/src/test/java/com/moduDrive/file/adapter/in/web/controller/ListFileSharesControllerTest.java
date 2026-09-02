package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListFileSharesController.class)
@Import(GlobalExceptionHandler.class)
class ListFileSharesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListFileSharesUseCase listFileSharesUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("소유자가 공유 목록을 조회할 때")
    class WhenOwnerLists {

        @Test
        void returnsOwnerAndShares() throws Exception {
            File file = File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1"),
                    new FileOwnerId(UUID.fromString(OWNER_ID)), null, null,
                    FileStatus.UPLOADED, new FileIsDirectory(false));
            UUID sharedWithUserId = UUID.randomUUID();
            FileShare share = FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(FILE_ID),
                    new FileShareOwnerId(UUID.fromString(OWNER_ID)),
                    new FileShareSharedWithUserId(sharedWithUserId), new FileShareRole(Role.EDITOR));
            given(listFileSharesUseCase.listFileShares(any(ListFileSharesCommand.class)))
                    .willReturn(new FileSharesView(file, List.of(share), List.of(), List.of(),
                            Map.of(sharedWithUserId, new MemberSummary("river", "river@modudrive.com"))));

            mockMvc.perform(get("/api/v1/files/{fileId}/shares", FILE_ID)
                            .header("X_USER_ID", OWNER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ownerId").value(OWNER_ID))
                    .andExpect(jsonPath("$.data.scope").value("RESTRICTED"))
                    .andExpect(jsonPath("$.data.shares[0].role").value("EDITOR"))
                    .andExpect(jsonPath("$.data.shares[0].sharedWithEmail").value("river@modudrive.com"))
                    .andExpect(jsonPath("$.data.shares[0].sharedWithName").value("river"));
        }
    }

    @Nested
    @DisplayName("호출자가 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void returnsForbidden() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(listFileSharesUseCase).listFileShares(any(ListFileSharesCommand.class));

            mockMvc.perform(get("/api/v1/files/{fileId}/shares", FILE_ID)
                            .header("X_USER_ID", OWNER_ID))
                    .andExpect(status().isForbidden());
        }
    }
}
