package com.aidemo.myaitravelreimbursement.service;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.dto.request.FileUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {

    FileVO upload(Long projectId, Long folderId, String type, MultipartFile file) throws IOException;

    FileVO getFile(Long fileId);

    PageResult<FileVO> listFiles(Long projectId, int current, int size, String type, String expenseType);

    void deleteFile(Long fileId);

    FileVO updateFile(Long fileId, FileUpdateDTO dto);

    List<FileVO> batchConfirm(Long projectId, List<Long> fileIds);

    FileVO unconfirmFile(Long fileId);

    byte[] downloadFile(Long fileId);
}
