package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.config.StorageConfig;
import com.aidemo.myaitravelreimbursement.constant.FileStatus;
import com.aidemo.myaitravelreimbursement.dto.request.FileUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import com.aidemo.myaitravelreimbursement.entity.Folder;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.ReportItem;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.FolderMapper;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final FolderMapper folderMapper;
    private final ProjectMapper projectMapper;
    private final StorageConfig storageConfig;

    @Override
    @Transactional
    public FileVO upload(Long projectId, Long folderId, String type, MultipartFile file) throws IOException {
        Long userId = verifyProjectOwnership(projectId);

        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (!FileUtils.isExtensionAllowed(originalName, storageConfig.getAllowedExtensions())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "不支持的文件类型: " + FileUtils.getExtension(originalName));
        }

        String storageName;
        if ("attachment".equals(type)) {
            // 附件类型：检查同名文件是否已存在，存在则拒绝入库
            Long count = uploadFileMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UploadFile>()
                            .eq(UploadFile::getProjectId, projectId)
                            .eq(UploadFile::getFolderId, folderId != null ? folderId : 0L)
                            .eq(UploadFile::getOriginalName, originalName)
                            .eq(UploadFile::getType, type));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.DATA_DUPLICATE, "该附件【" + originalName + "】已存在，请勿重复上传");
            }
            storageName = originalName;
        } else {
            storageName = UUID.randomUUID().toString().replace("-", "") + "."
                    + FileUtils.getExtension(originalName);
        }

        // 构建磁盘路径：basePath/projectName/folderName/storageName
        // 例：D:/myAI-tool/travel-files/1/2026年5月出差/发票文件/abc123.pdf
        String relativePath;
        if (folderId != null && folderId > 0) {
            // 查询项目名和文件夹名，构建正确路径
            Project project = projectMapper.selectById(projectId);
            Folder folder = folderMapper.selectById(folderId);
            // 文件夹名：子文件夹用 folder.name，主文件夹用 project.name
            String folderName = (folder.getParentId() != null && folder.getParentId() > 0)
                    ? folder.getName()
                    : project.getName();
            relativePath =  project.getName() + "/" + folderName + "/" + storageName;
        } else {
            // 无 folderId，回退到旧路径
            relativePath = projectId + "/0/" + storageName;
        }

        String fullPath = storageConfig.getBasePath() + "/" + relativePath;
        Path dir = Paths.get(fullPath).getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Files.copy(file.getInputStream(), Paths.get(fullPath), StandardCopyOption.REPLACE_EXISTING);

        UploadFile uploadFile = new UploadFile();
        uploadFile.setUserId(userId);
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
        emptyResult.setUserId(userId);
        emptyResult.setProjectId(projectId);
        emptyResult.setFileId(uploadFile.getId());
        emptyResult.setType(type);
        recognitionResultMapper.insert(emptyResult);

        return FileVO.fromEntity(uploadFile, emptyResult);
    }

    private Long verifyProjectOwnership(Long projectId) {
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(userId, project.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
        return userId;
    }

    @Override
    public FileVO getFile(Long fileId) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, file.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该文件");
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
        verifyProjectOwnership(projectId);
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
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, file.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该文件");
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

        // 删除关联的报表明细（软删除）
        reportItemMapper.softDeleteByReceiptFileId(fileId);

        uploadFileMapper.deleteById(fileId);
    }

    @Override
    @Transactional
    public FileVO updateFile(Long fileId, FileUpdateDTO dto) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, file.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该文件");
        }

        // 1. 更新文件基本信息
        if (dto.getRemark() != null) {
            file.setRemark(dto.getRemark());
        }
        if (dto.getConfirmed() != null) {
            file.setConfirmed(dto.getConfirmed());
        }
        // 若传入了 aiFilename，则更新文件存储名（仅发票/截图，附件类型跳过）
        if (dto.getAiFilename() != null && !dto.getAiFilename().isBlank() && !"attachment".equals(file.getType())) {
            file.setName(dto.getAiFilename());
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
            result = new RecognitionResult();
            result.setFileId(fileId);
            result.setProjectId(file.getProjectId());
            result.setUserId(file.getUserId());
            fillRecognitionResult(result, dto);
            recognitionResultMapper.insert(result);
        } else {
            fillRecognitionResult(result, dto);
            recognitionResultMapper.updateById(result);
        }

        // 3. 确认文件时（confirmed=1）自动创建报表明细（仅发票/截图，attachment 跳过）
        if (dto.getConfirmed() != null && dto.getConfirmed() == 1 && !"attachment".equals(file.getType())) {
            createOrUpdateReportItem(file, result);
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
        verifyProjectOwnership(projectId);
        List<FileVO> results = new ArrayList<>();
        for (Long fileId : fileIds) {
            UploadFile file = uploadFileMapper.selectById(fileId);
            if (file != null && file.getProjectId().equals(projectId)) {
                file.setConfirmed(1);
                // 若有 aiFilename 则更新文件存储名（仅发票/截图，附件类型跳过）
                if (!"attachment".equals(file.getType())) {
                    RecognitionResult tmpResult = recognitionResultMapper.selectOne(
                            new LambdaQueryWrapper<RecognitionResult>()
                                    .eq(RecognitionResult::getFileId, fileId)
                                    .orderByDesc(RecognitionResult::getCreatedAt)
                                    .last("LIMIT 1")
                    );
                    if (tmpResult != null && tmpResult.getAiFilename() != null && !tmpResult.getAiFilename().isBlank()) {
                        file.setName(tmpResult.getAiFilename());
                    }
                }
                uploadFileMapper.updateById(file);

                // 查询识别结果
                RecognitionResult recognitionResult = recognitionResultMapper.selectOne(
                        new LambdaQueryWrapper<RecognitionResult>()
                                .eq(RecognitionResult::getFileId, fileId)
                                .orderByDesc(RecognitionResult::getCreatedAt)
                                .last("LIMIT 1")
                );

                // 仅发票/截图文件自动创建报表明细，attachment 跳过
                if (!"attachment".equals(file.getType())) {
                    createOrUpdateReportItem(file, recognitionResult);
                }

                results.add(FileVO.fromEntity(file, recognitionResult));
            }
        }
        return results;
    }

    @Override
    public FileVO unconfirmFile(Long fileId) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, file.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该文件");
        }
        // 将确认状态改为未确认
        file.setConfirmed(0);
        uploadFileMapper.updateById(file);

        // 查询识别结果
        RecognitionResult recognitionResult = recognitionResultMapper.selectOne(
                new LambdaQueryWrapper<RecognitionResult>()
                        .eq(RecognitionResult::getFileId, fileId)
                        .orderByDesc(RecognitionResult::getCreatedAt)
                        .last("LIMIT 1")
        );

        // 软删除对应的报表明细（标记 deleted=1，不做物理删除）
        if (!"attachment".equals(file.getType())) {
            reportItemMapper.softDeleteByReceiptFileId(fileId);
        }

        return FileVO.fromEntity(file, recognitionResult);
    }

    /**
     * 根据文件确认状态自动创建或更新报表明细
     * 字段映射关系（按用户规范）：
     * 发票文件: 开票日期→日期, 消费类型→消费类型, 固定"发票"→票据类型, AI文件改名→票据文件, 价税合计→金额, 文件简述→摘要, 备注信息→备注
     * 截图文件: 消费日期→日期, 消费类型→消费类型, 固定"截图"→票据类型, AI文件改名→票据文件, 总额→金额, 文件简述→摘要, 备注信息→备注
     */
    private void createOrUpdateReportItem(UploadFile file, RecognitionResult result) {
        // 先查未删除的记录
        LambdaQueryWrapper<ReportItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportItem::getReceiptFileId, file.getId());
        ReportItem existingItem = reportItemMapper.selectOne(wrapper);

        // 若未找到，再尝试查软删除记录（用于恢复）
        ReportItem softDeletedItem = null;
        if (existingItem == null) {
            LambdaQueryWrapper<ReportItem> deletedWrapper = new LambdaQueryWrapper<>();
            deletedWrapper.eq(ReportItem::getReceiptFileId, file.getId());
            deletedWrapper.eq(ReportItem::getDeleted, 1);
            softDeletedItem = reportItemMapper.selectOne(deletedWrapper);
        }

        // ---- 日期 ----
        java.time.LocalDate date = null;
        if (result != null && result.getInvoiceDate() != null) {
            date = result.getInvoiceDate();
        } else if (result != null && result.getConsumptionDate() != null) {
            date = result.getConsumptionDate();
        }
        if (date == null) {
            date = java.time.LocalDate.now();
        }

        // ---- 消费类型 ----
        String expenseType = (result != null && result.getExpenseType() != null)
                ? result.getExpenseType()
                : "transport";

        // ---- 票据类型（固定值）----
        String receiptType = "发票";
        if ("screenshot".equals(file.getType())) {
            receiptType = "截图";
        }

        // ---- 票据文件（AI文件改名）----
        String receiptFile = null;
        if (result != null && result.getAiFilename() != null && !result.getAiFilename().isBlank()) {
            receiptFile = result.getAiFilename();
        }
        if (receiptFile == null) {
            receiptFile = (file.getOriginalName() != null) ? file.getOriginalName() : file.getName();
        }

        // ---- 金额 ----
        BigDecimal amount = BigDecimal.ZERO;
        if ("invoice".equals(file.getType()) && result != null && result.getTotalAmount() != null) {
            amount = result.getTotalAmount();
        } else if ("screenshot".equals(file.getType()) && result != null && result.getTotalConsumption() != null) {
            amount = result.getTotalConsumption();
        }

        // ---- 摘要（文件简述）----
        String summary = (result != null && result.getDescription() != null && !result.getDescription().isBlank())
                ? result.getDescription()
                : null;

        // ---- 备注（备注信息）----
        String remark = file.getRemark();

        if (existingItem != null) {
            // 已有未删除记录 → 更新
            existingItem.setDate(date);
            existingItem.setReceiptType(receiptType);
            existingItem.setExpenseType(expenseType);
            existingItem.setHasReceipt(1);
            existingItem.setReceiptFile(receiptFile);
            existingItem.setAmount(amount);
            if (summary != null) existingItem.setSummary(summary);
            if (remark != null) existingItem.setRemark(remark);
            reportItemMapper.updateById(existingItem);
        } else if (softDeletedItem != null) {
            // 软删除记录存在 → 恢复并更新
            softDeletedItem.setDeleted(0);
            softDeletedItem.setDate(date);
            softDeletedItem.setReceiptType(receiptType);
            softDeletedItem.setExpenseType(expenseType);
            softDeletedItem.setHasReceipt(1);
            softDeletedItem.setReceiptFile(receiptFile);
            softDeletedItem.setAmount(amount);
            if (summary != null) softDeletedItem.setSummary(summary);
            if (remark != null) softDeletedItem.setRemark(remark);
            reportItemMapper.updateById(softDeletedItem);
        } else {
            // 无任何记录 → 新建
            Long currentUserId = UserContext.getUserId();
            ReportItem item = new ReportItem();
            item.setUserId(currentUserId);
            item.setProjectId(file.getProjectId());
            item.setDate(date);
            item.setReceiptType(receiptType);
            item.setExpenseType(expenseType);
            item.setHasReceipt(1);
            item.setReceiptFile(receiptFile);
            item.setAmount(amount);
            if (summary != null) item.setSummary(summary);
            if (remark != null) item.setRemark(remark);
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
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, file.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权下载该文件");
        }
        String fullPath = storageConfig.getBasePath() + "/" + file.getStoragePath();
        try {
            return Files.readAllBytes(Paths.get(fullPath));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
    }
}
