package com.aidemo.myaitravelreimbursement.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 文件更新请求DTO
 * 支持同时更新文件基本信息与AI识别结果字段
 */
@Data
public class FileUpdateDTO {

    // ========== 文件基本信息 ==========

    private String remark;

    private Integer confirmed;

    // ========== AI识别结果字段（发票/截图通用） ==========

    /** 消费类型：transport / catering / accommodation / purchase */
    @Size(max = 50, message = "消费类型不能超过50字符")
    private String expenseType;

    /** 发票号码（发票类型） */
    @Size(max = 100, message = "发票号码不能超过100字符")
    private String invoiceNumber;

    /** 开票日期（发票类型） */
    private LocalDate invoiceDate;

    /** 销售方（发票类型） */
    @Size(max = 200, message = "销售方不能超过200字符")
    private String seller;

    /** 购买方（发票类型） */
    @Size(max = 200, message = "购买方不能超过200字符")
    private String buyer;

    /** 价税合计（发票类型） */
    private BigDecimal totalAmount;

    /** 消费次数/数量（截图类型） */
    @Size(max = 50, message = "消费次数不能超过50字符")
    private String consumptionCount;

    /** 消费日期（截图类型） */
    private LocalDate consumptionDate;

    /** 消费总额（截图类型） */
    private BigDecimal totalConsumption;

    /** 置信度（0.0 ~ 1.0） */
    private BigDecimal confidence;

    /** AI自动生成的文件名 */
    @Size(max = 200, message = "AI文件名不能超过200字符")
    private String aiFilename;

    /** 用户自定义的文件描述/别名 */
    @Size(max = 200, message = "文件描述不能超过200字符")
    private String description;
}
