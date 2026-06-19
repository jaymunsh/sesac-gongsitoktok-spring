/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/company/controller/CompanyController.java
 */
package com.gongsitoktok.assistant.company.controller;

import com.gongsitoktok.assistant.company.dto.CompanyCountResponse;
import com.gongsitoktok.assistant.company.dto.CompanyListItemResponse;
import com.gongsitoktok.assistant.company.dto.CompanyResponse;
import com.gongsitoktok.assistant.company.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 기업 공개 조회 컨트롤러 (제작요청 v6 §4-7, §4-8 + count / main 확장).
 *
 * <p>인증 불필요 (SecurityConfig 의 permitAll). 운영자 upsert 는 {@code InternalCompanyController}.</p>
 *
 * <h3>활성 필터링</h3>
 * <p>모든 조회 endpoint 는 {@code isActive=true} 기업만 반환. 비활성 기업은 단건 조회 시 404 로 응답하여
 * 비활성 상태 자체를 외부에 노출하지 않는다.</p>
 */
@Tag(name = "Company", description = "기업 상세 · 목록 · 개수 · 메인페이지 노출 목록 조회")
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @Operation(summary = "활성 기업 개수 조회",
            description = "메인페이지 헤더의 'N대 기업' 표시용. isActive=true 만 카운트.")
    @ApiResponse(responseCode = "200", description = "활성 기업 개수")
    @GetMapping("/count")
    public CompanyCountResponse count() {
        return companyService.countActive();
    }

    @Operation(summary = "메인페이지 노출 기업 목록",
            description = "tb_company_main 에 핀 등록된 기업만 displayOrder ASC. 비활성(isActive=false) 기업은 핀이 있어도 자동 제외.")
    @ApiResponse(responseCode = "200", description = "메인 노출 기업 목록")
    @GetMapping("/main")
    public List<CompanyListItemResponse> mainPageCompanies() {
        return companyService.getMainPageCompanies();
    }

    @Operation(summary = "특정 기업 상세 조회",
            description = "기업 상세 페이지 / 챗봇 응답 출처 카드 클릭 시 호출. 비활성 기업은 404.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "COMPANY_NOT_FOUND (미존재 또는 비활성)")
    @GetMapping("/{corpCode}")
    public CompanyResponse get(
            @Parameter(description = "DART 기업 고유번호", example = "00126380")
            @PathVariable String corpCode
    ) {
        return companyService.getByCorpCode(corpCode);
    }

    @Operation(summary = "기업 목록 조회 (검색 드롭다운 겸용)",
            description = "corpCode 정확 일치 + corpName 부분 일치(대소문자 무시) AND. 둘 다 없으면 활성 전체.")
    @ApiResponse(responseCode = "200", description = "목록 반환")
    @GetMapping
    public List<CompanyListItemResponse> list(
            @Parameter(description = "DART 기업 고유번호 (정확 일치)") @RequestParam(required = false) String corpCode,
            @Parameter(description = "기업명 (부분 일치)") @RequestParam(required = false) String corpName
    ) {
        return companyService.search(corpCode, corpName);
    }
}
