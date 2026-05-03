package com.aidemo.myaitravelreimbursement.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 报表汇总响应VO
 */
@Data
public class ReportSummaryVO {

    private Long projectId;
    private String projectName;
    private BigDecimal totalAmount;
    private BigDecimal transportAmount;
    private BigDecimal cateringAmount;
    private BigDecimal accommodationAmount;
    private BigDecimal purchaseAmount;
    private Long totalCount;
    private Long transportCount;
    private Long cateringCount;
    private Long accommodationCount;
    private Long purchaseCount;
    private BigDecimal budget;
    private BigDecimal budgetUsed;
    private BigDecimal budgetRemaining;
}
