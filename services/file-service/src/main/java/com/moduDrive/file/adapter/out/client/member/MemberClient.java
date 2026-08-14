package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.FeignFallbackUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

    // member-service's /find reads its target purely off X_USER_ID with no self/caller check
    // (see FindMemberController) — reused here service-to-service for id-based lookup instead of
    // adding a new member-service endpoint (see #156). MemberResponse ignores the extra
    // isValid field the response body carries.
    @GetMapping("/api/v1/member/find")
    @CircuitBreaker(name = "memberServiceCircuitBreaker", fallbackMethod = "findMemberByIdFallback")
    @Retry(name = "memberServiceRetry")
    ApiResponse<MemberResponse> findMemberById(@RequestHeader("X_USER_ID") String memberId);

    default ApiResponse<MemberResponse> findMemberByIdFallback(String memberId, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }
}
