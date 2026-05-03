package com.aidemo.myaitravelreimbursement.dto.response;

import com.aidemo.myaitravelreimbursement.constant.ExpenseType;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI识别结果响应VO
 */
@Data
public class RecognitionResultVO {

    private Long id;
    private Long projectId;
    private Long fileId;
    private String type;
    private String expenseType;
    private String expenseTypeName;
    private String aiFilename;
    private String description;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private String seller;
    private String buyer;
    private String consumptionCount;
    private LocalDate consumptionDate;
    private BigDecimal totalConsumption;
    private BigDecimal confidence;
    private BigDecimal confidencePercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RecognitionResultVO fromEntity(RecognitionResult result) {
        if (result == null) return null;
        RecognitionResultVO vo = new RecognitionResultVO();
        vo.setId(result.getId());
        vo.setProjectId(result.getProjectId());
        vo.setFileId(result.getFileId());
        vo.setType(result.getType());
        vo.setExpenseType(result.getExpenseType());
        vo.setExpenseTypeName(ExpenseType.getName(result.getExpenseType()));
        vo.setAiFilename(result.getAiFilename());
        vo.setDescription(result.getDescription());
        vo.setInvoiceNumber(result.getInvoiceNumber());
        vo.setInvoiceDate(result.getInvoiceDate());
        vo.setTotalAmount(result.getTotalAmount());
        vo.setSeller(result.getSeller());
        vo.setBuyer(result.getBuyer());
        vo.setConsumptionCount(result.getConsumptionCount());
        vo.setConsumptionDate(result.getConsumptionDate());
        vo.setTotalConsumption(result.getTotalConsumption());
        vo.setConfidence(result.getConfidence());
        vo.setConfidencePercent(result.getConfidence() != null
                ? result.getConfidence().multiply(BigDecimal.valueOf(100)) : null);
        vo.setCreatedAt(result.getCreatedAt());
        vo.setUpdatedAt(result.getUpdatedAt());
        return vo;
    }
}
