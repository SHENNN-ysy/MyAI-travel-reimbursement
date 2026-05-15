package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表明细实体
 */
@Data
@TableName("t_report_item")
public class ReportItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long projectId;

    private LocalDate date;

    private String receiptType; // 票据类型：发票/截图

    private String expenseType; // 消费类型：transport/catering/accommodation/purchase

    private String summary;

    private BigDecimal amount;

    private String remark;

    private Integer hasReceipt;

    private String receiptFile;

    private Long receiptFileId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
