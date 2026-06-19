/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/auth/dto/SignupRequest.java
 */
package com.gongsitoktok.assistant.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로컬 회원가입 요청 바디.
 *
 * <h3>검증 흐름</h3>
 * <ul>
 *     <li>{@link NotBlank} / {@link Size} — 형식적 1차 방어 (빈 값·길이 폭주 차단).</li>
 *     <li><b>userId 정규식</b> · <b>password 정책</b> — 서비스 레이어({@code AuthService.signup}) 에서 명시 분기,
 *         각각 {@code INVALID_USER_ID_FORMAT} / {@code PASSWORD_POLICY_VIOLATION} 코드로 throw.</li>
 *     <li>비밀번호 재입력 확인({@code passwordCheck}) 은 프론트엔드 책임. 서버는 단일 {@link #password} 만 수신.</li>
 * </ul>
 *
 * @param userId   영문 소문자/숫자만 사용, 4~20자 (혼합·단독 모두 허용 — 서비스에서 정규식 검증)
 * @param password 8자 이상 + 대/소문자·숫자·특수문자 각 1개 이상 (서비스에서 정책 검증)
 * @param nickname 표시명
 */
@Schema(description = "로컬 회원가입 요청")
public record SignupRequest(
        @Schema(description = "로그인 ID — 영문 소문자/숫자만 사용 가능, 4~20자 (혼합·단독 모두 허용)", example = "alice01")
        @NotBlank(message = "userId 는 필수입니다.")
        @Size(min = 4, max = 20, message = "userId 는 4~20자여야 합니다.")
        String userId,

        @Schema(description = "비밀번호 (8자 이상, 대/소문자·숫자·특수문자 포함)", example = "Passw0rd!")
        @NotBlank(message = "password 는 필수입니다.")
        @Size(min = 8, max = 100, message = "password 는 8~100자여야 합니다.")
        String password,

        @Schema(description = "표시명", example = "앨리스")
        @NotBlank(message = "nickname 은 필수입니다.")
        @Size(max = 50, message = "nickname 은 50자 이하여야 합니다.")
        String nickname
) {
}
