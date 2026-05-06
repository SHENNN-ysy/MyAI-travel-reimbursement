package com.aidemo.myaitravelreimbursement.dto.response;

import com.aidemo.myaitravelreimbursement.constant.ProjectStatus;
import com.aidemo.myaitravelreimbursement.entity.Project;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目详情响应VO
 */
@Data
public class ProjectDetailVO {

    private Long id;
    private String name;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String person;
    private String department;
    private String budget;
    private String remark;
    private Integer status;
    private String statusName;
    private BigDecimal totalAmount;
    private Long fileCount;
    private Long confirmedCount;
    private Long unconfirmedCount;
    private FolderStructureVO folderStructure;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<FolderVO> folders;

    public static ProjectDetailVO fromEntity(Project project) {
        ProjectDetailVO vo = new ProjectDetailVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setDestination(project.getDestination());
        vo.setStartDate(project.getStartDate());
        vo.setEndDate(project.getEndDate());
        vo.setReason(project.getReason());
        vo.setPerson(project.getPerson());
        vo.setDepartment(project.getDepartment());
        vo.setBudget(project.getBudget());
        vo.setRemark(project.getRemark());
        vo.setStatus(project.getStatus());
        vo.setStatusName(ProjectStatus.getName(project.getStatus()));
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
        return vo;
    }

    @Data
    public static class FolderStructureVO {
        private List<FolderVO> folders;
    }
}
