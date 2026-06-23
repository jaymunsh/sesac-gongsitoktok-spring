/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/auth/oauth/OAuth2LoginSuccessHandler.java
 */
package com.gongsitoktok.assistant.auth.oauth;

import com.gongsitoktok.assistant.auth.service.AuthFacade;
import com.gongsitoktok.assistant.auth.service.AuthFacade.IssuedTokens;
import com.gongsitoktok.assistant.user.entity.User;
import com.gongsitoktok.assistant.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;

/**
 * OAuth2 로그인 성공 핸들러 (제작요청 v6 §4-2b · §스텝 H-3).
 *
 * <h3>동작</h3>
 * <ol>
 *     <li>{@link OAuth2UserPrincipal} 캐스팅 — {@code CustomOAuth2UserService} 가 만든 principal.</li>
 *     <li>{@code (oauthService, providerId)} 로 {@code tb_user} upsert.
 *         <ul>
 *             <li>활성 행 존재 → 그대로 사용.</li>
 *             <li>미존재 → {@link User#createOauthUser} 로 신규 생성 (userSeq DB 자동 발급).</li>
 *         </ul>
 *     </li>
 *     <li>{@link AuthFacade#issueLoginTokens(User, String)} — Access + Refresh 동시 발급.</li>
 *     <li>Refresh Token 은 {@code Set-Cookie httpOnly} 로, Access Token 은 frontend redirect URL 의 <b>프래그먼트</b>로 전달.</li>
 * </ol>
 *
 * <h3>왜 프래그먼트인가</h3>
 * <p>프래그먼트(`#token=...`)는 HTTP 요청 URL 에 포함되지 않아 Referer 헤더와 서버 access log 에 노출되지 않는다.
 * Refresh Token 은 URL 에 절대 싣지 않고 쿠키로만 전달.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthFacade authFacade;

    @Value("${app.oauth.frontend-redirect-uri}")
    private String frontendRedirectUri;

    @Value("${refresh-token.cookie-name}")
    private String refreshCookieName;

    @Value("${refresh-token.cookie-path}")
    private String refreshCookiePath;

    @Value("${refresh-token.validity-seconds}")
    private long refreshValiditySeconds;

    /**
     * Refresh 쿠키 Secure 플래그 — AuthController 와 동일 정책. dev=false, prod=true.
     */
    @Value("${refresh-token.cookie-secure}")
    private boolean refreshCookieSecure;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2UserPrincipal principal)) {
            log.error("Authentication.principal 이 OAuth2UserPrincipal 이 아닙니다: {}",
                    authentication.getPrincipal().getClass());
            response.sendRedirect(buildFailureUri("UNEXPECTED_PRINCIPAL"));
            return;
        }

        User user = upsert(principal);
        IssuedTokens tokens = authFacade.issueLoginTokens(user, request.getHeader(HttpHeaders.USER_AGENT));

        // Refresh Token 은 쿠키로
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, tokens.refreshTokenRaw())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path(refreshCookiePath)
                .maxAge(Duration.ofSeconds(refreshValiditySeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Access Token 은 URL 프래그먼트로
        String fragment = "token=" + tokens.accessToken()
                + "&tokenType=Bearer"
                + "&expiresIn=" + tokens.accessExpiresInSeconds();
        String redirect = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment(fragment)
                .build()
                .toUriString();
        log.info("OAuth2 로그인 성공 — userSeq={}, provider={}, redirect={}",
                user.getUserSeq(), user.getOauthService(), frontendRedirectUri);
        response.sendRedirect(redirect);
    }

    /**
     * upsert — 활성 행이 있으면 그대로, 없으면 신규 생성.
     */
    private User upsert(OAuth2UserPrincipal principal) {
        return userRepository.findByOauthServiceAndProviderIdAndIsActiveTrue(
                        principal.oauthService(), principal.providerId())
                .orElseGet(() -> userRepository.save(
                        User.createOauthUser(principal.oauthService(), principal.providerId(), principal.nickname())));
    }

    private String buildFailureUri(String reason) {
        return UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("error", "OAUTH_FAILED")
                .queryParam("reason", reason)
                .build()
                .toUriString();
    }
}
