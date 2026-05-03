package com.aidemo.myaitravelreimbursement.constant;

/**
 * 文件类型常量
 */
public class FileType {

    public static final String INVOICE = "invoice";
    public static final String SCREENSHOT = "screenshot";
    public static final String ATTACHMENT = "attachment";

    public static String getName(String type) {
        return switch (type) {
            case INVOICE -> "发票";
            case SCREENSHOT -> "截图";
            case ATTACHMENT -> "附件";
            default -> "未知";
        };
    }
}
