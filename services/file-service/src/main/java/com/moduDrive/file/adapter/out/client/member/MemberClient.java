package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.FeignFallbackUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "member-service")
interface MemberClient {

    @GetMapping("/api/v1/member/find-by-email")
    @CircuitBreaker(name = "memberServiceCircuitBreaker", fallbackMethod = "findMemberByEmailFallback")
    @Retry(name = "memberServiceRetry")
    ApiResponse<MemberResponse> findMemberByEmail(@RequestParam("email") String email);

    default ApiResponse<MemberResponse> findMemberByEmailFallback(String email, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }
}
