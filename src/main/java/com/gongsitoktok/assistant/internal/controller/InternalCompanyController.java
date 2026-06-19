/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/internal/controller/InternalCompanyController.java
 */
package com.gongsitoktok.assistant.internal.controller;

import com.gongsitoktok.assistant.company.dto.CompanyResponse;
import com.gongsitoktok.assistant.company.entity.Company;
import com.gongsitoktok.assistant.company.entity.CompanyMain;
import com.gongsitoktok.assistant.company.repository.CompanyMainRepository;
import com.gongsitoktok.assistant.company.repository.CompanyRepository;
import com.gongsitoktok.assistant.global.error.ErrorCode;
import com.gongsitoktok.assistant.global.error.exception.BusinessException;
import com.gongsitoktok.assistant.global.error.exception.CompanyNotFoundException;
import com.gongsitoktok.assistant.internal.dto.InternalCompanyMainPinRequest;
import com.gongsitoktok.assistant.internal.dto.InternalCompanyPatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 운영자용 기업 정보 관리 컨트롤러 (제작요청 v6 §4-14 + 메인 노출 핀 관리).
 *
 * <h3>⚠️ v6 한정: 인증 없음</h3>
 * <p>본 endpoint 는 SecurityConfig 에서 임시 permitAll 처리되어 있다. 운영 배포 시 반드시 다음 중 하나로 보호:</p>
 * <ol>
 *     <li>endpoint 자체 비활성화 ({@code @ConditionalOnProperty})</li>
 *     <li>{@code 127.0.0.1} 바인딩 + SSH 터널</li>
 *     <li>LB/WAF IP 화이트리스트</li>
 * </ol>
 * <p>자세한 옵션 비교는 {@code archive/later.md #1}. 가짜 기업 대량 등록 사고 방지 최우선.</p>
 *
 * <h3>upsert 동작 (PATCH {@code /companies/{corpCode}})</h3>
 * <ul>
 *     <li>해당 {@code corpCode} 행이 있으면 — 전송된 필드만 부분 갱신 → HTTP 200. 활성/비활성 무관 조회 (비활성 기업 재활성화 가능).</li>
 *     <li>없으면 — 신규 row 생성 ({@code corpName} 필수, {@code isActive} 미지정 시 {@code true}) → HTTP 201.</li>
 * </ul>
 *
 * <h3>메인 노출 핀 관리</h3>
 * <ul>
 *     <li>{@code POST /companies/main} — upsert (이미 핀이 있으면 displayOrder 만 변경)</li>
 *     <li>{@code POST /companies/main/{corpCode}/remove} — 핀 해제 (CORS 정책상 DELETE 미사용)</li>
 * </ul>
 */
@Tag(name = "Internal", description = "⚠️ 운영자 전용 — 외부 노출 금지")
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalCompanyController {

    private final CompanyRepository companyRepository;
    private final CompanyMainRepository companyMainRepository;

    @Operation(summary = "기업 정보 upsert (운영자 전용)",
            description = "신규 생성 시 corpName 필수. 기존 행은 부분 갱신. isActive 토글도 본 endpoint 로 처리.")
    @ApiResponse(responseCode = "200", description = "기존 행 부분 갱신")
    @ApiResponse(responseCode = "201", description = "신규 생성")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (신규 생성 시 corpName 누락)")
    @Transactional
    @PatchMapping("/companies/{corpCode}")
    public ResponseEntity<CompanyResponse> upsert(
            @Parameter(description = "DART 기업 고유번호", example = "00126380")
            @PathVariable String corpCode,
            @Valid @RequestBody InternalCompanyPatchRequest req
    ) {
        return companyRepository.findByCorpCode(corpCode)
                .map(existing -> {
                    existing.patch(req.corpName(), req.logoUrl(), req.summaryContent(), req.isActive());
                    return ResponseEntity.ok(CompanyResponse.from(existing));
                })
                .orElseGet(() -> {
                    if (req.corpName() == null || req.corpName().isBlank()) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                                "신규 기업 생성 시 corpName 은 필수입니다.");
                    }
                    Company created = Company.create(corpCode, req.corpName(), req.logoUrl(), req.summaryContent());
                    if (Boolean.FALSE.equals(req.isActive())) {
                        // 신규 생성 시 isActive=false 로 들어오면 그대로 비활성으로 만든다.
                        created.patch(null, null, null, false);
                    }
                    Company saved = companyRepository.save(created);
                    return ResponseEntity.status(HttpStatus.CREATED).body(CompanyResponse.from(saved));
                });
    }

    @Operation(summary = "메인페이지 노출 핀 등록/갱신 (운영자 전용)",
            description = "tb_company_main 에 corpCode 가 있으면 displayOrder 만 변경, 없으면 신규 등록. 회사가 존재하지 않으면 404.")
    @ApiResponse(responseCode = "200", description = "기존 핀의 displayOrder 변경")
    @ApiResponse(responseCode = "201", description = "신규 핀 등록")
    @ApiResponse(responseCode = "404", description = "COMPANY_NOT_FOUND (대상 회사 미존재)")
    @Transactional
    @PostMapping("/companies/main")
    public ResponseEntity<Map<String, Object>> pinToMain(
            @Valid @RequestBody InternalCompanyMainPinRequest req
    ) {
        return companyMainRepository.findByCorpCode(req.corpCode())
                .map(existing -> {
                    existing.changeOrder(req.displayOrder());
                    return ResponseEntity.ok(toResponseMap(existing));
                })
                .orElseGet(() -> {
                    Company company = companyRepository.findByCorpCode(req.corpCode())
                            .orElseThrow(() -> new CompanyNotFoundException(req.corpCode()));
                    CompanyMain saved = companyMainRepository.save(
                            CompanyMain.pin(company, req.displayOrder()));
                    return ResponseEntity.status(HttpStatus.CREATED).body(toResponseMap(saved));
                });
    }

    @Operation(summary = "메인페이지 노출 핀 해제 (운영자 전용)",
            description = "CORS 정책상 DELETE 미사용 → POST 명령형 endpoint 로 해제. 핀이 없으면 멱등 처리 (204).")
    @ApiResponse(responseCode = "204", description = "해제 완료 (또는 처음부터 핀 없음 — 멱등)")
    @ApiResponse(responseCode = "404", description = "COMPANY_NOT_FOUND")
    @Transactional
    @PostMapping("/companies/main/{corpCode}/remove")
    public ResponseEntity<Void> unpinFromMain(
            @Parameter(description = "DART 기업 고유번호", example = "00126380")
            @PathVariable String corpCode
    ) {
        if (companyRepository.findByCorpCode(corpCode).isEmpty()) {
            throw new CompanyNotFoundException(corpCode);
        }
        companyMainRepository.findByCorpCode(corpCode)
                .ifPresent(companyMainRepository::delete);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toResponseMap(CompanyMain pin) {
        return Map.of(
                "corpCode", pin.getCompany().getCorpCode(),
                "corpName", pin.getCompany().getCorpName(),
                "displayOrder", pin.getDisplayOrder()
        );
    }
}
