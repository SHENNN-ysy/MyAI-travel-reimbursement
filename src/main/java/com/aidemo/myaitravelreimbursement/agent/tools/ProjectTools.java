package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.dto.response.ProjectDetailVO;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * T1: 获取项目基本信息
 */
@Component
@RequiredArgsConstructor
public class ProjectTools {

    private final ProjectService projectService;

    @Tool("获取指定项目的基本信息，包括项目名称、日期、人员、预算、状态等。")
    public String getProjectInfo(@P("projectId") Long projectId) {
        try {
            ProjectDetailVO detail = projectService.getDetail(projectId);
            return String.format("""
                项目信息：
                - 项目名称：%s
                - 目的地：%s
                - 开始日期：%s
                - 结束日期：%s
                - 出差人：%s
                - 部门：%s
                - 出差事由：%s
                - 预算：%s
                - 状态：%s
                - 文件总数：%d
                - 已确认文件：%d
                - 待确认文件：%d
                """,
                    detail.getName(),
                    detail.getDestination() != null ? detail.getDestination() : "未填写",
                    detail.getStartDate() != null ? detail.getStartDate().toString() : "未填写",
                    detail.getEndDate() != null ? detail.getEndDate().toString() : "未填写",
                    detail.getPerson() != null ? detail.getPerson() : "未填写",
                    detail.getDepartment() != null ? detail.getDepartment() : "未填写",
                    detail.getReason() != null ? detail.getReason() : "未填写",
                    detail.getBudget() != null ? detail.getBudget() : "未填写",
                    detail.getStatus() == 0 ? "待处理" : "已完成",
                    detail.getFileCount(),
                    detail.getConfirmedCount(),
                    detail.getUnconfirmedCount()
            );
        } catch (Exception e) {
            return "获取项目信息失败: " + e.getMessage();
        }
    }
}
