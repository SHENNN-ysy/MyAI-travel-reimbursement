package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.dto.request.SettingsUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.SettingsVO;
import com.aidemo.myaitravelreimbursement.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 全局设置控制器
 */
@Tag(name = "全局设置", description = "应用设置接口")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @Operation(summary = "获取全局设置")
    @GetMapping
    public Result<SettingsVO> getSettings() {
        return Result.success(settingsService.getSettings());
    }

    @Operation(summary = "更新全局设置")
    @PutMapping
    public Result<SettingsVO> updateSettings(@RequestBody SettingsUpdateDTO dto) {
        return Result.success(settingsService.updateSettings(dto));
    }
}
