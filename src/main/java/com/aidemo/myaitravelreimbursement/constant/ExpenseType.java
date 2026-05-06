package com.aidemo.myaitravelreimbursement.constant;

/**
 * 票据类型和消费类型常量
 * - 票据类型：发票 / 截图
 * - 消费类型：transport / catering / accommodation / purchase
 */
public class ExpenseType {

    public static final String INVOICE = "发票";
    public static final String SCREENSHOT = "截图";

    public static final String TRANSPORT = "transport";
    public static final String CATERING = "catering";
    public static final String ACCOMMODATION = "accommodation";
    public static final String PURCHASE = "purchase";

    /**
     * 获取票据类型中文名
     */
    public static String getName(String type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case INVOICE -> "发票";
            case SCREENSHOT -> "截图";
            default -> type;
        };
    }

    /**
     * 获取消费类型中文名
     */
    public static String getExpenseTypeName(String type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case TRANSPORT -> "交通";
            case CATERING -> "餐饮";
            case ACCOMMODATION -> "住宿";
            case PURCHASE -> "采购";
            default -> "其他";
        };
    }
}
