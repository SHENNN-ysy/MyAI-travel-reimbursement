package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.dto.response.RecognitionResultVO;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.AiRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI 识别控制器
 */
@Tag(name = "AI识别", description = "AI图像识别接口")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {
 
    private final AiRecognitionService aiRecognitionService;
    private final UploadFileMapper uploadFileMapper;

    @Operation(summary = "自动识别文件")
    @PostMapping("/recognize/auto")
    public Result<RecognitionResultVO> recognizeAuto(
            @RequestParam Long fileId,
            @RequestParam(defaultValue = "invoice") String type) {
        Long userId = UserContext.getUserId();
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null || !userId.equals(file.getUserId())) {
            throw new com.aidemo.myaitravelreimbursement.common.BusinessException(
                    com.aidemo.myaitravelreimbursement.common.ErrorCode.FORBIDDEN, "无权识别该文件");
        }
        return Result.success(aiRecognitionService.recognize(fileId, type));
    }
}
