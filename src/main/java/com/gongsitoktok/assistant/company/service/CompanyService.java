/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/company/service/CompanyService.java
 */
package com.gongsitoktok.assistant.company.service;

import com.gongsitoktok.assistant.company.dto.CompanyCountResponse;
import com.gongsitoktok.assistant.company.dto.CompanyListItemResponse;
import com.gongsitoktok.assistant.company.dto.CompanyResponse;
import com.gongsitoktok.assistant.company.entity.Company;
import com.gongsitoktok.assistant.company.repository.CompanyMainRepository;
import com.gongsitoktok.assistant.company.repository.CompanyRepository;
import com.gongsitoktok.assistant.global.error.exception.CompanyNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 기업 조회 서비스 (제작요청 v6 §4-7, §4-8 + 활성 필터링·count·메인 노출).
 *
 * <h3>활성 필터링</h3>
 * <p>공개 endpoint 의 모든 조회는 {@code isActive=true} 만 반환. 비활성 기업은 단건 조회 시 {@link CompanyNotFoundException}
 * (404) 으로 응답해 비활성 상태 자체를 외부에 노출하지 않음.</p>
 *
 * <h3>{@link #search(String, String)} 분기</h3>
 * <ul>
 *     <li>둘 다 있음 — 활성 corpCode 단건 조회 + corpName 부분 일치 필터 (AND)</li>
 *     <li>corpCode 만 — 활성 단건 조회</li>
 *     <li>corpName 만 — 활성 Containing IgnoreCase 정렬 조회</li>
 *     <li>둘 다 없음 — 활성 전체 조회 (corpName 오름차순)</li>
 * </ul>
 *
 * <p>JPQL 안에 {@code (:param IS NULL OR ...)} 같은 nullable 분기를 두지 않는다 — PostgreSQL JDBC 가 null 파라미터를
 * {@code bytea} 로 추론해 발생하는 {@code lower(bytea) does not exist} 오류 회피.</p>
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMainRepository companyMainRepository;

    /**
     * 활성 단건 조회 — 미존재 또는 비활성 시 {@link CompanyNotFoundException}.
     */
    @Transactional(readOnly = true)
    public CompanyResponse getByCorpCode(String corpCode) {
        Company c = companyRepository.findByCorpCodeAndIsActiveTrue(corpCode)
                .orElseThrow(() -> new CompanyNotFoundException(corpCode));
        return CompanyResponse.from(c);
    }

    /**
     * 4 케이스 분기 검색 — 모두 활성 기업만.
     */
    @Transactional(readOnly = true)
    public List<CompanyListItemResponse> search(String corpCode, String corpName) {
        String code = normalize(corpCode);
        String name = normalize(corpName);

        if (code != null && name != null) {
            String needle = name.toLowerCase(Locale.ROOT);
            return companyRepository.findByCorpCodeAndIsActiveTrue(code)
                    .filter(c -> c.getCorpName().toLowerCase(Locale.ROOT).contains(needle))
                    .map(c -> List.of(CompanyListItemResponse.from(c)))
                    .orElseGet(List::of);
        }
        if (code != null) {
            return companyRepository.findByCorpCodeAndIsActiveTrue(code)
                    .map(c -> List.of(CompanyListItemResponse.from(c)))
                    .orElseGet(List::of);
        }
        if (name != null) {
            return companyRepository.findByCorpNameContainingIgnoreCaseAndIsActiveTrueOrderByCorpNameAsc(name)
                    .stream().map(CompanyListItemResponse::from).toList();
        }
        return companyRepository.findAllByIsActiveTrueOrderByCorpNameAsc()
                .stream().map(CompanyListItemResponse::from).toList();
    }

    /**
     * 활성 기업 총 개수 — 메인페이지 'N대 기업' 표시용.
     */
    @Transactional(readOnly = true)
    public CompanyCountResponse countActive() {
        return new CompanyCountResponse(companyRepository.countByIsActiveTrue());
    }

    /**
     * 메인페이지 노출 기업 목록 — {@code tb_company_main} 핀 등록 + {@code isActive=true} AND, displayOrder ASC.
     */
    @Transactional(readOnly = true)
    public List<CompanyListItemResponse> getMainPageCompanies() {
        return companyMainRepository.findAllActiveOrderedWithCompany().stream()
                .map(cm -> CompanyListItemResponse.from(cm.getCompany()))
                .toList();
    }

    private String normalize(String v) {
        if (v == null) {
            return null;
        }
        String trimmed = v.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
