package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.PageResult;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;

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

    @Override
    public PageResult<ReportItemVO> pageItems(Long projectId, int current, int size, String receiptType) {
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
        ReportItem item = new ReportItem();
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
        if (reportItemMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "报表明细不存在");
        }
        reportItemMapper.deleteById(id);
    }

    @Override
    public ReportSummaryVO getSummary(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }

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
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }

        List<ReportItem> items = reportItemMapper.selectList(
                new LambdaQueryWrapper<ReportItem>()
                        .eq(ReportItem::getProjectId, projectId)
                        .orderByAsc(ReportItem::getDate)
        );

        ReportSummaryVO summary = getSummary(projectId);

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            Sheet summarySheet = workbook.createSheet("汇总");
            createSummarySheet(summarySheet, headerStyle, dataStyle, project, summary);

            Sheet detailSheet = workbook.createSheet("明细");
            createDetailSheet(detailSheet, headerStyle, dataStyle, items);

            Sheet fileSheet = workbook.createSheet("凭证清单");
            createFileSheet(fileSheet, headerStyle, dataStyle, projectId);

            workbook.write(outputStream);
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new BusinessException(ErrorCode.EXPORT_ERROR, "导出失败: " + e.getMessage());
        }
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

    private void createSummarySheet(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle,
                                    Project project, ReportSummaryVO summary) {
        String[] headers = {"项目名称", "出差人", "部门", "出差日期", "总金额", "发票金额", "截图金额", "预算项目", "预算使用"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 4000);
        }

        Row dataRow = sheet.createRow(1);
        String dateRange = (project.getStartDate() != null ? DateUtils.formatDate(project.getStartDate()) : "")
                + " ~ " + (project.getEndDate() != null ? DateUtils.formatDate(project.getEndDate()) : "");
        dataRow.createCell(0).setCellValue(project.getName());
        dataRow.createCell(1).setCellValue(project.getPerson() != null ? project.getPerson() : "");
        dataRow.createCell(2).setCellValue(project.getDepartment() != null ? project.getDepartment() : "");
        dataRow.createCell(3).setCellValue(dateRange);
        dataRow.createCell(4).setCellValue(summary.getTotalAmount() != null ? summary.getTotalAmount().doubleValue() : 0.0);
        dataRow.createCell(5).setCellValue(summary.getTransportAmount() != null ? summary.getTransportAmount().doubleValue() : 0.0);
        dataRow.createCell(6).setCellValue(summary.getCateringAmount() != null ? summary.getCateringAmount().doubleValue() : 0.0);
        dataRow.createCell(7).setCellValue(summary.getBudgetName() != null ? summary.getBudgetName() : "");
        dataRow.createCell(8).setCellValue(summary.getBudgetUsed() != null ? summary.getBudgetUsed().doubleValue() : 0.0);

        for (int i = 0; i <= 8; i++) {
            dataRow.getCell(i).setCellStyle(dataStyle);
        }
    }

    private void createDetailSheet(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle, List<ReportItem> items) {
        String[] headers = {"日期", "票据类型", "消费类型", "票据文件", "摘要", "金额", "备注"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 4500);
        }

        int rowNum = 1;
        for (ReportItem item : items) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(DateUtils.formatDate(item.getDate()));
            row.createCell(1).setCellValue(ExpenseType.getName(item.getReceiptType()));
            row.createCell(2).setCellValue(ExpenseType.getExpenseTypeName(item.getExpenseType()));
            row.createCell(3).setCellValue(item.getReceiptFile() != null ? item.getReceiptFile() : "");
            row.createCell(4).setCellValue(item.getSummary() != null ? item.getSummary() : "");
            row.createCell(5).setCellValue(item.getAmount() != null ? item.getAmount().doubleValue() : 0.0);
            row.createCell(6).setCellValue(item.getRemark() != null ? item.getRemark() : "");
            for (int i = 0; i < 7; i++) {
                row.getCell(i).setCellStyle(dataStyle);
            }
        }
    }

    private void createFileSheet(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle, Long projectId) {
        String[] headers = {"文件名", "类型", "大小", "上传时间", "状态"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        List<UploadFile> files = uploadFileMapper.selectList(
                new LambdaQueryWrapper<UploadFile>().eq(UploadFile::getProjectId, projectId)
        );

        int rowNum = 1;
        for (UploadFile file : files) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(file.getOriginalName());
            row.createCell(1).setCellValue(com.aidemo.myaitravelreimbursement.constant.FileType.getName(file.getType()));
            row.createCell(2).setCellValue(formatSize(file.getSize()));
            row.createCell(3).setCellValue(DateUtils.formatDateTime(file.getCreatedAt()));
            row.createCell(4).setCellValue(com.aidemo.myaitravelreimbursement.constant.FileStatus.getName(file.getStatus()));
            for (int i = 0; i < 5; i++) {
                row.getCell(i).setCellStyle(dataStyle);
            }
        }
    }

    private String formatSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
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
}
