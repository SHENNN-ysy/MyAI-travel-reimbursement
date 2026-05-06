package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.config.StorageConfig;
import com.aidemo.myaitravelreimbursement.constant.FileStatus;
import com.aidemo.myaitravelreimbursement.dto.request.FileUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.ReportItem;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.ReportItemMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aidemo.myaitravelreimbursement.util.FileUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件存储服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;
    private final ReportItemMapper reportItemMapper;
    private final StorageConfig storageConfig;

    @Override
    @Transactional
    public FileVO upload(Long projectId, Long folderId, String type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (!FileUtils.isExtensionAllowed(originalName, storageConfig.getAllowedExtensions())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "不支持的文件类型: " + FileUtils.getExtension(originalName));
        }

        String storageName = UUID.randomUUID().toString().replace("-", "") + "."
                + FileUtils.getExtension(originalName);

        String folderPath = projectId + "/" + (folderId != null ? folderId : "0");
        String relativePath = folderPath + "/" + storageName;
        String fullPath = storageConfig.getBasePath() + "/" + relativePath;

        FileUtils.ensureDirectoryExists(new java.io.File(fullPath).getParentFile().getAbsolutePath());
        Files.copy(file.getInputStream(), Paths.get(fullPath), StandardCopyOption.REPLACE_EXISTING);

        UploadFile uploadFile = new UploadFile();
        uploadFile.setProjectId(projectId);
        uploadFile.setFolderId(folderId != null ? folderId : 0L);
        uploadFile.setName(storageName);
        uploadFile.setOriginalName(originalName);
        uploadFile.setSize(file.getSize());
        uploadFile.setType(type);
        uploadFile.setMimeType(FileUtils.getMimeType(originalName));
        uploadFile.setStoragePath(relativePath);
        uploadFile.setStatus(FileStatus.PENDING);
        uploadFile.setConfirmed(0);
        uploadFileMapper.insert(uploadFile);

        // 上传成功后立即创建一条空的 RecognitionResult 记录
        // 后续 AI 识别时更新该记录，未识别前各字段均为 null
        RecognitionResult emptyResult = new RecognitionResult();
        emptyResult.setProjectId(projectId);
        emptyResult.setFileId(uploadFile.getId());
        emptyResult.setType(type);
        recognitionResultMapper.insert(emptyResult);

        return FileVO.fromEntity(uploadFile, emptyResult);
    }

    @Override
    public FileVO getFile(Long fileId) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        // 查询该文件的 AI 识别结果
        RecognitionResult result = recognitionResultMapper.selectOne(
                new LambdaQueryWrapper<RecognitionResult>()
                        .eq(RecognitionResult::getFileId, fileId)
                        .orderByDesc(RecognitionResult::getCreatedAt)
                        .last("LIMIT 1")
        );
        return FileVO.fromEntity(file, result);
    }

    @Override
    public com.aidemo.myaitravelreimbursement.common.PageResult<FileVO> listFiles(Long projectId, int current, int size, String type, String expenseType) {
        Page<UploadFile> page = new Page<>(current, size);
        LambdaQueryWrapper<UploadFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UploadFile::getProjectId, projectId);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(UploadFile::getType, type);
        }

        wrapper.orderByDesc(UploadFile::getCreatedAt);
        IPage<UploadFile> result = uploadFileMapper.selectPage(page, wrapper);

        List<FileVO> voList = result.getRecords().stream()
                .map(file -> {
                    RecognitionResult recognitionResult = recognitionResultMapper.selectOne(
                            new LambdaQueryWrapper<RecognitionResult>()
                                    .eq(RecognitionResult::getFileId, file.getId())
                                    .orderByDesc(RecognitionResult::getCreatedAt)
                                    .last("LIMIT 1")
                    );
                    if (expenseType != null && !expenseType.isEmpty()) {
                        if (recognitionResult == null || !expenseType.equals(recognitionResult.getExpenseType())) {
                            return null;
                        }
                    }
                    return FileVO.fromEntity(file, recognitionResult);
                })
                .filter(vo -> vo != null)
                .toList();

        return PageResult.of(result.getTotal(), current, size, voList);
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        String fullPath = storageConfig.getBasePath() + "/" + file.getStoragePath();
        java.io.File physicalFile = new java.io.File(fullPath);
        if (physicalFile.exists()) {
            physicalFile.delete();
        }

        // 删除识别结果
        recognitionResultMapper.delete(
                new LambdaQueryWrapper<RecognitionResult>().eq(RecognitionResult::getFileId, fileId)
        );

        uploadFileMapper.deleteById(fileId);
    }

    @Override
    @Transactional
    public FileVO updateFile(Long fileId, FileUpdateDTO dto) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        // 1. 更新文件基本信息
        if (dto.getRemark() != null) {
            file.setRemark(dto.getRemark());
        }
        if (dto.getConfirmed() != null) {
            file.setConfirmed(dto.getConfirmed());
        }
        uploadFileMapper.updateById(file);

        // 2. 更新或新增识别结果记录
        RecognitionResult result = recognitionResultMapper.selectOne(
                new LambdaQueryWrapper<RecognitionResult>()
                        .eq(RecognitionResult::getFileId, fileId)
                        .orderByDesc(RecognitionResult::getCreatedAt)
                        .last("LIMIT 1")
        );

        if (result == null) {
            // 无识别结果记录，则新建
            result = new RecognitionResult();
            result.setFileId(fileId);
            result.setProjectId(file.getProjectId());
            fillRecognitionResult(result, dto);
            recognitionResultMapper.insert(result);
        } else {
            // 更新已有记录
            fillRecognitionResult(result, dto);
            recognitionResultMapper.updateById(result);
        }

        return FileVO.fromEntity(file, result);
    }

    /**
     * 将 DTO 中的识别结果字段填充到实体
     */
    private void fillRecognitionResult(RecognitionResult result, FileUpdateDTO dto) {
        if (dto.getExpenseType() != null) {
            result.setExpenseType(dto.getExpenseType());
        }
        if (dto.getInvoiceNumber() != null) {
            result.setInvoiceNumber(dto.getInvoiceNumber());
        }
        if (dto.getInvoiceDate() != null) {
            result.setInvoiceDate(dto.getInvoiceDate());
        }
        if (dto.getSeller() != null) {
            result.setSeller(dto.getSeller());
        }
        if (dto.getBuyer() != null) {
            result.setBuyer(dto.getBuyer());
        }
        if (dto.getTotalAmount() != null) {
            result.setTotalAmount(dto.getTotalAmount());
        }
        if (dto.getConsumptionCount() != null) {
            result.setConsumptionCount(dto.getConsumptionCount());
        }
        if (dto.getConsumptionDate() != null) {
            result.setConsumptionDate(dto.getConsumptionDate());
        }
        if (dto.getTotalConsumption() != null) {
            result.setTotalConsumption(dto.getTotalConsumption());
        }
        if (dto.getConfidence() != null) {
            result.setConfidence(dto.getConfidence());
        }
        if (dto.getAiFilename() != null) {
            result.setAiFilename(dto.getAiFilename());
        }
        if (dto.getDescription() != null) {
            result.setDescription(dto.getDescription());
        }
    }

    @Override
    @Transactional
    public List<FileVO> batchConfirm(Long projectId, List<Long> fileIds) {
        List<FileVO> results = new ArrayList<>();
        for (Long fileId : fileIds) {
            UploadFile file = uploadFileMapper.selectById(fileId);
            if (file != null && file.getProjectId().equals(projectId)) {
                file.setConfirmed(1);
                uploadFileMapper.updateById(file);

                // 查询识别结果
                RecognitionResult recognitionResult = recognitionResultMapper.selectOne(
                        new LambdaQueryWrapper<RecognitionResult>()
                                .eq(RecognitionResult::getFileId, fileId)
                                .orderByDesc(RecognitionResult::getCreatedAt)
                                .last("LIMIT 1")
                );

                // 根据识别结果自动创建或更新报表明细
                createOrUpdateReportItem(file, recognitionResult);

                results.add(FileVO.fromEntity(file, recognitionResult));
            }
        }
        return results;
    }

    /**
     * 根据文件确认状态自动创建或更新报表明细
     * 字段映射关系：
     * - date: invoiceDate(发票) 或 consumptionDate(截图)
     * - receiptType: expenseType
     * - hasReceipt: 固定为1
     * - receiptFile: aiFilename
     * - amount: totalAmount(发票) 或 totalConsumption(截图)
     * - summary: description
     * - remark: file.remark
     * - receiptFileId: file.id
     */
    private void createOrUpdateReportItem(UploadFile file, RecognitionResult result) {
        // 查找是否已存在该文件的报表明细（按receiptFileId查重）
        LambdaQueryWrapper<ReportItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportItem::getReceiptFileId, file.getId());
        ReportItem existingItem = reportItemMapper.selectOne(wrapper);

        // 确定报销日期
        java.time.LocalDate date = null;
        if (result != null) {
            if (result.getInvoiceDate() != null) {
                date = result.getInvoiceDate();
            } else if (result.getConsumptionDate() != null) {
                date = result.getConsumptionDate();
            }
        }
        if (date == null) {
            date = java.time.LocalDate.now();
        }

        // 确定报销金额
        BigDecimal amount = null;
        if (result != null) {
            if (result.getTotalAmount() != null) {
                amount = result.getTotalAmount();
            } else if (result.getTotalConsumption() != null) {
                amount = result.getTotalConsumption();
            }
        }

        // 确定文件名
        String receiptFile = null;
        if (result != null && result.getAiFilename() != null) {
            receiptFile = result.getAiFilename();
        }

        // 确定摘要
        String summary = null;
        if (result != null && result.getDescription() != null) {
            summary = result.getDescription();
        }

        if (existingItem != null) {
            // 已存在则更新
            existingItem.setDate(date);
            if (result != null && result.getExpenseType() != null) {
                existingItem.setReceiptType(result.getExpenseType());
            }
            existingItem.setHasReceipt(1);
            if (receiptFile != null) existingItem.setReceiptFile(receiptFile);
            if (amount != null) existingItem.setAmount(amount);
            if (summary != null) existingItem.setSummary(summary);
            if (file.getRemark() != null) existingItem.setRemark(file.getRemark());
            reportItemMapper.updateById(existingItem);
        } else {
            // 不存在则创建
            ReportItem item = new ReportItem();
            item.setProjectId(file.getProjectId());
            item.setDate(date);
            if (result != null && result.getExpenseType() != null) {
                item.setReceiptType(result.getExpenseType());
            }
            item.setHasReceipt(1);
            if (receiptFile != null) item.setReceiptFile(receiptFile);
            if (amount != null) {
                item.setAmount(amount);
            } else {
                item.setAmount(BigDecimal.ZERO);
            }
            if (summary != null) item.setSummary(summary);
            if (file.getRemark() != null) item.setRemark(file.getRemark());
            item.setReceiptFileId(file.getId());
            reportItemMapper.insert(item);
        }
    }

    @Override
    public byte[] downloadFile(Long fileId) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        String fullPath = storageConfig.getBasePath() + "/" + file.getStoragePath();
        try {
            return Files.readAllBytes(Paths.get(fullPath));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
    }
}
