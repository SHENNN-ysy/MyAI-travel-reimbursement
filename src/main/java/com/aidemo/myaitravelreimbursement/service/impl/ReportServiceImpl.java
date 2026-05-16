package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.constant.ExpenseType;
import com.aidemo.myaitravelreimbursement.dto.request.ReportItemDTO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportItemVO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportSummaryVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.entity.ReportItem;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.mapper.ReportItemMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.ReportService;
import com.aidemo.myaitravelreimbursement.util.DateUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 报表服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportItemMapper reportItemMapper;
    private final ProjectMapper projectMapper;
    private final UploadFileMapper uploadFileMapper;

    @Value("${storage.base-path}")
    private String storageBasePath;

    @Override
    public PageResult<ReportItemVO> pageItems(Long projectId, int current, int size, String receiptType) {
        verifyProjectOwnership(projectId);
        Page<ReportItem> page = new Page<>(current, size);
        LambdaQueryWrapper<ReportItem> wrapper = new LambdaQueryWrapper<ReportItem>()
                .eq(ReportItem::getProjectId, projectId)
                .orderByDesc(ReportItem::getDate);

        if (receiptType != null && !receiptType.isEmpty()) {
            wrapper.eq(ReportItem::getReceiptType, receiptType);
        }

        IPage<ReportItem> result = reportItemMapper.selectPage(page, wrapper);

        List<ReportItemVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .toList();

        return PageResult.of(result.getTotal(), current, size, voList);
    }

    @Override
    public List<ReportItemVO> listItems(Long projectId, String receiptType) {
        verifyProjectOwnership(projectId);
        LambdaQueryWrapper<ReportItem> wrapper = new LambdaQueryWrapper<ReportItem>()
                .eq(ReportItem::getProjectId, projectId)
                .orderByDesc(ReportItem::getDate);

        if (receiptType != null && !receiptType.isEmpty()) {
            wrapper.eq(ReportItem::getReceiptType, receiptType);
        }

        return reportItemMapper.selectList(wrapper).stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    @Transactional
    public ReportItemVO createItem(Long projectId, ReportItemDTO dto) {
        Long userId = verifyProjectOwnership(projectId).getUserId();
        ReportItem item = new ReportItem();
        item.setUserId(userId);
        item.setProjectId(projectId);
        item.setDate(dto.getDate());
        item.setReceiptType(dto.getReceiptType());
        item.setExpenseType(dto.getExpenseType());
        item.setSummary(dto.getSummary());
        item.setAmount(dto.getAmount());
        item.setRemark(dto.getRemark());
        item.setHasReceipt(dto.getHasReceipt() != null ? dto.getHasReceipt() : 1);
        item.setReceiptFile(dto.getReceiptFile());
        item.setReceiptFileId(dto.getReceiptFileId());
        reportItemMapper.insert(item);
        return convertToVO(item);
    }

    @Override
    @Transactional
    public ReportItemVO updateItem(Long id, ReportItemDTO dto) {
        ReportItem item = reportItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "报表明细不存在");
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, item.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该报表明细");
        }
        if (dto.getDate() != null) item.setDate(dto.getDate());
        if (dto.getReceiptType() != null) item.setReceiptType(dto.getReceiptType());
        if (dto.getExpenseType() != null) item.setExpenseType(dto.getExpenseType());
        if (dto.getSummary() != null) item.setSummary(dto.getSummary());
        if (dto.getAmount() != null) item.setAmount(dto.getAmount());
        if (dto.getRemark() != null) item.setRemark(dto.getRemark());
        if (dto.getHasReceipt() != null) item.setHasReceipt(dto.getHasReceipt());
        if (dto.getReceiptFile() != null) item.setReceiptFile(dto.getReceiptFile());
        if (dto.getReceiptFileId() != null) item.setReceiptFileId(dto.getReceiptFileId());
        reportItemMapper.updateById(item);
        return convertToVO(item);
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        ReportItem item = reportItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "报表明细不存在");
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, item.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该报表明细");
        }
        reportItemMapper.deleteById(id);
    }

    @Override
    public ReportSummaryVO getSummary(Long projectId) {
        Project project = verifyProjectOwnership(projectId);

        List<ReportItem> items = reportItemMapper.selectList(
                new LambdaQueryWrapper<ReportItem>().eq(ReportItem::getProjectId, projectId)
        );

        ReportSummaryVO vo = new ReportSummaryVO();
        vo.setProjectId(projectId);
        vo.setProjectName(project.getName());
        vo.setBudgetName(project.getBudget());

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal transport = BigDecimal.ZERO;
        BigDecimal catering = BigDecimal.ZERO;
        BigDecimal accommodation = BigDecimal.ZERO;
        BigDecimal purchase = BigDecimal.ZERO;

        for (ReportItem item : items) {
            total = total.add(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
            switch (item.getExpenseType()) {
                case "transport" -> transport = transport.add(item.getAmount());
                case "catering" -> catering = catering.add(item.getAmount());
                case "accommodation" -> accommodation = accommodation.add(item.getAmount());
                case "purchase" -> purchase = purchase.add(item.getAmount());
            }
        }

        vo.setTotalAmount(total);
        vo.setTransportAmount(transport);
        vo.setCateringAmount(catering);
        vo.setAccommodationAmount(accommodation);
        vo.setPurchaseAmount(purchase);
        vo.setTotalCount((long) items.size());
        vo.setTransportCount(items.stream().filter(i -> "transport".equals(i.getExpenseType())).count());
        vo.setCateringCount(items.stream().filter(i -> "catering".equals(i.getExpenseType())).count());
        vo.setAccommodationCount(items.stream().filter(i -> "accommodation".equals(i.getExpenseType())).count());
        vo.setPurchaseCount(items.stream().filter(i -> "purchase".equals(i.getExpenseType())).count());

        if (project.getBudget() != null && total != null) {
            try {
                BigDecimal budgetNum = new BigDecimal(project.getBudget());
                vo.setBudgetUsed(total);
                vo.setBudgetRemaining(budgetNum.subtract(total));
            } catch (NumberFormatException ignored) {
            }
        }

        return vo;
    }

    @Override
    public void exportExcel(Long projectId, OutputStream outputStream) {
        Project project = verifyProjectOwnership(projectId);

        List<ReportItem> items = reportItemMapper.selectList(
                new LambdaQueryWrapper<ReportItem>()
                        .eq(ReportItem::getProjectId, projectId)
                        .orderByAsc(ReportItem::getDate)
        );

        // Sort by expense type: transport > accommodation > purchase > catering
        List<ReportItem> sortedItems = items.stream()
                .sorted(Comparator.comparingInt(item -> {
                    String type = item.getExpenseType() != null ? item.getExpenseType() : "";
                    return switch (type) {
                        case "transport" -> 0;
                        case "accommodation" -> 1;
                        case "purchase" -> 2;
                        case "catering" -> 3;
                        default -> 4;
                    };
                }))
                .toList();

        ReportSummaryVO summary = getSummary(projectId);

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle sectionTitleStyle = createSectionTitleStyle(workbook);

            Sheet sheet = workbook.createSheet("报销单");

            // Row 0: Section title "汇总信息"
            Row section0 = sheet.createRow(0);
            Cell section0Cell = section0.createCell(0);
            section0Cell.setCellValue("【汇总信息】");
            section0Cell.setCellStyle(sectionTitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            // Row 1: Summary headers (8 columns)
            Row summaryHeader = sheet.createRow(1);
            String[] summaryHeaders = {"项目名称", "出差人", "部门", "起始日期", "结束日期", "总天数", "总金额", "出差项目"};
            for (int i = 0; i < summaryHeaders.length; i++) {
                Cell cell = summaryHeader.createCell(i);
                cell.setCellValue(summaryHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            // Row 2: Summary data
            Row summaryData = sheet.createRow(2);
            long totalDays = 0;
            if (project.getStartDate() != null && project.getEndDate() != null) {
                totalDays = ChronoUnit.DAYS.between(project.getStartDate(), project.getEndDate()) + 1;
            }
            summaryData.createCell(0).setCellValue(project.getName() != null ? project.getName() : "");
            summaryData.createCell(1).setCellValue(project.getPerson() != null ? project.getPerson() : "");
            summaryData.createCell(2).setCellValue(project.getDepartment() != null ? project.getDepartment() : "");
            summaryData.createCell(3).setCellValue(project.getStartDate() != null ? DateUtils.formatDate(project.getStartDate()) : "");
            summaryData.createCell(4).setCellValue(project.getEndDate() != null ? DateUtils.formatDate(project.getEndDate()) : "");
            summaryData.createCell(5).setCellValue(totalDays);
            summaryData.createCell(6).setCellValue(summary.getTotalAmount() != null ? summary.getTotalAmount().doubleValue() : 0.0);
            summaryData.createCell(7).setCellValue(summary.getBudgetName() != null ? summary.getBudgetName() : "");
            for (int i = 0; i <= 7; i++) {
                summaryData.getCell(i).setCellStyle(dataStyle);
            }

            // Row 3: blank separator
            sheet.createRow(3);

            // Row 4: Section title "明细信息"
            Row section1 = sheet.createRow(4);
            Cell section1Cell = section1.createCell(0);
            section1Cell.setCellValue("【明细信息】");
            section1Cell.setCellStyle(sectionTitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, 6));

            // Row 5: Detail headers (7 columns)
            Row detailHeader = sheet.createRow(5);
            String[] detailHeaders = {"票据类型", "日期", "消费类型", "票据文件", "摘要", "金额", "备注"};
            for (int i = 0; i < detailHeaders.length; i++) {
                Cell cell = detailHeader.createCell(i);
                cell.setCellValue(detailHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            // Detail data rows start from row 6
            int rowNum = 6;
            for (ReportItem item : sortedItems) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(ExpenseType.getName(item.getReceiptType()));
                row.createCell(1).setCellValue(DateUtils.formatDate(item.getDate()));
                row.createCell(2).setCellValue(ExpenseType.getExpenseTypeName(item.getExpenseType()));
                row.createCell(3).setCellValue(item.getReceiptFile() != null ? item.getReceiptFile() : "");
                row.createCell(4).setCellValue(item.getSummary() != null ? item.getSummary() : "");
                row.createCell(5).setCellValue(item.getAmount() != null ? item.getAmount().doubleValue() : 0.0);
                row.createCell(6).setCellValue(item.getRemark() != null ? item.getRemark() : "");
                for (int i = 0; i < 7; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            // Set column widths: col0=project name wider, col3=receipt file widest
            sheet.setColumnWidth(0, 6000);
            sheet.setColumnWidth(1, 4000);
            sheet.setColumnWidth(2, 3500);
            sheet.setColumnWidth(3, 8000);
            sheet.setColumnWidth(4, 5000);
            sheet.setColumnWidth(5, 4000);
            sheet.setColumnWidth(6, 4500);
            sheet.setColumnWidth(7, 4500);

            workbook.write(outputStream);

            // Save a copy to disk under project-specific directory
            String fileName = (project.getName() != null ? project.getName() : "项目")
                    + "_报销单_" + ".xlsx";
            File destDir = new File(storageBasePath,  (project.getName() != null ? project.getName() : "项目"));
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            File destFile = new File(destDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                workbook.write(fos);
                log.info("Excel已保存到: {}", destFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new BusinessException(ErrorCode.EXPORT_ERROR, "导出失败: " + e.getMessage());
        }
    }

    private CellStyle createSectionTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private ReportItemVO convertToVO(ReportItem item) {
        ReportItemVO vo = ReportItemVO.fromEntity(item);
        if (item.getReceiptFileId() != null) {
            UploadFile file = uploadFileMapper.selectById(item.getReceiptFileId());
            if (file != null) {
                vo.setReceiptFileName(file.getOriginalName());
            }
        } else if (item.getReceiptFile() != null) {
            vo.setReceiptFileName(item.getReceiptFile());
        }
        return vo;
    }

    private Project verifyProjectOwnership(Long projectId) {
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(userId, project.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
        return project;
    }
}
