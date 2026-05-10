package com.aidemo.myaitravelreimbursement.ai;

/**
 * AI 识别 Prompt 模板
 * <p>
 * 集中管理所有用于发票/截图识别的 Prompt，便于维护和热更新。
 * LangChain4j 会将这些 Prompt 与图片内容组合后发送给视觉大模型。
 */
public final class RecognitionPrompts {

    private RecognitionPrompts() {
    }

    /**
     * 发票识别 Prompt
     */
    public static final String INVOICE_PROMPT = """
            请识别这张发票图片，提取以下信息并以JSON格式返回：
            {
                 "expense_type": "费用类型(transport/catering/accommodation/purchase)",
                 "invoice_number": "发票号码",
                 "invoice_date": "开票日期(格式: YYYY-MM-DD)",
                 "total_amount": "价税合计金额(数字)",
                 "seller": "销售方名称",
                 "buyer": "购买方名称",
                 "description": "文件简述",
                 "rewriteFileNameByAi": "根据发票信息生成一个中文文件名，例如'滴滴出行客运服务费_20260405'，不要包含任何文件后缀（如.json、.txt等），只能使用中文、数字和下划线"
             }
            如果无法识别某字段，请返回null。请只返回纯JSON格式，不要添加任何解释以及任何多余信息。
            """;

    /**
     * 截图识别 Prompt
     */
    public static final String SCREENSHOT_PROMPT = """
            请识别这张截图图片，这张图片可能是包含多次消费的截图，需要识别出每次消费以及该次消费的金额，并最后计算所有消费的金额总和，提取以下信息并以JSON格式返回：
            {
                "expense_type": "费用类型(transport/catering/accommodation/purchase，这四个里面选一个)",
                "consumption_date": "消费日期(格式: YYYY-MM-DD)",
                "total_consumption": "消费总额(数字)",
                "consumption_count": "消费次数（有几笔消费）",
                "description": "文件简述",
                 "rewriteFileNameByAi": "根据发票信息生成一个中文文件名，例如'微信支付账单总额_20260405'，不要包含任何文件后缀（如.json、.txt等），只能使用中文、数字和下划线"        
            }
            如果无法识别某字段，请返回null。请只返回纯JSON，不要添加任何解释以及任何多余信息。
            """;
}
