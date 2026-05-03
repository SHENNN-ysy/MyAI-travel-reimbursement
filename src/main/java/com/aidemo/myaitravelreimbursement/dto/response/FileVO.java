package com.aidemo.myaitravelreimbursement.dto.response;

import com.aidemo.myaitravelreimbursement.constant.FileStatus;
import com.aidemo.myaitravelreimbursement.constant.FileType;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件响应VO
 */
@Data
public class FileVO {

    private Long id;
    private Long projectId;
    private Long folderId;
    private String name;
    private String originalName;
    private Long size;
    private String sizeDisplay;
    private String type;
    private String typeName;
    private String mimeType;
    private Integer status;
    private String statusName;
    private String remark;
    private Integer confirmed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private RecognitionResultVO recognitionResult;

    public static FileVO fromEntity(UploadFile file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setProjectId(file.getProjectId());
        vo.setFolderId(file.getFolderId());
        vo.setName(file.getName());
        vo.setOriginalName(file.getOriginalName());
        vo.setSize(file.getSize());
        vo.setSizeDisplay(formatSize(file.getSize()));
        vo.setType(file.getType());
        vo.setTypeName(FileType.getName(file.getType()));
        vo.setMimeType(file.getMimeType());
        vo.setStatus(file.getStatus());
        vo.setStatusName(FileStatus.getName(file.getStatus()));
        vo.setRemark(file.getRemark());
        vo.setConfirmed(file.getConfirmed());
        vo.setCreatedAt(file.getCreatedAt());
        vo.setUpdatedAt(file.getUpdatedAt());
        return vo;
    }

    private static String formatSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
