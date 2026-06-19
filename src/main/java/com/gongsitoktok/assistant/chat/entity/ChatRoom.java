/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/entity/ChatRoom.java
 */
package com.gongsitoktok.assistant.chat.entity;

import com.gongsitoktok.assistant.company.entity.Company;
import com.gongsitoktok.assistant.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 대화방 세션 엔티티 — {@code tb_chat_room}.
 *
 * <h3>핵심 정책 (v2 기획 변경)</h3>
 * <ul>
 *     <li><b>한 방 = 정확히 한 기업</b>. {@link #company} FK 는 생성 시점에 박히고 이후 변경 불가.</li>
 *     <li><b>{@code isActive} 의 의미</b> = <b>세션 활성 여부</b>.
 *         <ul>
 *             <li>{@code true}  — 상세페이지에서 사용 중인 세션 (마지막 입력 후 30분 이내). 마이페이지 노출 대상 <b>아님</b>.</li>
 *             <li>{@code false} — 30분 초과로 만료된 세션. 마이페이지에서 기록 조회 전용으로 노출.</li>
 *         </ul>
 *     </li>
 *     <li><b>{@link #closedAt}</b> — 세션이 만료(또는 명시적으로 닫힘) 된 시각. 활성 상태에서는 {@code null}.</li>
 *     <li><b>30분 만료 트리거</b>:
 *         <ul>
 *             <li>{@code /chat/continue} 시도 — 만료면 {@link #close} 처리 후 {@code 410 CHAT_ROOM_EXPIRED}.</li>
 *             <li>{@code /chat/rooms} 조회 — 만료 임계 초과한 활성 방을 lazy 로 {@link #close} 처리해서 마이페이지에 자동 노출.</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h3>인덱스</h3>
 * <p>{@code (user_seq, is_active, last_active_at DESC)} — 사이드바 목록 및 만료 임계 초과 활성 방 일괄 조회.</p>
 *
 * <h3>FK 안정성</h3>
 * <p>FK 는 항상 불변 {@link User#getUserSeq()} · {@link Company#getCompanySeq()} 를 참조. cascade·@OnDelete 명시 없음.
 * 회원이 dismiss 변형되어도 {@code userSeq} 는 불변이므로 FK 깨지지 않는다.</p>
 */
@Entity
@Table(
        name = "tb_chat_room",
        indexes = {
                @Index(name = "idx_room_user_active_last",
                        columnList = "user_seq, is_active, last_active_at DESC")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    /** FK → {@link User#getUserSeq()}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_seq", referencedColumnName = "user_seq", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_room_user"))
    private User user;

    /** FK → {@link Company#getCompanySeq()}. 한 방 = 한 기업. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_seq", referencedColumnName = "company_seq", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_room_company"))
    private Company company;

    /** 최초 질문 첫 20자 슬라이싱. 컬럼 길이는 마진 포함 50. */
    @Column(name = "room_title", nullable = false, length = 50)
    private String roomTitle;

    /** 챗봇 대화방 최종 활성화 시각. 질문 전송 시마다 갱신. */
    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    /**
     * 세션 활성 여부.
     * <ul>
     *     <li>{@code true}  — 상세페이지에서 사용 가능한 세션. 마이페이지 비노출.</li>
     *     <li>{@code false} — 만료(또는 명시적 close) 처리된 세션. 마이페이지에 기록 조회 용도로 노출.</li>
     * </ul>
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /** 세션 만료 시각. 활성 상태에서는 {@code null}. {@link #close(LocalDateTime)} 시 세팅. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 정적 팩토리 — 신규 대화방 생성. 30자 초과 질문은 20자에서 잘라 제목으로 사용.
     * 새 방은 항상 {@code isActive=true}, {@code closedAt=null} 로 시작.
     */
    public static ChatRoom create(User user, Company company, String firstQuestion, LocalDateTime now) {
        ChatRoom r = new ChatRoom();
        r.user = user;
        r.company = company;
        r.roomTitle = slice(firstQuestion);
        r.lastActiveAt = now;
        r.isActive = true;
        r.closedAt = null;
        return r;
    }

    /**
     * 대화방 활성 시간 터치 ({@code /chat/continue} 호출 시 사용).
     * <p>{@code isActive=false} 상태에서는 호출되지 않아야 한다 (서비스 레이어에서 사전 차단).</p>
     */
    public void touch(LocalDateTime now) {
        this.lastActiveAt = now;
    }

    /**
     * 세션 닫기 — 만료 감지 또는 명시적 종료 시점에 호출.
     * <ul>
     *     <li>{@link #isActive} → {@code false}</li>
     *     <li>{@link #closedAt} → {@code now} (멱등 — 이미 닫혀 있으면 변경 안 함)</li>
     * </ul>
     */
    public void close(LocalDateTime now) {
        if (this.isActive) {
            this.isActive = false;
            this.closedAt = now;
        }
    }

    /**
     * 30분 만료 여부. {@code lastActiveAt + 30분 < now} 이면 만료.
     */
    public boolean isExpired(LocalDateTime now) {
        return this.lastActiveAt.plusMinutes(30).isBefore(now);
    }

    /**
     * 본인 소유 여부 검증.
     */
    public boolean isOwnedBy(Long userSeq) {
        return this.user.getUserSeq().equals(userSeq);
    }

    private static String slice(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        return trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20);
    }
}
