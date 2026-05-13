package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.dto.response.ProjectDetailVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * T1: 获取项目基本信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectTools {

    private final ProjectService projectService;

    @Value("${storage.base-path}")
    private String storageBasePath;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Tool("获取指定项目的基本信息，包括项目名称、日期、人员、预算、状态等。")
    public String getProjectInfo(@P("projectName") String projectName) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }
            ProjectDetailVO detail = projectService.getDetail(project.getId());
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

    @Tool("导出报销项目（打包下载）。入参：projectName - 项目名称（必填）。【重要】此方法会将项目打包为 zip 文件并保存到磁盘，然后返回下载路径，请将此路径告知用户以便下载。")
    public String exportPackage(@P("projectName") String projectName) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }

            File projectDir = new File(storageBasePath,
                    project.getId() + File.separator + (project.getName() != null ? project.getName() : "项目"));
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                return "项目文件夹不存在，无法导出。";
            }

            String zipFileName = (project.getName() != null ? project.getName() : "项目")
                    + "_报销项目_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + ".zip";
            File zipFile = new File(projectDir.getParentFile(), zipFileName);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                zipDirectory(projectDir, "", zos);
            }

            return String.format("""
                报销项目打包导出成功！
                - 项目名称：%s
                - 打包文件：%s
                【下载地址】：%s
                请复制上述下载地址，在浏览器中打开即可下载 zip 文件。
                """,
                    project.getName(),
                    zipFile.getAbsolutePath(),
                    appBaseUrl + "/api/v1/projects/" + project.getId() + "/export-package");
        } catch (Exception e) {
            log.error("导出报销项目失败", e);
            return "导出报销项目失败: " + e.getMessage();
        }
    }

    private void zipDirectory(File dir, String baseName, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = baseName.isEmpty() ? file.getName() : baseName + "/" + file.getName();
            if (file.isDirectory()) {
                zipDirectory(file, entryName, zos);
            } else {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }
}
