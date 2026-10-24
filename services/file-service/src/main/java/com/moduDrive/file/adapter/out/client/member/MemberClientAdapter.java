package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
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
    public MemberSummary findMemberByIdOrUnknown(UUID memberId) {
        try {
            val response = memberClient.findMemberById(memberId.toString()).getData();
            return response != null ? new MemberSummary(response.name(), response.email()) : UNKNOWN_MEMBER;
        } catch (FeignException e) {
            // FeignFallbackUtils rethrows a 4xx untouched: 400/404 is a member that's gone, not a failure.
            if (e.status() != 400 && e.status() != 404) {
                log.warn("Failed to resolve memberId={}, showing as unknown: HTTP {}", memberId, e.status());
            }
            return UNKNOWN_MEMBER;
        } catch (BusinessException e) {
            // member-service down or its circuit open (FeignFallbackUtils): repeats per lookup, so no stack.
            log.warn("Failed to resolve memberId={}, showing as unknown: {}", memberId, e.getExceptionCase());
            return UNKNOWN_MEMBER;
        } catch (RuntimeException e) {
            log.warn("Failed to resolve memberId={}, showing as unknown", memberId, e);
            return UNKNOWN_MEMBER;
        }
    }
}
