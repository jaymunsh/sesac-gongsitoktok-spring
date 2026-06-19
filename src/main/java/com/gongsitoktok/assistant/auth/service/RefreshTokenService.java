/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/auth/service/RefreshTokenService.java
 */
package com.gongsitoktok.assistant.auth.service;

import com.gongsitoktok.assistant.auth.entity.RefreshToken;
import com.gongsitoktok.assistant.auth.repository.RefreshTokenRepository;
import com.gongsitoktok.assistant.global.error.ErrorCode;
import com.gongsitoktok.assistant.global.error.exception.InvalidRefreshTokenException;
import com.gongsitoktok.assistant.global.error.exception.RefreshTokenReusedException;
import com.gongsitoktok.assistant.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Refresh Token 발급·회전·revoke 책임 서비스 (제작요청 v6 §스텝 H-5).
 *
 * <h3>핵심 정책</h3>
 * <ul>
 *     <li>원본 토큰은 절대 DB 에 저장하지 않음. {@link #hash(String)} (SHA-256 hex 64자) 만 저장.</li>
 *     <li>{@link #rotate(String, String)} 시 이미 revoke 된 해시가 들어오면 = 도난 시나리오 →
 *         {@link RefreshTokenReusedException} + {@code revokeAllByUserSeq} 일괄 처리.</li>
 *     <li>{@link #revokeAllByUser(Long)} 은 비밀번호 변경(§4-5) · 탈퇴(§4-6) 시 호출.</li>
 * </ul>
 *
 * <h3>왜 SecureRandom 32 bytes 인가</h3>
 * <p>256bit 엔트로피는 brute-force 공격을 사실상 불가능하게 만든다 (랜덤 충돌 확률 미미). base64url 인코딩 시 약 43자 ASCII.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository repository;

    @Value("${refresh-token.validity-seconds}")
    private long validitySeconds;

    /**
     * 신규 Refresh Token 발급.
     *
     * <p>원본은 호출자가 응답 쿠키에 실어 클라이언트에게 단 한 번 노출하고, 서버는 해시만 보관한다.</p>
     *
     * @param user      소유 사용자 (불변 userSeq 참조)
     * @param userAgent 요청 헤더 User-Agent (감사용)
     * @return 클라이언트에 내려보낼 raw 토큰 (base64url 문자열)
     */
    @Transactional
    public String issue(User user, String userAgent) {
        String raw = generateRaw();
        String tokenHash = hash(raw);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(validitySeconds);
        repository.save(RefreshToken.issue(user, tokenHash, expiresAt, userAgent));
        return raw;
    }

    /**
     * Refresh Token 회전 (§4-2c).
     *
     * <ol>
     *     <li>raw → 해시 → 단건 조회. 없으면 {@link InvalidRefreshTokenException}.</li>
     *     <li>이미 revoke 됨 → 도난 의심 → {@link #revokeAllByUser(Long)} + {@link RefreshTokenReusedException}.</li>
     *     <li>만료 → {@code REFRESH_TOKEN_EXPIRED}.</li>
     *     <li>정상 → 기존 row revoke + 새 토큰 발급.</li>
     * </ol>
     *
     * @return 회전 결과 — 새 raw 토큰 + 소유 사용자
     */
    @Transactional
    public RotateResult rotate(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        String oldHash = hash(rawToken);
        RefreshToken stored = repository.findByTokenHash(oldHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        LocalDateTime now = LocalDateTime.now();

        if (stored.getRevokedAt() != null) {
            // 이미 revoke 된 토큰의 재사용 — 도난 시나리오. 모든 세션 종료.
            Long userSeq = stored.getUser().getUserSeq();
            repository.revokeAllByUserSeq(userSeq, now);
            log.warn("Refresh Token 재사용 감지 — userSeq={} 전체 revoke 처리", userSeq);
            throw new RefreshTokenReusedException();
        }

        if (!stored.isValid(now)) {
            throw new InvalidRefreshTokenException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // 정상: 기존 revoke + 새 발급
        stored.revoke(now);
        User owner = stored.getUser();
        String newRaw = issue(owner, userAgent);
        // ⚠️ owner 는 @ManyToOne(LAZY) 프록시. 트랜잭션 종료 후 (AuthController) 에서 owner.getUserId() 등을 호출하면
        // OSIV=false 설정상 LazyInitializationException 이 떨어진다.
        // 따라서 트랜잭션 안에서 미리 필요한 필드를 추출해 primitive 만 들고 나간다.
        return new RotateResult(newRaw, owner.getUserSeq(), owner.getUserId(), owner.getOauthService());
    }

    /**
     * 로그아웃 시 단건 revoke.
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hash(rawToken);
        repository.findByTokenHash(tokenHash).ifPresent(t -> t.revoke(LocalDateTime.now()));
    }

    /**
     * 비밀번호 변경 · 탈퇴 시 전체 무효화.
     *
     * @return revoke 처리된 row 수
     */
    @Transactional
    public int revokeAllByUser(Long userSeq) {
        return repository.revokeAllByUserSeq(userSeq, LocalDateTime.now());
    }

    /**
     * SHA-256 hex 인코딩. {@code MessageDigest} 는 thread-safe 가 아니므로 매 호출마다 새 인스턴스.
     */
    public static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 알고리즘이 존재하지 않습니다.", ex);
        }
    }

    private String generateRaw() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 회전 결과.
     *
     * <p>{@code User} 엔티티를 직접 들고 다니지 않는다 — {@link #rotate(String, String)} 의 트랜잭션이 종료되면
     * lazy 프록시 접근 시 {@code LazyInitializationException} 이 떨어지기 때문이다 (OSIV=false 설정 기준).
     * 토큰 발급에 필요한 사용자 식별 필드를 트랜잭션 안에서 미리 추출해 primitive 로 넘긴다.</p>
     *
     * @param newRawToken  클라이언트 쿠키에 실을 새 raw 토큰
     * @param userSeq      소유 사용자 PK
     * @param userId       소유 사용자 노출 ID (JWT custom claim 용)
     * @param oauthService 소유 사용자 가입 경로 (JWT custom claim 용)
     */
    public record RotateResult(
            String newRawToken,
            Long userSeq,
            String userId,
            com.gongsitoktok.assistant.user.entity.OauthService oauthService
    ) {
    }
}
