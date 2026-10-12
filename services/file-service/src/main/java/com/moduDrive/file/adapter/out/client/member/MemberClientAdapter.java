package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.exception.FileExceptionCase;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class MemberClientAdapter implements FindMemberByEmailPort, FindMemberByIdPort {

    private final MemberClient memberClient;

    @Override
    public Optional<UUID> findMemberIdByEmail(String email) {
        try {
            val response = memberClient.findMemberByEmail(email).getData();
            return Optional.of(UUID.fromString(response.id()));
        } catch (FeignException e) {
            // FeignFallbackUtils rethrows a 4xx untouched (and it doesn't trip the breaker). A
            // 400/404 means "not a member": a guest invite, not a failure.
            if (e.status() == 400 || e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public MemberSummary findMemberById(UUID memberId) {
        try {
            val response = memberClient.findMemberById(memberId.toString()).getData();
            return new MemberSummary(response.name(), response.email());
        } catch (FeignException e) {
            if (e.status() == 400 || e.status() == 404) {
                throw new BusinessException(FileExceptionCase.SHARE_TARGET_NOT_FOUND);
            }
            throw e;
        }
    }
}
