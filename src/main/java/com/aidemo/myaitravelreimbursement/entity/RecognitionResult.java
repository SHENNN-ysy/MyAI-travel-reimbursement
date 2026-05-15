package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI识别结果实体
 */
@Data
@TableName("t_recognition_result")
public class RecognitionResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long projectId;

    private Long fileId;

    private String type;

    private String expenseType;

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

    private String rawResponse;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
