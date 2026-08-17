package com.moduDrive.member.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberExceptionCase implements ExceptionCase {

    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 존재하는 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "회원 정보를 찾을 수 없습니다."),
    PASSWORD_NOT_MATCHED(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 유효하지 않거나 만료되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}