package com.aidemo.myaitravelreimbursement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建项目请求DTO
 */
@Data
public class ProjectCreateDTO {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称不能超过100字符")
    private String name;

    @Size(max = 200, message = "目的地不能超过200字符")
    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 500, message = "出差事由不能超过500字符")
    private String reason;

    @Size(max = 50, message = "出差人不能超过50字符")
    private String person;

    @Size(max = 100, message = "部门不能超过100字符")
    private String department;

    private BigDecimal budget;

    @Size(max = 500, message = "备注不能超过500字符")
    private String remark;
}
