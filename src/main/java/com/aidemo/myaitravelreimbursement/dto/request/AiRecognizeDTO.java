package com.aidemo.myaitravelreimbursement.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文件识别请求DTO
 */
@Data
public class AiRecognizeDTO {

    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    private String type; // invoice / screenshot
}
