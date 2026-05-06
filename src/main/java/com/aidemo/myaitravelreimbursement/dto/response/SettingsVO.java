package com.aidemo.myaitravelreimbursement.dto.response;

import lombok.Data;

/**
 * 全局设置响应VO
 */
@Data
public class SettingsVO {

    private String appName;
    private Boolean autoRecognize;
    private Boolean autoArchive;
    private Boolean notifications;
    private Integer invoiceMaxSize;
    private Integer screenshotMaxSize;
    private Integer attachmentMaxSize;
}
