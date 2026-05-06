package com.aidemo.myaitravelreimbursement.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
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
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @NotNull(message = "凭证类型不能为空")
    @Size(max = 50, message = "凭证类型不能超过50字符")
    private String receiptType; // 票据类型: 发票/截图

    @NotNull(message = "消费类型不能为空")
    @Size(max = 50, message = "消费类型不能超过50字符")
    private String expenseType; // 消费类型: transport/catering/accommodation/purchase

    @NotNull(message = "是否有票据不能为空")
    private Integer hasReceipt;

    @NotBlank(message = "票据文件不能为空")
    @Size(max = 200, message = "票据文件不能超过200字符")
    private String receiptFile;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    @Size(max = 500, message = "摘要不能超过500字符")
    private String summary;

    @Size(max = 500, message = "备注不能超过500字符")
    private String remark;

//    private Integer hasReceipt;
//
    private Long receiptFileId;
}
