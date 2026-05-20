package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.config.StorageConfig;
import com.aidemo.myaitravelreimbursement.dto.request.ReportItemDTO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportItemVO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportSummaryVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 报表管理控制器
 */
@Tag(name = "报表管理", description = "报表明细管理接口")
@RestController
@RequestMapping("/projects/{projectId}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ProjectMapper projectMapper;
    private final StorageConfig storageConfig;

    @Operation(summary = "获取报表明细列表（分页）")
    @GetMapping("/items")
    public Result<PageResult<ReportItemVO>> listItems(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String receiptType) {
        return Result.success(reportService.pageItems(projectId, current, size, receiptType));
    }

    @Operation(summary = "获取报表明细列表（全部，不分页）")
    @GetMapping("/items/all")
    public Result<List<ReportItemVO>> listAllItems(
            @PathVariable Long projectId,
            @RequestParam(required = false) String receiptType) {
        return Result.success(reportService.listItems(projectId, receiptType));
    }

    @Operation(summary = "添加报表明细")
    @PostMapping("/items")
    public Result<ReportItemVO> createItem(
            @PathVariable Long projectId,
            @Valid @RequestBody ReportItemDTO dto) {
        return Result.success(reportService.createItem(projectId, dto));
    }

    @Operation(summary = "更新报表明细")
    @PutMapping("/items/{itemId}")
    public Result<ReportItemVO> updateItem(
            @PathVariable Long itemId,
            @RequestBody ReportItemDTO dto) {
        return Result.success(reportService.updateItem(itemId, dto));
    }

    @Operation(summary = "删除报表明细")
    @DeleteMapping("/items/{itemId}")
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        reportService.deleteItem(itemId);
        return Result.success();
    }

    @Operation(summary = "获取项目汇总")
    @GetMapping("/summary")
    public Result<ReportSummaryVO> getSummary(@PathVariable Long projectId) {
        return Result.success(reportService.getSummary(projectId));
    }

    @Operation(summary = "导出Excel报销单")
    @GetMapping("/export")
    public void exportExcel(@PathVariable Long projectId,
                            HttpServletResponse response) throws IOException {
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!Objects.equals(userId, project.getUserId())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String fileName = project.getName()
                + "_报销单.xlsx";
        File destFile = new File(new File(storageConfig.getBasePath(), project.getUserId() + "/" + project.getName()), fileName);

        if (!destFile.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setContentLengthLong(destFile.length());

        try (FileInputStream fis = new FileInputStream(destFile)) {
            fis.transferTo(response.getOutputStream());
        }
    }
}
