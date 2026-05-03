package com.aidemo.myaitravelreimbursement.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置更新请求DTO
 */
@Data
public class SettingsUpdateDTO {

    @Size(max = 100, message = "设置键不能超过100字符")
    private String appName;

    private Boolean autoRecognize;

    private Boolean autoArchive;
}
