package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * T2 & T3: 文件管理工具（列出文件、上传文件）
 */
@Component
@RequiredArgsConstructor
public class FileTools {

    private final FileStorageService fileStorageService;
    private final ProjectMapper projectMapper;

    @Tool("列出指定项目下的所有上传文件，支持按类型筛选。入参：projectName - 项目名称（必填）")
    public String listFiles(@P("projectName") String projectName) {
        UserContext.Snapshot snapshot = UserContext.getCurrentSnapshot();
        if (snapshot != null) {
            UserContext.restore(snapshot);
        }
        try {
            Project project = projectMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Project>()
                            .eq(Project::getName, projectName));
            if (project == null) {
                return "未找到项目名为【" + projectName + "】的项目。";
            }
            Long projectId = project.getId();
            PageResult<FileVO> result = fileStorageService.listFiles(projectId, 1, 100, null, null);

            if (result.getRecords().isEmpty()) {
                return "项目【" + projectName + "】下暂无文件。";
            }

            StringBuilder sb = new StringBuilder("项目【" + projectName + "】文件列表（共 " + result.getRecords().size() + " 个文件）：\n");
            for (FileVO file : result.getRecords()) {
                String statusStr;
                if ("attachment".equals(file.getType())) {
                    statusStr = "无需识别";
                } else {
                    statusStr = switch (file.getStatus()) {
                        case 0 -> "待识别";
                        case 1 -> "识别中";
                        case 2 -> "已识别";
                        case 3 -> "识别失败";
                        default -> "未知";
                    };
                }
                String confirmedStr = file.getConfirmed() != null && file.getConfirmed() == 1 ? "✓已确认" : "未确认";
                sb.append(String.format("- [%s] %s | 类型:%s | 状态:%s | %s",
                        file.getId(), file.getName(),
                        file.getType() != null ? file.getType() : "-",
                        statusStr, confirmedStr));
                if (file.getRecognitionResult() != null && file.getRecognitionResult().getTotalAmount() != null) {
                    sb.append(" | 金额:¥").append(file.getRecognitionResult().getTotalAmount());
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "获取文件列表失败: " + e.getMessage();
        }
    }

    @Tool("获取用于上传文件的预签名信息，用于接收用户上传的文件。实际文件上传需通过标准文件上传接口完成。")
    public String uploadFile() {
        return "请让用户通过标准的文件上传接口上传发票或截图。文件上传接口路径：POST /projects/{projectId}/files/upload";
    }
}
