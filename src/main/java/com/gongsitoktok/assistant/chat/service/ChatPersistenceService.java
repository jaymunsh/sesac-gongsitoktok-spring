/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/service/ChatPersistenceService.java
 */
package com.gongsitoktok.assistant.chat.service;

import com.gongsitoktok.assistant.chat.dto.fastapi.FastApiChatResponse;
import com.gongsitoktok.assistant.chat.entity.ChatRoom;
import com.gongsitoktok.assistant.chat.entity.QaHistory;
import com.gongsitoktok.assistant.chat.repository.ChatRoomRepository;
import com.gongsitoktok.assistant.chat.repository.QaHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QA 영속화 전용 서비스 — <b>반드시 별도 빈으로 분리</b> (제작요청 v6 §스텝 E · Spring_챗봇_핸들링_가이드 §7).
 *
 * <h3>Self-Invocation 함정</h3>
 * <p>{@code @Async} + {@code @Transactional} 은 Spring AOP 프록시 기반이라, {@code ChatController} 가 같은
 * 클래스의 메서드를 직접 호출하면 프록시가 우회되어 비동기·트랜잭션이 둘 다 동작하지 않는다. 별도 빈으로 분리하여
 * 외부에서 주입받아 호출해야 한다.</p>
 *
 * <h3>실행 풀</h3>
 * <p>{@code chatPersistenceExecutor} ({@link com.gongsitoktok.assistant.global.config.AsyncConfig}) 가 가상 스레드 풀.
 * TaskDecorator 덕분에 MDC traceId 가 자식 작업까지 자동 전파된다.</p>
 *
 * <h3>실패 정책</h3>
 * <p>영속화 실패는 사용자 응답에 영향 주지 않는다 (이미 응답 완료 시점). DB 사고는 로그만 남기고 GG.
 * 추후 모니터링 도입 시 Counter 로 시각화 (§스텝 G TODO).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPersistenceService {

    private final QaHistoryRepository qaHistoryRepository;
    private final ChatRoomRepository chatRoomRepository;

    /**
     * QA 행 적재 — 새로운 트랜잭션에서 가상 스레드 비동기 실행.
     *
     * @param roomId   대상 방
     * @param question 사용자 질문 (원본 그대로)
     * @param response FastAPI 정상 응답 ({@code error == null} 보장된 호출 측 책임)
     */
    @Async("chatPersistenceExecutor")
    @Transactional
    public void persistAsync(Long roomId, String question, FastApiChatResponse response) {
        try {
            ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
            if (room == null) {
                log.warn("영속화 시 방을 찾지 못함 — roomId={}", roomId);
                return;
            }
            Double groundedScore = response.verification() == null
                    ? null
                    : response.verification().groundedScore();
            QaHistory entity = QaHistory.record(
                    room,
                    question,
                    response.answerText(),
                    response.sourceContent(),
                    response.macroSnapshot(),
                    groundedScore
            );
            qaHistoryRepository.save(entity);
            // TODO(monitoring): Counter("chat.persist.ok") 등록
        } catch (Exception ex) {
            // TODO(monitoring): Counter("chat.persist.fail") 등록
            log.error("QA 영속화 실패 — roomId={}, question={}", roomId, abbreviate(question), ex);
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
    }
}
