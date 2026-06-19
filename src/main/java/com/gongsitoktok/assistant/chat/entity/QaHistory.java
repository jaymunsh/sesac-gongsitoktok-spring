/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/entity/QaHistory.java
 */
package com.gongsitoktok.assistant.chat.entity;

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
 * 질의응답 이력 엔티티 — {@code tb_qa_history}.
 *
 * <h3>설계 결정</h3>
 * <ul>
 *     <li>별도의 근거 매핑 테이블(tb_qa_source) 을 두지 않음 — 인용 근거는 FastAPI 가 미리 포맷한 단일 텍스트({@link #sourceContent}) 로 한 컬럼에 보관.</li>
 *     <li>{@code chat_room} cascade·@OnDelete 명시 없음 — 채팅방이 hide(soft delete)되어도 본 행은 보존된다.</li>
 *     <li>인덱스 {@code (chat_room_id, created_at)} — 타임라인 조회(§4-12) 및 멀티턴 이력 빌드(§4-10) 쿼리 가속.</li>
 * </ul>
 */
@Entity
@Table(
        name = "tb_qa_history",
        indexes = {
                @Index(name = "idx_qa_room_created", columnList = "chat_room_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qa_id")
    private Long qaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", referencedColumnName = "room_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_qa_room"))
    private ChatRoom chatRoom;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    /**
     * FastAPI 가 인용 공시 조각들을 사람이 읽을 수 있는 형태로 묶은 자유 텍스트.
     * <p>예: {@code "[20240515000123 / 삼성전자 분기보고서] 매출 ...\n\n[20240612000045 / SK하이닉스 ...]"}</p>
     */
    @Column(name = "source_content", columnDefinition = "TEXT")
    private String sourceContent;

    /** 환율/금리 등 거시 지표 스냅샷. 필요 시 채움. */
    @Column(name = "macro_snapshot", columnDefinition = "TEXT")
    private String macroSnapshot;

    /**
     * 답변 신뢰도 — FastAPI {@code verification.groundedScore} (0.0 ~ 1.0, nullable).
     *
     * <p>{@code DOUBLE PRECISION} 으로 매핑. 검증을 우회한 응답(예: smalltalk·out_of_scope) 은 {@code null}.</p>
     * <p>UI 에서는 "정확도" 라벨로 노출되며 출처 모달의 우측 하단 뱃지로 표기.</p>
     */
    @Column(name = "grounded_score")
    private Double groundedScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 정적 팩토리 — 신규 QA 행 적재.
     *
     * @param chatRoom       대상 대화방
     * @param question       사용자 질문 (TEXT, NOT NULL)
     * @param answer         FastAPI 답변 (TEXT, NOT NULL)
     * @param sourceContent  인용 근거 텍스트 (nullable)
     * @param macroSnapshot  거시 지표 스냅샷 (nullable)
     * @param groundedScore  답변 신뢰도 0.0~1.0 (nullable)
     */
    public static QaHistory record(ChatRoom chatRoom, String question, String answer,
                                   String sourceContent, String macroSnapshot, Double groundedScore) {
        QaHistory q = new QaHistory();
        q.chatRoom = chatRoom;
        q.question = question;
        q.answer = answer;
        q.sourceContent = sourceContent;
        q.macroSnapshot = macroSnapshot;
        q.groundedScore = groundedScore;
        return q;
    }
}
