package com.aidemo.myaitravelreimbursement.controller;

import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.dto.request.BatchRecognizeRequestDTO;
import com.aidemo.myaitravelreimbursement.dto.request.FileUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.BatchRecognizeTaskVO;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import com.aidemo.myaitravelreimbursement.dto.response.RecognitionResultVO;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.AiRecognitionService;
import com.aidemo.myaitravelreimbursement.service.BatchRecognizeService;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 文件管理控制器
 */
@Tag(name = "文件管理", description = "文件上传下载管理接口")
@RestController
@RequestMapping("/projects/{projectId}/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final UploadFileMapper uploadFileMapper;
    private final AiRecognitionService aiRecognitionService;
    private final BatchRecognizeService batchRecognizeService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<FileVO> upload(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "attachment") String type,
            @RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(fileStorageService.upload(projectId, folderId, type, file));
    }

    @Operation(summary = "获取文件列表")
    @GetMapping
    public Result<PageResult<FileVO>> listFiles(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String expenseType) {
        return Result.success(fileStorageService.listFiles(projectId, current, size, type, expenseType));
    }

    @Operation(summary = "获取文件信息")
    @GetMapping("/{fileId}")
    public Result<FileVO> getFile(@PathVariable Long fileId) {
        return Result.success(fileStorageService.getFile(fileId));
    }

    @Operation(summary = "更新文件")
    @PatchMapping("/{fileId}")
    public Result<FileVO> updateFile(@PathVariable Long fileId, @RequestBody FileUpdateDTO dto) {
        return Result.success(fileStorageService.updateFile(fileId, dto));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        fileStorageService.deleteFile(fileId);
        return Result.success();
    }

    @Operation(summary = "单文件AI识别")
    @PostMapping("/{fileId}/recognize")
    public Result<RecognitionResultVO> recognize(@PathVariable Long fileId) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            return Result.error(ErrorCode.FILE_NOT_FOUND);
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, file.getUserId())) {
            return Result.error(ErrorCode.FORBIDDEN);
        }
        String type = file.getType() != null ? file.getType() : "invoice";
        return Result.success(aiRecognitionService.recognize(fileId, type));
    }

    @Operation(summary = "批量识别（异步任务模式）")
    @PostMapping("/batch/recognize")
    public Result<BatchRecognizeTaskVO> submitBatchRecognize(
            @PathVariable Long projectId,
            @RequestBody BatchRecognizeRequestDTO dto) {
        return Result.success(batchRecognizeService.submitTask(projectId, dto.getFileIds()));
    }

    @Operation(summary = "查询批量识别任务进度")
    @GetMapping("/batch/recognize/{taskId}/progress")
    public Result<BatchRecognizeTaskVO> getTaskProgress(@PathVariable String taskId) {
        return Result.success(batchRecognizeService.getTaskProgress(taskId));
    }

    @Operation(summary = "批量确认")
    @PostMapping("/batch/confirm")
    public Result<List<FileVO>> batchConfirm(
            @PathVariable Long projectId,
            @RequestBody List<Long> fileIds) {
        return Result.success(fileStorageService.batchConfirm(projectId, fileIds));
    }

    @Operation(summary = "取消确认")
    @PatchMapping("/{fileId}/unconfirm")
    public Result<FileVO> unconfirmFile(@PathVariable Long fileId) {
        return Result.success(fileStorageService.unconfirmFile(fileId));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{fileId}/download")
    public void download(@PathVariable Long fileId, HttpServletResponse response) throws IOException {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, file.getUserId())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        byte[] data = fileStorageService.downloadFile(fileId);
        response.setContentType(file.getMimeType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + new String(file.getOriginalName().getBytes("UTF-8"), "ISO-8859-1") + "\"");
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }
}
