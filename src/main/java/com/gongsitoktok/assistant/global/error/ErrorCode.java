/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/global/error/ErrorCode.java
 */
package com.gongsitoktok.assistant.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 전역 에러 코드 enum.
 *
 * <p>제작요청 v6 §5-D 에 정의된 코드 집합을 그대로 구현. 클라이언트는 {@link #name()} (문자열) 으로 분기한다.</p>
 *
 * <h3>HTTP 상태 매핑 원칙</h3>
 * <ul>
 *     <li>4xx — 클라이언트가 고쳐 보낼 수 있는 오류 (검증·인증·자원 미존재 등)</li>
 *     <li>5xx — 서버측 사고 (FastAPI 사고는 502, 타임아웃은 504)</li>
 *     <li>410 — {@code CHAT_ROOM_EXPIRED} (자원이 시간 기준으로 소멸)</li>
 * </ul>
 *
 * <h3>Reserved 코드</h3>
 * <p>{@link #INTERNAL_API_KEY_MISMATCH} 는 v6 미사용 — later.md #1 의 운영자 endpoint 보호 옵션 4 채택 시 활성.</p>
 */
@Getter
public enum ErrorCode {

    // ===== 챗봇 도메인 =====
    CHAT_ROOM_EXPIRED(HttpStatus.GONE, "30분 이상 활동이 없어 만료된 대화방입니다. 새 세션을 시작해 주세요."),
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않거나 접근 권한이 없는 대화방입니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "등록되지 않은 기업입니다."),

    // ===== 인증·인가 =====
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_LOGIN_METHOD(HttpStatus.FORBIDDEN, "해당 계정의 로그인 방식이 아닙니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴 처리된 계정입니다."),
    OAUTH_USER_PASSWORD_CHANGE_DENIED(HttpStatus.FORBIDDEN, "OAuth 가입 사용자는 비밀번호를 변경할 수 없습니다."),
    OAUTH_FAILED(HttpStatus.BAD_REQUEST, "소셜 로그인에 실패했습니다."),
    OAUTH_UNLINK_FAILED(HttpStatus.BAD_GATEWAY, "소셜 계정 연동 해제에 실패했습니다. 잠시 후 다시 시도해 주세요."),

    // ===== 회원가입·정책 =====
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "비밀번호 정책(8자 이상, 대/소문자·숫자·특수문자 각 1개 이상)을 만족하지 않습니다."),
    INVALID_USER_ID_FORMAT(HttpStatus.BAD_REQUEST, "아이디는 영문 소문자와 숫자만 사용해 4~20자로 입력해 주세요."),
    USER_ID_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),

    // ===== Refresh Token =====
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token 입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh Token 이 만료되었습니다. 다시 로그인해 주세요."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용된 Refresh Token 입니다. 보안을 위해 모든 세션이 종료되었습니다."),

    // ===== FastAPI 업스트림 =====
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답 대기 시간이 초과되었습니다."),
    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "AI 서버 처리 중 오류가 발생했습니다."),
    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서버에 일시적으로 연결할 수 없습니다."),
    UPSTREAM_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AI 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    CONTEXT_TOO_LONG(HttpStatus.PAYLOAD_TOO_LARGE, "대화 길이가 너무 길어 처리할 수 없습니다."),
    CONTENT_FILTERED(HttpStatus.UNPROCESSABLE_ENTITY, "정책에 의해 답변이 차단되었습니다."),
    ANSWER_NOT_TRUSTWORTHY(HttpStatus.BAD_GATEWAY, "검증된 답변을 만들지 못했습니다. 다시 시도해 주세요."),

    // ===== 일반 =====
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),
    INTERNAL_BUG(HttpStatus.INTERNAL_SERVER_ERROR, "내부 처리 중 오류가 발생했습니다."),

    // ===== Reserved (v6 미사용) =====
    INTERNAL_API_KEY_MISMATCH(HttpStatus.UNAUTHORIZED, "내부 API 키가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
