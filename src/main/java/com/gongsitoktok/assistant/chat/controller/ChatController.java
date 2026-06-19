/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/chat/controller/ChatController.java
 */
package com.gongsitoktok.assistant.chat.controller;

import com.gongsitoktok.assistant.chat.client.FastApiChatClient;
import com.gongsitoktok.assistant.chat.client.UpstreamErrorMapper;
import com.gongsitoktok.assistant.chat.dto.AskRequest;
import com.gongsitoktok.assistant.chat.dto.ChatMessagesResponse;
import com.gongsitoktok.assistant.chat.dto.ChatResponse;
import com.gongsitoktok.assistant.chat.dto.ChatRoomListItemResponse;
import com.gongsitoktok.assistant.chat.dto.ContinueRequest;
import com.gongsitoktok.assistant.chat.dto.fastapi.CompanyContext;
import com.gongsitoktok.assistant.chat.dto.fastapi.FastApiChatRequest;
import com.gongsitoktok.assistant.chat.dto.fastapi.FastApiChatResponse;
import com.gongsitoktok.assistant.chat.dto.fastapi.MessageDto;
import com.gongsitoktok.assistant.chat.entity.ChatRoom;
import com.gongsitoktok.assistant.chat.service.ChatHistoryService;
import com.gongsitoktok.assistant.chat.service.ChatPersistenceService;
import com.gongsitoktok.assistant.chat.service.ChatRoomService;
import com.gongsitoktok.assistant.global.error.ErrorCode;
import com.gongsitoktok.assistant.global.error.exception.UpstreamUnavailableException;
import com.gongsitoktok.assistant.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 챗봇 컨트롤러 — Spring ↔ Browser 동기 JSON (Spring_챗봇_핸들링_가이드 §3 · 제작요청 v6 §4-9 ~ §4-13).
 *
 * <h3>처리 순서 (ask / continue 공통)</h3>
 * <ol>
 *     <li>요청 검증 (Bean Validation + 도메인 검증).</li>
 *     <li>대화방 생성({@code /ask}) 또는 검증·이력 조립({@code /continue}).</li>
 *     <li>{@link FastApiChatClient#call} 동기 호출 ({@code .block()}; 가상 스레드 위 carrier 점유 없음).</li>
 *     <li>응답 body 의 {@code error} 필드 분기 — null 이면 정상, 아니면 {@link UpstreamErrorMapper} 로 변환 throw.</li>
 *     <li>정상 응답만 {@link ChatPersistenceService#persistAsync} 호출 (별도 빈, fire-and-forget).</li>
 *     <li>{@link ChatResponse} 반환.</li>
 * </ol>
 *
 * <h3>가상 스레드 + {@code .block()}</h3>
 * <p>Tomcat servlet 스레드가 가상 스레드로 활성화되어 있으므로 {@code .block()} 호출 시 carrier 가 해방된다.
 * 다른 가상 스레드가 동일 carrier 를 즉시 사용 가능 → 대규모 동시 요청에서도 carrier 부족 없음.</p>
 */
@Tag(name = "Chat", description = "챗봇 — 최초 질문 · 이어하기 · 목록 · 타임라인 · 숨김")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final ChatHistoryService chatHistoryService;
    private final ChatPersistenceService chatPersistenceService;
    private final FastApiChatClient fastApiChatClient;
    private final UpstreamErrorMapper upstreamErrorMapper;

    @Operation(summary = "최초 질문 전송 및 대화방 생성",
            description = "기업 상세 페이지에서 챗봇을 처음 열어 첫 질문 전송. 내부적으로 tb_chat_room 즉시 개설 후 FastAPI 호출.")
    @ApiResponse(responseCode = "200", description = "정상 응답")
    @ApiResponse(responseCode = "404", description = "COMPANY_NOT_FOUND")
    @ApiResponse(responseCode = "502", description = "UPSTREAM_* (FastAPI 사고)")
    @PostMapping(value = "/ask", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse ask(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AskRequest req
    ) {
        ChatRoom room = chatRoomService.createRoom(principal.userSeq(), req.corpCode(), req.question());

        FastApiChatRequest fastReq = new FastApiChatRequest(
                room.getRoomId(),
                principal.userSeq(),
                new CompanyContext(room.getCompany().getCorpCode(), room.getCompany().getCorpName()),
                List.of(MessageDto.user(req.question()))
        );
        FastApiChatResponse fastResp = blockOnFastApi(fastReq);
        handleErrorOrPersist(room.getRoomId(), req.question(), fastResp);
        return ChatResponse.from(room.getRoomId(), fastResp);
    }

    @Operation(summary = "기존 대화방 이어하기 (Multi-turn)",
            description = "30분 만료 검증 + 본인 소유 검증. companyContext 는 DB 에서 자동 첨부 (다른 기업 우회 차단).")
    @ApiResponse(responseCode = "200", description = "정상 응답")
    @ApiResponse(responseCode = "404", description = "CHAT_ROOM_NOT_FOUND")
    @ApiResponse(responseCode = "410", description = "CHAT_ROOM_EXPIRED")
    @ApiResponse(responseCode = "502", description = "UPSTREAM_* (FastAPI 사고)")
    @PostMapping(value = "/room/{roomId}/continue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse continueChat(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId,
            @Valid @RequestBody ContinueRequest req
    ) {
        ChatRoom room = chatRoomService.validateAndTouch(roomId, principal.userSeq());
        List<MessageDto> messages = chatHistoryService.buildMessages(roomId, req.question());

        FastApiChatRequest fastReq = new FastApiChatRequest(
                roomId,
                principal.userSeq(),
                new CompanyContext(room.getCompany().getCorpCode(), room.getCompany().getCorpName()),
                messages
        );
        FastApiChatResponse fastResp = blockOnFastApi(fastReq);
        handleErrorOrPersist(roomId, req.question(), fastResp);
        return ChatResponse.from(roomId, fastResp);
    }

    @Operation(summary = "마이페이지 — 만료된 대화방 목록",
            description = "isActive=false (만료 = closed) 만 lastActiveAt 내림차순. 호출 시점에 만료 임계 초과한 활성 방은 자동 close 처리되어 목록에 합류.")
    @ApiResponse(responseCode = "200", description = "목록 반환")
    @GetMapping("/rooms")
    public List<ChatRoomListItemResponse> rooms(@AuthenticationPrincipal UserPrincipal principal) {
        return chatRoomService.list(principal.userSeq());
    }

    @Operation(summary = "특정 대화방 내용 조회 (타임라인)",
            description = "방 메타 + Q&A 시간순 묶음. 활성·만료 무관 본인 소유면 열람 가능 (마이페이지의 기록 조회 용도).")
    @ApiResponse(responseCode = "200", description = "타임라인 반환")
    @ApiResponse(responseCode = "404", description = "CHAT_ROOM_NOT_FOUND")
    @GetMapping("/room/{roomId}/messages")
    public ChatMessagesResponse messages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId
    ) {
        return chatRoomService.timeline(roomId, principal.userSeq());
    }

    // ===== 내부 헬퍼 =====
    // (v2 기획 변경: /chat/room/{roomId}/hide endpoint 제거.
    //  isActive 의미가 "세션 활성 여부" 로 재정의되어 사용자 hide 개념이 자연 소멸.)

    /**
     * FastAPI 호출 + 결과 동기 대기. null 응답은 방어적으로 UPSTREAM_ERROR 변환.
     */
    private FastApiChatResponse blockOnFastApi(FastApiChatRequest req) {
        FastApiChatResponse resp = fastApiChatClient.call(req).block();
        if (resp == null) {
            throw new UpstreamUnavailableException(ErrorCode.UPSTREAM_ERROR, "FastAPI 가 빈 응답을 반환했습니다.");
        }
        return resp;
    }

    /**
     * 응답 분기 — error 필드 분기 후 정상이면 영속화, 실패면 throw.
     */
    private void handleErrorOrPersist(Long roomId, String question, FastApiChatResponse resp) {
        if (resp.error() != null) {
            throw upstreamErrorMapper.toException(resp.error());
        }
        chatPersistenceService.persistAsync(roomId, question, resp);
    }
}
