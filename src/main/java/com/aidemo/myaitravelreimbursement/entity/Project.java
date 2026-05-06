package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报销项目实体
 */
@Data
@TableName("t_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String person;

    private String department;

    private String budget;

    private String remark;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
