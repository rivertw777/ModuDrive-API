package com.moduDrive.member.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberExceptionCase implements ExceptionCase {

    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 가입중인 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "회원 정보를 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    // 410 so WEB can tell "type it again" from "this code is gone, ask for a new one" without parsing the message.
    VERIFICATION_CODE_ENDED(HttpStatus.GONE, "인증 코드가 만료되었거나 입력 횟수를 넘었습니다. 코드를 다시 받아주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),
    TOO_MANY_VERIFICATION_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 요청해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}