package com.aidemo.myaitravelreimbursement.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票识别结果 - LangChain4j Structured Output DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRecognitionResult {

    /**
     * 费用类型: transport / catering / accommodation / purchase
     */
    @JsonProperty("expense_type")
    private String expenseType;

    /**
     * 发票号码
     */
    @JsonProperty("invoice_number")
    private String invoiceNumber;

    /**
     * 开票日期 (格式: YYYY-MM-DD)
     */
    @JsonProperty("invoice_date")
    private LocalDate invoiceDate;

    /**
     * 价税合计金额
     */
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    /**
     * 销售方名称
     */
    private String seller;

    /**
     * 购买方名称
     */
    private String buyer;

    /**
     * 文件简述
     */
    private String description;

    /**
     * AI文件改名
     */
    @JsonProperty("rewriteFileNameByAi")
    private String rewriteFileNameByAi;

    /**
     * 置信度 (0-1)
     */
    @JsonProperty("confidence")
    private BigDecimal confidence;

}
