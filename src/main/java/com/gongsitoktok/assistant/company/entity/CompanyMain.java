/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/company/entity/CompanyMain.java
 */
package com.gongsitoktok.assistant.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 메인페이지 노출 기업 핀(pin) 엔티티 — {@code tb_company_main}.
 *
 * <h3>설계 결정</h3>
 * <ul>
 *     <li>{@code tb_company} 에 {@code isMainPage} 같은 컬럼을 추가하지 않고 별도 테이블로 분리한 이유는,
 *         메인페이지 표시 정책(순서, 향후 그룹/캠페인 메타 등) 이 회사 마스터 정보와 라이프사이클이 다르기 때문.</li>
 *     <li>한 회사 = 한 메인 항목 ({@code company_seq} 유니크). 회사가 두 번 노출되는 케이스 사전 차단.</li>
 *     <li>{@link #displayOrder} 가 정렬 키. 작을수록 먼저 노출. 운영자가 직접 관리.</li>
 *     <li>{@code Company.isActive=false} 인 회사는 본 핀이 있어도 {@code CompanyMainRepository} 조회에서 자동 제외.</li>
 * </ul>
 */
@Entity
@Table(
        name = "tb_company_main",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_main_company", columnNames = "company_seq")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyMain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "main_seq")
    private Long mainSeq;

    /**
     * FK → {@link Company#getCompanySeq()}. 한 회사당 한 row 만 허용 (unique).
     * <p>{@code FetchType.LAZY} — 본 row 조회 시점에 회사까지 즉시 로드할 필요가 없는 케이스를 위함.
     * 메인페이지 목록 조회 시에는 {@code JOIN FETCH} 로 명시 로드.</p>
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_seq", referencedColumnName = "company_seq", nullable = false, unique = true,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_company_main_company"))
    private Company company;

    /**
     * 표시 순서 (ASC). 작을수록 먼저 노출. 운영자가 임의로 관리하며 시스템이 자동 채번하지 않음.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 정적 팩토리 — 신규 핀 등록.
     */
    public static CompanyMain pin(Company company, Integer displayOrder) {
        CompanyMain m = new CompanyMain();
        m.company = company;
        m.displayOrder = displayOrder;
        return m;
    }

    /**
     * displayOrder 만 변경 (기존 핀의 순서 조정).
     */
    public void changeOrder(Integer newOrder) {
        this.displayOrder = newOrder;
    }
}
