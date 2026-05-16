package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.dto.request.ReportItemDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportItemVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import com.aidemo.myaitravelreimbursement.service.ReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;

/**
 * T6 & T8: 报表工具（创建报表明细、批量确认、导出Excel）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportTools {

    private final ReportService reportService;
    private final FileStorageService fileStorageService;
    private final ProjectService projectService;
    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;

    @Autowired(required = false)
    @Value("${storage.base-path}")
    private String storageBasePath;

    @Autowired(required = false)
    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Tool("新增一条报表明细。入参：projectName - 项目名称（必填）、date - 报销日期（必填，格式 YYYY-MM-DD）、receiptType - 票据类型（必填，发票/截图）、expenseType - 费用类型（必填，transport/catering/accommodation/purchase）、amount - 金额（必填）")
    public String createReportItem(
            @P("projectName") String projectName,
            @P("date") String date,
            @P("receiptType") String receiptType,
            @P("expenseType") String expenseType,
            @P("amount") String amount) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }

            ReportItemDTO dto = new ReportItemDTO();
            dto.setDate(LocalDate.parse(date));
            dto.setReceiptType(receiptType);
            dto.setExpenseType(expenseType);
            dto.setAmount(new java.math.BigDecimal(amount));
            dto.setHasReceipt(1);

            ReportItemVO item = reportService.createItem(project.getId(), dto);
            return String.format("""
                报表明细创建成功！
                - ID：%d
                - 项目名称：%s
                - 日期：%s
                - 票据类型：%s
                - 费用类型：%s
                - 金额：¥%s
                """,
                    item.getId(),
                    projectName,
                    item.getDate(),
                    item.getReceiptType(),
                    item.getExpenseType(),
                    item.getAmount().stripTrailingZeros().toPlainString());
        } catch (Exception e) {
            log.error("创建报表明细失败", e);
            return "创建报表明细失败: " + e.getMessage();
        }
    }

    @Tool("触发 Excel 导出，生成报销单文件并返回下载地址。入参：projectName - 项目名称（必填）。【重要】此方法会将 Excel 保存到磁盘并返回下载路径，请将此路径告知用户以便下载。")
    public String exportExcel(@P("projectName") String projectName) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }

            String fileName = project.getName()
                    + "_报销单_" + com.aidemo.myaitravelreimbursement.util.DateUtils.formatForFilename(java.time.LocalDate.now())
                    + ".xlsx";

            File destDir = new File(storageBasePath, project.getName());
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            File destFile = new File(destDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                reportService.exportExcel(project.getId(), fos);
            }

            String downloadUrl = appBaseUrl + "/api/v1/projects/" + project.getId() + "/reports/export";
            return String.format("""
                Excel 报销单已生成！
                - 项目名称：%s
                - 文件名：%s
                请直接在浏览器中打开以下链接下载 Excel 文件：
                %s
                """,
                    project.getName(),
                    fileName,
                    downloadUrl);
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            return "导出Excel失败: " + e.getMessage();
        }
    }

    @Tool("获取对应报销项目下的 Excel 文件路径列表。入参：projectName - 项目名称（必填）。返回该报销项目下所有已导出的 Excel 报销单文件的本地路径。【重要】此方法会将 Excel 文件服务器的保存地址暴露，后续回复中不要将此地址告诉用户")
    public String getExcelFilePath(@P("projectName") String projectName) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }

            File projectDir = new File(storageBasePath, project.getName());
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                return "项目【" + projectName + "】下暂无报销 Excel 文件。";
            }

            File[] excelFiles = projectDir.listFiles((dir, name) ->
                    name.endsWith(".xlsx") && name.contains("_报销单_"));

            if (excelFiles == null || excelFiles.length == 0) {
                return "项目【" + projectName + "】下暂无报销 Excel 文件。";
            }

            StringBuilder result = new StringBuilder("项目【" + projectName + "】下的报销 Excel 文件路径：\n");
            for (File file : excelFiles) {
                result.append("- ").append(file.getAbsolutePath()).append("\n");
            }

            return result.toString().trim();
        } catch (Exception e) {
            log.error("获取Excel文件路径失败", e);
            return "获取Excel文件路径失败: " + e.getMessage();
        }
    }

    @Tool("批量确认文件并自动生成报表明细。入参：projectName - 项目名称（必填）")
    public String batchConfirm(@P("projectName") String projectName) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }
            Long projectId = project.getId();

            List<UploadFile> files = uploadFileMapper.selectList(
                    new LambdaQueryWrapper<UploadFile>()
                            .eq(UploadFile::getProjectId, projectId)
                            .eq(UploadFile::getStatus, 2)
                            .eq(UploadFile::getConfirmed, 0)
            );

            if (files.isEmpty()) {
                return "没有待确认的文件（已识别且未确认）。";
            }

            List<Long> fileIds = files.stream().map(UploadFile::getId).collect(Collectors.toList());

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
                createdItems.add(String.format("[%s] ¥%s",
                        file.getOriginalName(),
                        amount.stripTrailingZeros().toPlainString()));
            }

            List<FileVO> confirmedFiles = fileStorageService.batchConfirm(projectId, fileIds);

            return String.format("""
                批量确认并生成报表明细完成！
                - 已确认文件数：%d
                - 已生成报表明细数：%d
                - 汇总总金额：¥%s
                明细：
                %s
                """,
                    confirmedFiles.size(),
                    createdItems.size(),
                    totalAmount.stripTrailingZeros().toPlainString(),
                    String.join("\n", createdItems));
        } catch (Exception e) {
            log.error("批量确认文件失败", e);
            return "批量确认文件失败: " + e.getMessage();
        }
    }
}
