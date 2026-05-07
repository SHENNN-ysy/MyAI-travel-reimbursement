package com.aidemo.myaitravelreimbursement.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 截图识别结果 - LangChain4j Structured Output DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenshotRecognitionResult {

    /**
     * 费用类型: transport / catering / accommodation / purchase
     */
    @JsonProperty("expense_type")
    private String expenseType;

    /**
     * 消费日期 (格式: YYYY-MM-DD)
     */
    @JsonProperty("consumption_date")
    private LocalDate consumptionDate;

    /**
     * 消费总额
     */
    @JsonProperty("total_consumption")
    private BigDecimal totalConsumption;

    /**
     * 消费次数
     */
    @JsonProperty("consumption_count")
    private String consumptionCount;

    /**
     * 文件简述
     */
    private String description;

    /**
     * AI文件改名
     */
    @JsonProperty("rewriteFileNameByAi")
    private String rewriteFileNameByAi;
}
