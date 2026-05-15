package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectCreateDTO;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectDetailVO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectVO;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 项目管理控制器
 */
@Tag(name = "项目管理", description = "报销项目管理接口")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    @Value("${storage.base-path}")
    private String storageBasePath;

    @Operation(summary = "创建项目")
    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectCreateDTO dto) {
        return Result.success(projectService.create(dto));
    }

    @Operation(summary = "分页查询项目列表")
    @GetMapping
    public Result<PageResult<ProjectVO>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("name", name != null ? name : "");
        params.put("status", status);
        return Result.success(projectService.page(current, size, params));
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public Result<ProjectDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(projectService.getDetail(id));
    }

    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    public Result<ProjectVO> update(@PathVariable Long id, @RequestBody ProjectUpdateDTO dto) {
        return Result.success(projectService.update(id, dto));
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success();
    }

    @Operation(summary = "导出报销项目（打包下载）")
    @GetMapping("/{id}/export-package")
    public void exportPackage(@PathVariable Long id, HttpServletResponse response) throws IOException {
        com.aidemo.myaitravelreimbursement.entity.Project project =
                projectMapper.selectById(id);
        if (project == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        File projectDir = new File(storageBasePath,
                project.getName() != null ? project.getName() : "项目");
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String zipFileName = (project.getName() != null ? project.getName() : "项目")
                + "_报销项目_" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(zipFileName, StandardCharsets.UTF_8) + "\"");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            zipDirectory(projectDir, "", zos);
        }

        projectService.markAsProcessed(id);
    }

    private void zipDirectory(File dir, String baseName, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = baseName.isEmpty() ? file.getName() : baseName + "/" + file.getName();
            if (file.isDirectory()) {
                zipDirectory(file, entryName, zos);
            } else {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }
}
