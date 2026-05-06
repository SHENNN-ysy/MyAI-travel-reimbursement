package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectCreateDTO;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectDetailVO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectVO;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 项目管理控制器
 */
@Tag(name = "项目管理", description = "报销项目管理接口")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

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
}
