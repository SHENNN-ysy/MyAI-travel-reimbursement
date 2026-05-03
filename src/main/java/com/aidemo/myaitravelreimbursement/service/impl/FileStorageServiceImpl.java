package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.config.StorageConfig;
import com.aidemo.myaitravelreimbursement.constant.FileStatus;
import com.aidemo.myaitravelreimbursement.dto.request.FileUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.AiRecognitionService;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import com.aidemo.myaitravelreimbursement.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final StorageConfig storageConfig;
    private final AiRecognitionService aiRecognitionService;

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

        return FileVO.fromEntity(uploadFile);
    }

    @Override
    public FileVO getFile(Long fileId) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return FileVO.fromEntity(file);
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

        uploadFileMapper.deleteById(fileId);
    }

    @Override
    @Transactional
    public FileVO updateFile(Long fileId, FileUpdateDTO dto) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (dto.getRemark() != null) {
            file.setRemark(dto.getRemark());
        }
        if (dto.getConfirmed() != null) {
            file.setConfirmed(dto.getConfirmed());
        }
        uploadFileMapper.updateById(file);
        return FileVO.fromEntity(file);
    }

    @Override
    @Transactional
    public List<FileVO> batchRecognize(Long projectId, List<Long> fileIds) {
        List<FileVO> results = new ArrayList<>();
        for (Long fileId : fileIds) {
            try {
                UploadFile file = uploadFileMapper.selectById(fileId);
                if (file != null && file.getProjectId().equals(projectId)) {
                    aiRecognitionService.recognize(fileId, file.getType());
                    results.add(FileVO.fromEntity(uploadFileMapper.selectById(fileId)));
                }
            } catch (Exception e) {
                log.error("批量识别文件失败: fileId={}", fileId, e);
            }
        }
        return results;
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
                results.add(FileVO.fromEntity(file));
            }
        }
        return results;
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
