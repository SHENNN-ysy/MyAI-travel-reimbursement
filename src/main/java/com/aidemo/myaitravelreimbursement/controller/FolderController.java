package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.dto.request.FolderDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FolderVO;
import com.aidemo.myaitravelreimbursement.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件夹管理控制器
 */
@Tag(name = "文件夹管理", description = "项目文件夹管理接口")
@RestController
@RequestMapping("/projects/{projectId}/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @Operation(summary = "获取文件夹树")
    @GetMapping
    public Result<List<FolderVO>> getTree(@PathVariable Long projectId) {
        return Result.success(folderService.getTree(projectId));
    }

    @Operation(summary = "创建文件夹")
    @PostMapping
    public Result<FolderVO> create(@PathVariable Long projectId, @Valid @RequestBody FolderDTO dto) {
        return Result.success(folderService.create(projectId, dto));
    }

    @Operation(summary = "更新文件夹")
    @PutMapping("/{id}")
    public Result<FolderVO> update(@PathVariable Long id, @RequestBody FolderDTO dto) {
        return Result.success(folderService.update(id, dto));
    }

    @Operation(summary = "删除文件夹")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        folderService.delete(id);
        return Result.success();
    }
}
