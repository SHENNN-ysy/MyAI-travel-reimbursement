package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 全局设置实体
 */
@Data
@TableName("t_settings")
public class Settings {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String settingKey;

    private String settingValue;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updatedAt;
}
