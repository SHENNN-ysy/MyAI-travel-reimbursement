package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.dto.response.FileVO;
import com.aidemo.myaitravelreimbursement.dto.response.RecognitionResultVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileToolsTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private FileTools fileTools;

    private Project testProject;

    @BeforeEach
    void setUp() {
        testProject = new Project();
        testProject.setId(1L);
        testProject.setName("测试项目");
        testProject.setUserId(100L);
    }

    @Nested
    @DisplayName("listFiles 项目不存在")
    class ProjectNotFound {

        @Test
        @DisplayName("项目名不存在时返回提示信息")
        void shouldReturnNotFoundMessage() {
            when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            String result = fileTools.listFiles("不存在的项目");

            assertEquals("未找到项目名为【不存在的项目】的项目。", result);
            verify(projectMapper).selectOne(any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("listFiles 项目无文件")
    class NoFiles {

        @Test
        @DisplayName("项目下没有文件时返回提示信息")
        void shouldReturnEmptyMessage() {
            when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testProject);
            PageResult<FileVO> emptyResult = new PageResult<>(0, 1, 100, Collections.emptyList());
            when(fileStorageService.listFiles(eq(1L), eq(1), eq(100), isNull(), isNull()))
                    .thenReturn(emptyResult);

            String result = fileTools.listFiles("测试项目");

            assertEquals("项目【测试项目】下暂无文件。", result);
        }
    }

    @Nested
    @DisplayName("listFiles 正常返回文件列表")
    class WithFiles {

        @Test
        @DisplayName("返回文件列表包含文件信息")
        void shouldReturnFileList() {
            when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testProject);

            FileVO file1 = createFileVO(10L, "发票1.png", "invoice", 2, 1, "150.00");
            FileVO file2 = createFileVO(20L, "截图2.jpg", "photo", 2, 0, "80.50");
            PageResult<FileVO> pageResult = new PageResult<>(2, 1, 100, List.of(file1, file2));
            when(fileStorageService.listFiles(eq(1L), eq(1), eq(100), isNull(), isNull()))
                    .thenReturn(pageResult);

            String result = fileTools.listFiles("测试项目");

            assertTrue(result.contains("项目【测试项目】文件列表（共 2 个文件）："));
            assertTrue(result.contains("[10] 发票1.png"));
            assertTrue(result.contains("[20] 截图2.jpg"));
            assertTrue(result.contains("已识别"));
            assertTrue(result.contains("待识别"));
            assertTrue(result.contains("未确认"));
            assertTrue(result.contains("金额:¥150.00"));
            assertTrue(result.contains("金额:¥80.50"));
        }

        @Test
        @DisplayName("附件类型文件显示无需识别状态")
        void shouldShowAttachmentNoRecognitionNeeded() {
            when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testProject);

            FileVO attachmentFile = createFileVO(30L, "合同.pdf", "attachment", 0, 0, null);
            PageResult<FileVO> pageResult = new PageResult<>(1, 1, 100, List.of(attachmentFile));
            when(fileStorageService.listFiles(eq(1L), eq(1), eq(100), isNull(), isNull()))
                    .thenReturn(pageResult);

            String result = fileTools.listFiles("测试项目");

            assertTrue(result.contains("无需识别"));
            assertFalse(result.contains("金额"));
        }

        @Test
        @DisplayName("文件状态为已确认时显示已确认标记")
        void shouldShowConfirmedMark() {
            when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testProject);

            FileVO confirmedFile = createFileVO(40L, "已确认发票.png", "invoice", 2, 1, "200.00");
            PageResult<FileVO> pageResult = new PageResult<>(1, 1, 100, List.of(confirmedFile));
            when(fileStorageService.listFiles(eq(1L), eq(1), eq(100), isNull(), isNull()))
                    .thenReturn(pageResult);

            String result = fileTools.listFiles("测试项目");

            assertTrue(result.contains("✓已确认"));
        }
    }

    private FileVO createFileVO(Long id, String name, String type, int status, Integer confirmed, String amount) {
        FileVO vo = new FileVO();
        vo.setId(id);
        vo.setName(name);
        vo.setType(type);
        vo.setStatus(status);
        vo.setConfirmed(confirmed);
        if (amount != null) {
            RecognitionResultVO recResult = new RecognitionResultVO();
            recResult.setTotalAmount(new BigDecimal(amount));
            vo.setRecognitionResult(recResult);
        }
        return vo;
    }
}
