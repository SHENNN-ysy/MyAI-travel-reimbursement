package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.dto.request.ReportItemDTO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportItemVO;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import com.aidemo.myaitravelreimbursement.service.ReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * T6 & T7 & T8: 报表工具（创建报表明细、智能填充、导出Excel）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportTools {

    private final ReportService reportService;
    private final FileStorageService fileStorageService;
    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;

    @Tool("新增一条报表明细。入参：projectId - 项目ID（必填）、date - 报销日期（必填，格式 YYYY-MM-DD）、receiptType - 票据类型（必填，发票/截图）、expenseType - 费用类型（必填，transport/catering/accommodation/purchase）、amount - 金额（必填）")
    public String createReportItem(
            @P("projectId") Long projectId,
            @P("date") String date,
            @P("receiptType") String receiptType,
            @P("expenseType") String expenseType,
            @P("amount") String amount) {
        try {
            ReportItemDTO dto = new ReportItemDTO();
            dto.setDate(LocalDate.parse(date));
            dto.setReceiptType(receiptType);
            dto.setExpenseType(expenseType);
            dto.setAmount(new java.math.BigDecimal(amount));
            dto.setHasReceipt(1);

            ReportItemVO item = reportService.createItem(projectId, dto);
            return String.format("""
                报表明细创建成功！
                - ID：%d
                - 日期：%s
                - 票据类型：%s
                - 费用类型：%s
                - 金额：¥%s
                """,
                    item.getId(),
                    item.getDate(),
                    item.getReceiptType(),
                    item.getExpenseType(),
                    item.getAmount().stripTrailingZeros().toPlainString());
        } catch (Exception e) {
            log.error("创建报表明细失败", e);
            return "创建报表明细失败: " + e.getMessage();
        }
    }

    @Tool("根据项目内所有已识别的文件，批量生成报表明细（一键智能填充）。入参：projectId - 项目ID（必填）、confirm - 是否确认生成（true=确认生成，false=仅预览）")
    public String autoFillReport(@P("projectId") Long projectId, @P("confirm") Boolean confirm) {
        try {
            List<UploadFile> files = uploadFileMapper.selectList(
                    new LambdaQueryWrapper<UploadFile>()
                            .eq(UploadFile::getProjectId, projectId)
                            .eq(UploadFile::getStatus, 2)
            );

            if (files.isEmpty()) {
                return "没有已识别完成的文件，无法生成报表明细。请先对文件进行识别。";
            }

            List<String> createdItems = new ArrayList<>();
            java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;

            for (UploadFile file : files) {
                RecognitionResult result = recognitionResultMapper.selectOne(
                        new LambdaQueryWrapper<RecognitionResult>()
                                .eq(RecognitionResult::getFileId, file.getId())
                                .orderByDesc(RecognitionResult::getCreatedAt)
                                .last("LIMIT 1")
                );

                if (result == null) continue;

                String receiptType = "invoice".equals(file.getType()) ? "发票" : "截图";
                String expenseType = result.getExpenseType() != null ? result.getExpenseType() : "transport";
                LocalDate date = result.getInvoiceDate() != null
                        ? result.getInvoiceDate()
                        : (result.getConsumptionDate() != null ? result.getConsumptionDate() : LocalDate.now());
                java.math.BigDecimal amount = result.getTotalAmount() != null
                        ? result.getTotalAmount()
                        : result.getTotalConsumption();
                if (amount == null) amount = java.math.BigDecimal.ZERO;
                totalAmount = totalAmount.add(amount);

                if (Boolean.TRUE.equals(confirm)) {
                    ReportItemDTO dto = new ReportItemDTO();
                    dto.setDate(date);
                    dto.setReceiptType(receiptType);
                    dto.setExpenseType(expenseType);
                    dto.setAmount(amount);
                    dto.setHasReceipt(1);
                    dto.setReceiptFileId(file.getId());
                    if (result.getAiFilename() != null) dto.setReceiptFile(result.getAiFilename());
                    else dto.setReceiptFile(file.getOriginalName());
                    if (result.getDescription() != null) dto.setSummary(result.getDescription());

                    reportService.createItem(projectId, dto);
                    createdItems.add(String.format("[%s] ¥%s", file.getOriginalName(),
                            amount.stripTrailingZeros().toPlainString()));
                } else {
                    createdItems.add(String.format("[%s] ¥%s | %s | %s",
                            file.getOriginalName(),
                            amount.stripTrailingZeros().toPlainString(),
                            expenseType,
                            date));
                }
            }

            if (Boolean.TRUE.equals(confirm)) {
                return String.format("""
                    智能填充完成！已生成 %d 条报表明细。
                    汇总：总金额 ¥%s
                    明细：
                    %s
                    """, createdItems.size(), totalAmount.stripTrailingZeros().toPlainString(),
                        String.join("\n", createdItems));
            } else {
                return String.format("""
                    【预览模式】即将生成 %d 条报表明细。
                    预计总金额：¥%s
                    明细：
                    %s
                    确认生成请回复"确认"。
                    """, createdItems.size(), totalAmount.stripTrailingZeros().toPlainString(),
                        String.join("\n", createdItems));
            }
        } catch (Exception e) {
            log.error("智能填充失败", e);
            return "智能填充失败: " + e.getMessage();
        }
    }

    @Tool("触发 Excel 导出，生成报销单文件。入参：projectId - 项目ID（必填）")
    public String exportExcel(@P("projectId") Long projectId) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            reportService.exportExcel(projectId, outputStream);
            return String.format("""
                Excel 报销单已生成并保存！
                项目ID：%d
                """, projectId);
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            return "导出Excel失败: " + e.getMessage();
        }
    }
}
