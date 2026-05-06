package com.aidemo.myaitravelreimbursement.dto.response;

import com.aidemo.myaitravelreimbursement.entity.Folder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹响应VO
 */
@Data
public class FolderVO {

    private Long id;
    private Long projectId;
    private String name;
    private Long parentId;
    private Integer sortOrder;
    private String type;
    private Long fileCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<FolderVO> children = new ArrayList<>();

    public static FolderVO fromEntity(Folder folder) {
        FolderVO vo = new FolderVO();
        vo.setId(folder.getId());
        vo.setProjectId(folder.getProjectId());
        vo.setName(folder.getName());
        vo.setParentId(folder.getParentId());
        vo.setSortOrder(folder.getSortOrder());
        vo.setType(folder.getType());
        vo.setCreatedAt(folder.getCreatedAt());
        vo.setUpdatedAt(folder.getUpdatedAt());
        return vo;
    }
}
