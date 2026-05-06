package com.aidemo.myaitravelreimbursement.dto.response;

import com.aidemo.myaitravelreimbursement.constant.ExpenseType;
import com.aidemo.myaitravelreimbursement.entity.ReportItem;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表明细响应VO
 */
@Data
public class ReportItemVO {

    private Long id;
    private Long projectId;
    private LocalDate date;
    private String receiptType;
    private String receiptTypeName;
    private String summary;
    private BigDecimal amount;
    private String remark;
    private Integer hasReceipt;
    private String receiptFile;
    private Long receiptFileId;
    private String receiptFileName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReportItemVO fromEntity(ReportItem item) {
        ReportItemVO vo = new ReportItemVO();
        vo.setId(item.getId());
        vo.setProjectId(item.getProjectId());
        vo.setDate(item.getDate());
        vo.setReceiptType(item.getReceiptType());
        vo.setReceiptTypeName(ExpenseType.getName(item.getReceiptType()));
        vo.setSummary(item.getSummary());
        vo.setAmount(item.getAmount());
        vo.setRemark(item.getRemark());
        vo.setHasReceipt(item.getHasReceipt());
        vo.setReceiptFile(item.getReceiptFile());
        vo.setReceiptFileId(item.getReceiptFileId());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }
}
