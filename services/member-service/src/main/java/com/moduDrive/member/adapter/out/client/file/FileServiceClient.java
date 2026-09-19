package com.moduDrive.member.adapter.out.client.file;

import com.moduDrive.common.api.dto.namespace.CreateNamespaceRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "file-service", url = "${clients.file-service.url}")
interface FileServiceClient {

    @PostMapping("/internal/v1/namespaces")
    void createNamespace(@RequestBody CreateNamespaceRequest request);
}
