package com.moduDrive.auth.adapter.out.client.member;

import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.api.dto.member.AuthenticateMemberResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.FeignFallbackUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "member-service")
interface MemberClient {

    @PostMapping("/api/v1/member/authenticate")
    @CircuitBreaker(name = "memberServiceCircuitBreaker", fallbackMethod = "authenticateMemberFallback")
    @Retry(name = "memberServiceRetry")
    ApiResponse<AuthenticateMemberResponse> authenticateMember(AuthenticateMemberRequest authenticateMemberRequest);

    default ApiResponse<AuthenticateMemberResponse> authenticateMemberFallback(AuthenticateMemberRequest authenticateMemberRequest, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }

    @GetMapping("/api/v1/member/{memberId}/status")
    @CircuitBreaker(name = "memberServiceCircuitBreaker", fallbackMethod = "fetchMemberStatusFallback")
    @Retry(name = "memberServiceRetry")
    ApiResponse<AuthenticateMemberResponse> fetchMemberStatus(@PathVariable("memberId") String memberId);

    default ApiResponse<AuthenticateMemberResponse> fetchMemberStatusFallback(String memberId, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }

}
