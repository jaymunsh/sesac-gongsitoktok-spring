/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/company/repository/CompanyRepository.java
 */
package com.gongsitoktok.assistant.company.repository;

import com.gongsitoktok.assistant.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 기업 리포지토리.
 *
 * <h3>공개 vs 운영자 조회 분리</h3>
 * <ul>
 *     <li>공개 조회({@code /companies/**}, 채팅방 생성) — {@code isActive=true} 만 — {@code *AndIsActiveTrue} suffix 메서드 사용</li>
 *     <li>운영자 조회({@code /internal/**}) — 활성/비활성 무관 — {@link #findByCorpCode(String)} 사용 (비활성 기업의 재활성화 작업 위함)</li>
 * </ul>
 *
 * <h3>nullable param 회피</h3>
 * <p>JPQL 안에서 {@code (:param IS NULL OR ...)} 패턴을 쓰면 PostgreSQL JDBC 가 {@code null} 파라미터를 {@code bytea} 로
 * 추론해 {@code lower(bytea) does not exist} 오류를 일으킨다. 본 리포지토리는 단순 derived query 만 노출하고
 * 동적 분기는 서비스 레이어에서 4 케이스 호출로 처리한다.</p>
 */
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // ===== 운영자 조회 (활성 무관) =====

    /**
     * corpCode 단건 조회 — 활성 무관. 운영자 upsert / 재활성화 처리에서 사용 (§4-14).
     */
    Optional<Company> findByCorpCode(String corpCode);

    // ===== 공개 조회 (활성만) =====

    /**
     * 활성 corpCode 단건 조회 — 공개 endpoint({@code /companies/{corpCode}}, 채팅방 생성) 에서 사용.
     */
    Optional<Company> findByCorpCodeAndIsActiveTrue(String corpCode);

    /**
     * 활성 corpName 부분 일치(대소문자 무시) + 이름순 (§4-8).
     */
    List<Company> findByCorpNameContainingIgnoreCaseAndIsActiveTrueOrderByCorpNameAsc(String corpName);

    /**
     * 활성 전체 조회 — 이름순.
     */
    List<Company> findAllByIsActiveTrueOrderByCorpNameAsc();

    /**
     * 활성 기업 개수 — 메인페이지 'N대 기업' 표시용.
     */
    long countByIsActiveTrue();
}
