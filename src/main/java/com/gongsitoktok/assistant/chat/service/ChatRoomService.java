/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/service/ChatRoomService.java
 */
package com.gongsitoktok.assistant.chat.service;

import com.gongsitoktok.assistant.chat.dto.ChatMessagesResponse;
import com.gongsitoktok.assistant.chat.dto.ChatRoomListItemResponse;
import com.gongsitoktok.assistant.chat.entity.ChatRoom;
import com.gongsitoktok.assistant.chat.entity.QaHistory;
import com.gongsitoktok.assistant.chat.repository.ChatRoomRepository;
import com.gongsitoktok.assistant.chat.repository.QaHistoryRepository;
import com.gongsitoktok.assistant.company.entity.Company;
import com.gongsitoktok.assistant.company.repository.CompanyRepository;
import com.gongsitoktok.assistant.global.error.ErrorCode;
import com.gongsitoktok.assistant.global.error.exception.BusinessException;
import com.gongsitoktok.assistant.global.error.exception.ChatRoomExpiredException;
import com.gongsitoktok.assistant.global.error.exception.ChatRoomNotFoundException;
import com.gongsitoktok.assistant.global.error.exception.CompanyNotFoundException;
import com.gongsitoktok.assistant.user.entity.User;
import com.gongsitoktok.assistant.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대화방 라이프사이클 서비스 (v2 기획 — {@code isActive} 의미 = 세션 활성 여부).
 *
 * <h3>isActive 의미 재정의 — 마이페이지·상세페이지 분리</h3>
 * <ul>
 *     <li><b>상세페이지</b> 챗봇 패널 → {@code isActive=true} (현 세션) 만 사용. {@code /ask} 로 새 방 생성, {@code /continue} 로 후속.</li>
 *     <li><b>마이페이지</b> 기록 조회 → {@code isActive=false} (만료 세션) 만 노출. 활성 세션은 노출 안 함.</li>
 *     <li>30분 만료 트리거는 두 경로에서 모두 적용:
 *         <ul>
 *             <li>{@code /chat/continue} 시도 시 만료 감지 → {@link ChatRoom#close} + {@link ChatRoomExpiredException}.</li>
 *             <li>{@code /chat/rooms} 조회 시 만료 임계 초과한 활성 방 일괄 lazy {@link ChatRoom#close}.</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h3>본인 소유 검증 통일</h3>
 * <p>"방이 없다" / "다른 사람 것이다" 둘 다 단일 {@link ChatRoomNotFoundException} (404) 로 응답해 정보 누출 차단.</p>
 */
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private static final int SESSION_TIMEOUT_MINUTES = 30;

    private final ChatRoomRepository chatRoomRepository;
    private final QaHistoryRepository qaHistoryRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    /**
     * 최초 질문 시 대화방 생성 — 새 방은 항상 {@code isActive=true} 로 시작.
     */
    @Transactional
    public ChatRoom createRoom(Long userSeq, String corpCode, String firstQuestion) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }
        Company company = companyRepository.findByCorpCodeAndIsActiveTrue(corpCode)
                .orElseThrow(() -> new CompanyNotFoundException(corpCode));
        return chatRoomRepository.save(ChatRoom.create(user, company, firstQuestion, LocalDateTime.now()));
    }

    /**
     * {@code /continue} 진입 시 본인 소유 + 활성 + 만료 검증 후 lastActiveAt 터치.
     *
     * <p>{@code isActive=false} (이미 만료된 방) 진입 시 정보 누출 차단 위해 404 응답. 활성이지만 30분 초과면
     * 본 메서드 내에서 {@link ChatRoom#close} 마킹 후 410 {@code CHAT_ROOM_EXPIRED}.</p>
     */
    @Transactional
    public ChatRoom validateAndTouch(Long roomId, Long userSeq) {
        ChatRoom room = chatRoomRepository.findByIdWithCompany(roomId)
                .orElseThrow(() -> new ChatRoomNotFoundException(roomId));
        if (!room.isOwnedBy(userSeq) || !room.isActive()) {
            throw new ChatRoomNotFoundException(roomId);
        }
        LocalDateTime now = LocalDateTime.now();
        if (room.isExpired(now)) {
            room.close(now);
            throw new ChatRoomExpiredException(roomId);
        }
        room.touch(now);
        return room;
    }

    /**
     * 마이페이지 — 만료된 방(=closed) 만 시간 역순으로 반환.
     *
     * <h3>Lazy close 처리</h3>
     * <p>호출 시점에 만료 임계({@code lastActiveAt + 30분 < now}) 를 이미 넘긴 활성 방들이 있으면 일괄
     * {@link ChatRoom#close} 처리해서 마이페이지에 자연 노출. 이로써 사용자가 {@code /continue} 를 시도하지 않아도
     * 시간이 지나면 자동으로 마이페이지에 항목이 쌓이는 흐름이 보장된다.</p>
     */
    @Transactional
    public List<ChatRoomListItemResponse> list(Long userSeq) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiredBefore = now.minusMinutes(SESSION_TIMEOUT_MINUTES);

        // 1. 만료 임계 초과한 활성 방을 일괄 close 처리 (dirty checking 으로 commit 시 UPDATE)
        for (ChatRoom expired : chatRoomRepository.findActiveExpiredByUserSeq(userSeq, expiredBefore)) {
            expired.close(now);
        }

        // 2. closed(만료) 방만 반환
        return chatRoomRepository.findClosedByUserSeqWithCompany(userSeq).stream()
                .map(ChatRoomListItemResponse::from)
                .toList();
    }

    /**
     * 타임라인 조회 — 만료된 방의 기록 열람이 마이페이지의 주 기능이므로 {@code isActive} 무관하게 본인 소유면 허용.
     */
    @Transactional(readOnly = true)
    public ChatMessagesResponse timeline(Long roomId, Long userSeq) {
        ChatRoom room = chatRoomRepository.findByIdWithCompany(roomId)
                .orElseThrow(() -> new ChatRoomNotFoundException(roomId));
        if (!room.isOwnedBy(userSeq)) {
            throw new ChatRoomNotFoundException(roomId);
        }
        List<QaHistory> history = qaHistoryRepository.findByChatRoomRoomIdOrderByCreatedAtAsc(roomId);
        List<ChatMessagesResponse.Item> items = history.stream()
                .map(ChatMessagesResponse.Item::from)
                .toList();
        return new ChatMessagesResponse(
                room.getRoomId(),
                room.getCompany().getCorpCode(),
                room.getCompany().getCorpName(),
                items
        );
    }
}
