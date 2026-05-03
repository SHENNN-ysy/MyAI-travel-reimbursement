package com.aidemo.myaitravelreimbursement.constant;

/**
 * 文件识别状态常量
 */
public class FileStatus {

    public static final int PENDING = 0;   // 待识别
    public static final int RECOGNIZING = 1; // 识别中
    public static final int SUCCESS = 2;     // 识别成功
    public static final int FAILED = 3;     // 识别失败

    public static String getName(int status) {
        return switch (status) {
            case PENDING -> "待识别";
            case RECOGNIZING -> "识别中";
            case SUCCESS -> "识别成功";
            case FAILED -> "识别失败";
            default -> "未知";
        };
    }
}
