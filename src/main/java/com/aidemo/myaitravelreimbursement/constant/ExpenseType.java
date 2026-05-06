package com.aidemo.myaitravelreimbursement.constant;

/**
 * 费用类型常量
 */
public class ExpenseType {

    public static final String TRANSPORT = "transport";
    public static final String CATERING = "catering";
    public static final String ACCOMMODATION = "accommodation";
    public static final String PURCHASE = "purchase";

    public static String getName(String type) {
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
