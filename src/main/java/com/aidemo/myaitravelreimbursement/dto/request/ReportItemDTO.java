package com.aidemo.myaitravelreimbursement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报表明细请求DTO
 */
@Data
public class ReportItemDTO {

    @NotNull(message = "报销日期不能为空")
    private LocalDate date;

    @NotNull(message = "凭证类型不能为空")
    @Size(max = 50, message = "凭证类型不能超过50字符")
    private String receiptType;

    @Size(max = 500, message = "摘要不能超过500字符")
    private String summary;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    @Size(max = 500, message = "备注不能超过500字符")
    private String remark;

    private Integer hasReceipt;

    private Long receiptFileId;
}
