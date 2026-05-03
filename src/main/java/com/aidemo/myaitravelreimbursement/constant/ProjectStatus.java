package com.aidemo.myaitravelreimbursement.constant;

/**
 * 项目状态常量
 */
public class ProjectStatus {

    public static final int DRAFT = 0;       // 草稿
    public static final int IN_PROGRESS = 1; // 进行中
    public static final int COMPLETED = 2;   // 已完成

    public static String getName(int status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case IN_PROGRESS -> "进行中";
            case COMPLETED -> "已完成";
            default -> "未知";
        };
    }
}
