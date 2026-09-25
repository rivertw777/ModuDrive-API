package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.FeignFallbackUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "member-service", url = "${clients.member-service.url}")
interface MemberClient {

    // Internal routes (#441): the internal-token interceptor attaches the secret on /internal/**.
    @GetMapping("/internal/v1/member/by-email")
    @CircuitBreaker(name = "memberServiceCircuitBreaker", fallbackMethod = "findMemberByEmailFallback")
    @Retry(name = "memberServiceRetry")
    ApiResponse<MemberResponse> findMemberByEmail(@RequestParam("email") String email);

    default ApiResponse<MemberResponse> findMemberByEmailFallback(String email, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }

    @GetMapping("/internal/v1/member/{memberId}")
    @CircuitBreaker(name = "memberServiceCircuitBreaker", fallbackMethod = "findMemberByIdFallback")
    @Retry(name = "memberServiceRetry")
    ApiResponse<MemberResponse> findMemberById(@PathVariable("memberId") String memberId);

    default ApiResponse<MemberResponse> findMemberByIdFallback(String memberId, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }
}
