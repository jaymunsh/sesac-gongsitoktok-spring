/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/company/repository/CompanyMainRepository.java
 */
package com.gongsitoktok.assistant.company.repository;

import com.gongsitoktok.assistant.company.entity.CompanyMain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 메인페이지 노출 핀 리포지토리.
 *
 * <p>회사 활성 상태({@code Company.isActive}) 와 핀 존재 여부가 AND 결합되어, 비활성 회사는 핀이 있어도 노출되지 않는다.</p>
 */
public interface CompanyMainRepository extends JpaRepository<CompanyMain, Long> {

    /**
     * 메인페이지 노출 목록 — {@code displayOrder} ASC + {@code Company.isActive=true} 만.
     *
     * <p>{@code JOIN FETCH} 로 N+1 차단.</p>
     */
    @Query("""
            SELECT cm FROM CompanyMain cm
              JOIN FETCH cm.company c
             WHERE c.isActive = true
             ORDER BY cm.displayOrder ASC, c.corpName ASC
            """)
    List<CompanyMain> findAllActiveOrderedWithCompany();

    /**
     * corpCode 로 핀 단건 조회 — 운영자 등록/해제·순서 변경 시 사용.
     */
    @Query("""
            SELECT cm FROM CompanyMain cm
              JOIN FETCH cm.company c
             WHERE c.corpCode = :corpCode
            """)
    Optional<CompanyMain> findByCorpCode(String corpCode);
}
