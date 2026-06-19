/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/dto/ChatRoomListItemResponse.java
 */
package com.gongsitoktok.assistant.chat.dto;

import com.gongsitoktok.assistant.chat.entity.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 마이페이지 대화방 목록 항목 (v2 — {@code isActive=false} 만 노출 정책).
 *
 * <p>{@code corpCode}/{@code corpName} 은 JOIN FETCH 로 한 쿼리에 묶어 조회.</p>
 *
 * @param roomId       대화방 PK
 * @param roomTitle    최초 질문 첫 20자 슬라이싱 (목록 카드 본문 표시용)
 * @param corpCode     기업 외부 노출 키
 * @param corpName     기업명 (목록 카드 칩 표시용)
 * @param createdAt    방 생성 시각 — 마이페이지에서 날짜별 그룹화 키
 * @param lastActiveAt 마지막 활동 시각 — 카드 우측 시간 표시용
 * @param closedAt     세션 종료(만료) 시각. 활성 방은 {@code null} 이지만 본 목록은 만료 방만 반환하므로 항상 값 있음
 */
@Schema(description = "대화방 목록 항목 (마이페이지용)")
public record ChatRoomListItemResponse(
        Long roomId,
        String roomTitle,
        String corpCode,
        String corpName,
        LocalDateTime createdAt,
        LocalDateTime lastActiveAt,
        LocalDateTime closedAt
) {
    public static ChatRoomListItemResponse from(ChatRoom r) {
        return new ChatRoomListItemResponse(
                r.getRoomId(),
                r.getRoomTitle(),
                r.getCompany().getCorpCode(),
                r.getCompany().getCorpName(),
                r.getCreatedAt(),
                r.getLastActiveAt(),
                r.getClosedAt()
        );
    }
}
