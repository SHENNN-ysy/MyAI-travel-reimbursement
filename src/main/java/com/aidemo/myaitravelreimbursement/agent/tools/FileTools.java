package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
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

    @Tool("列出指定项目下的所有上传文件，支持按类型筛选。入参：projectId - 项目ID（必填）、type - 文件类型（可选，invoice/screenshot/attachment）")
    public String listFiles(@P("projectId") Long projectId, @P("type") String type) {
        try {
            PageResult<FileVO> result = fileStorageService.listFiles(projectId, 1, 100,
                    (type == null || type.isEmpty()) ? null : type, null);

            if (result.getRecords().isEmpty()) {
                return "该项目下暂无文件。";
            }

            StringBuilder sb = new StringBuilder("文件列表（共 " + result.getRecords().size() + " 个文件）：\n");
            for (FileVO file : result.getRecords()) {
                String statusStr = switch (file.getStatus()) {
                    case 0 -> "待识别";
                    case 1 -> "识别中";
                    case 2 -> "已识别";
                    case 3 -> "识别失败";
                    default -> "未知";
                };
                String confirmedStr = file.getConfirmed() != null && file.getConfirmed() == 1 ? "✓已确认" : "未确认";
                sb.append(String.format("- [%s] %s | 类型:%s | 状态:%s | %s",
                        file.getId(), file.getOriginalName(),
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
