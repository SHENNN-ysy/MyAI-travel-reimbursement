package com.aidemo.myaitravelreimbursement.common;

/**
 * 统一错误码枚举
 */
public enum ErrorCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 参数错误
    BAD_REQUEST(400, "参数错误"),

    // 未认证
    UNAUTHORIZED(401, "未登录或登录已过期"),

    // 无权限
    FORBIDDEN(403, "无权限访问"),

    // 资源不存在
    NOT_FOUND(404, "资源不存在"),

    // 业务错误 (5xx)
    BUSINESS_ERROR(500, "业务处理异常"),

    // 文件相关
    FILE_UPLOAD_ERROR(501, "文件上传失败"),
    FILE_NOT_FOUND(502, "文件不存在"),
    FILE_TYPE_NOT_ALLOWED(503, "文件类型不允许"),
    FILE_SIZE_EXCEEDED(504, "文件大小超出限制"),

    // 数据库相关
    DATA_NOT_FOUND(601, "数据不存在"),
    DATA_DUPLICATE(602, "数据重复"),

    // AI 识别相关
    AI_RECOGNITION_FAILED(701, "AI识别失败"),
    AI_API_ERROR(702, "AI服务调用失败"),

    // Excel 导出相关
    EXPORT_ERROR(801, "导出失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
