package com.aidemo.myaitravelreimbursement.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

/**
 * 更新项目请求DTO
 */
@Data
public class ProjectUpdateDTO {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称不能超过100字符")
    private String name;

    @NotBlank(message = "出差地点不能为空")
    @Size(max = 200, message = "目的地不能超过200字符")
    private String destination;

    @NotBlank(message = "出差人员不能为空")
    @Size(max = 50, message = "出差人不能超过50字符")
    private String person;

    @NotNull(message = "出差开始日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "出差结束日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @NotBlank(message = "预算项目不能为空")
    @Size(max = 100, message = "预算项目不能超过100字符")
    private String budget;

    @Size(max = 500, message = "出差事由不能超过500字符")
    private String reason;

    @Size(max = 100, message = "部门不能超过100字符")
    private String department;

    @Size(max = 500, message = "备注不能超过500字符")
    private String remark;

    private Integer status;
}
