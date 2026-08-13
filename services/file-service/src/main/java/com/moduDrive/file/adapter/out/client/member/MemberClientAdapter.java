package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.exception.FileExceptionCase;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class MemberClientAdapter implements FindMemberByEmailPort, FindMemberByIdPort {

    private final MemberClient memberClient;

    @Override
    public UUID findMemberIdByEmail(String email) {
        try {
            val response = memberClient.findMemberByEmail(email).getData();
            return UUID.fromString(response.id());
        } catch (FeignException e) {
            // member-service answers an unknown/invalid email with a 4xx business rejection, which
            // FeignFallbackUtils deliberately rethrows untouched (only 5xx/timeouts become
            // SERVICE_UNAVAILABLE, and only those are in resilience4j's record-exceptions, so this
            // path can't trip the breaker). Translate it here so the caller sees a clean 404
            // instead of the raw FeignException leaking out as a relayed member-service error.
            if (e.status() == 400 || e.status() == 404) {
                throw new BusinessException(FileExceptionCase.SHARE_TARGET_NOT_FOUND);
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
