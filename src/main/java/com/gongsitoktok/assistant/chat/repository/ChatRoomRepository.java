/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/repository/ChatRoomRepository.java
 */
package com.gongsitoktok.assistant.chat.repository;

import com.gongsitoktok.assistant.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 대화방 리포지토리 (v2 기획 — {@code isActive} 의미 = 세션 활성 여부).
 *
 * <p>본인 소유 검증·만료 검증은 모두 서비스 레이어에서 명시적으로 적용한다.</p>
 */
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 마이페이지 노출 목록 — {@code isActive=false} (만료된 방) 만 최근 활동 순으로 조회.
     *
     * <p>{@code company} 를 JOIN FETCH 하여 N+1 차단.</p>
     */
    @Query("""
            SELECT r FROM ChatRoom r
              JOIN FETCH r.company c
             WHERE r.user.userSeq = :userSeq
               AND r.isActive = false
             ORDER BY r.lastActiveAt DESC
            """)
    List<ChatRoom> findClosedByUserSeqWithCompany(@Param("userSeq") Long userSeq);

    /**
     * 만료 임계({@code lastActiveAt + 30분}) 를 이미 넘긴 활성 방 일괄 조회 — 마이페이지 조회 시점에 lazy close 처리용.
     *
     * <p>{@code expiredBefore} 파라미터는 호출 측에서 {@code now.minusMinutes(30)} 를 전달.</p>
     */
    @Query("""
            SELECT r FROM ChatRoom r
             WHERE r.user.userSeq = :userSeq
               AND r.isActive = true
               AND r.lastActiveAt < :expiredBefore
            """)
    List<ChatRoom> findActiveExpiredByUserSeq(@Param("userSeq") Long userSeq,
                                              @Param("expiredBefore") LocalDateTime expiredBefore);

    /**
     * 단건 조회 — {@code company} 함께 fetch.
     * <p>본인 소유 검증·만료 검증·활성 검증은 모두 서비스 레이어 책임.</p>
     */
    @Query("""
            SELECT r FROM ChatRoom r
              JOIN FETCH r.company c
             WHERE r.roomId = :roomId
            """)
    Optional<ChatRoom> findByIdWithCompany(@Param("roomId") Long roomId);
}
