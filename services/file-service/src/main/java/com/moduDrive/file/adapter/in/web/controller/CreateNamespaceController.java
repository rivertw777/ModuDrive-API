package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.api.dto.namespace.CreateNamespaceRequest;
import com.moduDrive.file.adapter.in.web.dto.NamespaceResponse;
import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.application.port.in.usecase.CreateNamespaceUseCase;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class CreateNamespaceController {

    private final CreateNamespaceUseCase createNamespaceUseCase;

    @PostMapping("/api/v1/namespaces")
    public ApiResponse<NamespaceResponse> createNamespace(@Valid @RequestBody CreateNamespaceRequest request) {
        Namespace namespace = createNamespaceUseCase.createNamespace(
                new CreateNamespaceCommand(new NamespaceUserId(request.userId()))
        );
        return ApiResponse.success(NamespaceResponse.from(namespace));
    }
}
