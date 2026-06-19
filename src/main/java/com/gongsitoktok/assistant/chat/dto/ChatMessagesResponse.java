/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/dto/ChatMessagesResponse.java
 */
package com.gongsitoktok.assistant.chat.dto;

import com.gongsitoktok.assistant.chat.entity.QaHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대화방 타임라인 응답 (제작요청 v6 §4-12).
 *
 * @param roomId    방 id
 * @param corpCode  방에 박힌 기업 corpCode
 * @param corpName  방에 박힌 기업명
 * @param messages  과거 Q&A 시간순 (createdAt ASC)
 */
@Schema(description = "대화방 타임라인")
public record ChatMessagesResponse(
        Long roomId,
        String corpCode,
        String corpName,
        List<Item> messages
) {

    /**
     * 타임라인 단일 turn.
     *
     * @param groundedScore  답변 신뢰도 0.0~1.0 (nullable). 프론트의 출처 모달 우측 하단 "정확도" 뱃지에 사용
     */
    @Schema(description = "Q&A 한 턴")
    public record Item(
            String question,
            String answer,
            String sourceContent,
            String macroSnapshot,
            Double groundedScore,
            LocalDateTime createdAt
    ) {
        public static Item from(QaHistory q) {
            return new Item(
                    q.getQuestion(),
                    q.getAnswer(),
                    q.getSourceContent(),
                    q.getMacroSnapshot(),
                    q.getGroundedScore(),
                    q.getCreatedAt()
            );
        }
    }
}
