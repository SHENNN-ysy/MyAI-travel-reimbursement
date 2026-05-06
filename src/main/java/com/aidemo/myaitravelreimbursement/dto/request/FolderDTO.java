package com.aidemo.myaitravelreimbursement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件夹请求DTO
 */
@Data
public class FolderDTO {

    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 100, message = "文件夹名称不能超过100字符")
    private String name;

    private String type;

    private Long parentId;

    private Integer sortOrder;
}
