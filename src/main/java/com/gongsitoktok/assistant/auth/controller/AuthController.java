/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/auth/controller/AuthController.java
 */
package com.gongsitoktok.assistant.auth.controller;

import com.gongsitoktok.assistant.auth.dto.LoginRequest;
import com.gongsitoktok.assistant.auth.dto.SignupRequest;
import com.gongsitoktok.assistant.auth.dto.TokenResponse;
import com.gongsitoktok.assistant.auth.jwt.JwtTokenProvider;
import com.gongsitoktok.assistant.auth.service.AuthFacade.IssuedTokens;
import com.gongsitoktok.assistant.auth.service.AuthService;
import com.gongsitoktok.assistant.auth.service.RefreshTokenService;
import com.gongsitoktok.assistant.auth.service.RefreshTokenService.RotateResult;
import com.gongsitoktok.assistant.global.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 인증 컨트롤러 — 로컬 회원가입/로그인/리프레시/로그아웃 (제작요청 v6 §4-1, §4-2, §4-2c, §4-3).
 *
 * <h3>Refresh Token 쿠키 정책</h3>
 * <ul>
 *     <li>{@code HttpOnly · Secure · SameSite=Strict} 고정</li>
 *     <li>{@code Path=/api/v1/auth} — refresh 흐름에만 쿠키 동행. 챗봇·기업 API 호출에는 따라가지 않음.</li>
 *     <li>{@code Max-Age=1209600} (14일, Refresh TTL 과 일치)</li>
 *     <li>로그아웃 시 동일 속성 + {@code Max-Age=0} 으로 명시 제거</li>
 * </ul>
 *
 * <h3>OAuth2 로그인은 본 컨트롤러를 거치지 않음</h3>
 * <p>OAuth 진입은 Spring Security 가 자동 노출하는 {@code /oauth2/authorization/{provider}} 이며, 콜백 처리는
 * {@code OAuth2LoginSuccessHandler} / {@code OAuth2LoginFailureHandler} 가 담당한다 (§4-2b · §스텝 H-3).</p>
 */
@Tag(name = "Auth", description = "회원가입 · 로그인 · 토큰 회전 · 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${refresh-token.cookie-name}")
    private String refreshCookieName;

    @Value("${refresh-token.cookie-path}")
    private String refreshCookiePath;

    @Value("${refresh-token.validity-seconds}")
    private long refreshValiditySeconds;

    @Operation(summary = "회원가입 (로컬)", description = "userId 정규식과 비밀번호 정책을 서버에서 강제 검증한다.")
    @ApiResponse(responseCode = "201", description = "가입 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_USER_ID_FORMAT / PASSWORD_POLICY_VIOLATION")
    @ApiResponse(responseCode = "409", description = "USER_ID_DUPLICATED")
    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@Valid @RequestBody SignupRequest req) {
        String userId = authService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", userId));
    }

    @Operation(summary = "로그인 (로컬)",
            description = "Access Token 은 응답 body, Refresh Token 은 httpOnly 쿠키로 동시 발급.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN (id/pw 불일치)")
    @ApiResponse(responseCode = "403", description = "INVALID_LOGIN_METHOD / USER_WITHDRAWN")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest req,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        IssuedTokens tokens = authService.login(req, userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshTokenRaw(), refreshValiditySeconds).toString())
                .body(TokenResponse.bearer(tokens.accessToken(), tokens.accessExpiresInSeconds()));
    }

    @Operation(summary = "Access Token 갱신",
            description = "Refresh Token 회전 + 도난 재사용 탐지. Refresh Token 쿠키를 갱신한다.")
    @ApiResponse(responseCode = "200", description = "갱신 성공")
    @ApiResponse(responseCode = "401",
            description = "INVALID_REFRESH_TOKEN / REFRESH_TOKEN_EXPIRED / REFRESH_TOKEN_REUSED")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "${refresh-token.cookie-name}", required = false) String rawRefreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        RotateResult rotated = refreshTokenService.rotate(rawRefreshToken, userAgent);
        // RotateResult 는 트랜잭션 안에서 미리 추출한 primitive 만 보유. lazy 접근 사고 차단.
        String newAccess = jwtTokenProvider.issueAccessToken(
                rotated.userSeq(),
                rotated.userId(),
                rotated.oauthService());
        long accessExp = parseAccessExp(newAccess);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotated.newRawToken(), refreshValiditySeconds).toString())
                .body(TokenResponse.bearer(newAccess, accessExp));
    }

    @Operation(summary = "로그아웃",
            description = "현재 JWT jti 블랙리스트 + Refresh Token 단건 revoke + 쿠키 제거.")
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request,
            @CookieValue(name = "${refresh-token.cookie-name}", required = false) String rawRefreshToken
    ) {
        // 1. 현재 토큰 jti 를 블랙리스트로
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length());
            Claims claims = jwtTokenProvider.parseAndValidate(token);
            jwtTokenProvider.blacklist(claims.getId(), jwtTokenProvider.extractExpiryEpochMillis(claims));
            jwtTokenProvider.invalidateClaimsCache(token);
        }

        // 2. Refresh Token 단건 revoke
        refreshTokenService.revoke(rawRefreshToken);

        // 3. 쿠키 즉시 제거 (발급 시점과 동일 속성으로)
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString())
                .build();
    }

    /**
     * Refresh Token 쿠키 빌더 — 발급/갱신/제거에 동일 속성을 적용해 브라우저가 정확히 매칭하도록 보장.
     */
    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(refreshCookieName, value == null ? "" : value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(refreshCookiePath)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    /** {@code TokenResponse.expiresIn} 채우기 위해 새 토큰의 exp 클레임을 즉시 파싱. */
    private long parseAccessExp(String accessToken) {
        Claims c = jwtTokenProvider.parseAndValidate(accessToken);
        return Math.max(0L, (jwtTokenProvider.extractExpiryEpochMillis(c) - System.currentTimeMillis()) / 1000L);
    }
}
