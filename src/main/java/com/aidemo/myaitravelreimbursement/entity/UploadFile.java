package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 上传文件实体
 */
@Data
@TableName("t_upload_file")
public class UploadFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long projectId;

    private Long folderId;

    private String name;

    private String originalName;

    private Long size;

    private String type;

    private String mimeType;

    private String storagePath;

    private Integer status;

    private String remark;

    private Integer confirmed;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
