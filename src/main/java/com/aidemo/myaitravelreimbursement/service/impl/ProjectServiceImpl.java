package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.config.StorageConfig;
import com.aidemo.myaitravelreimbursement.dto.request.FolderDTO;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectCreateDTO;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FolderVO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectDetailVO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.FolderService;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 项目服务实现
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;
    private final FolderService folderService;
    private final StorageConfig storageConfig;

    @Override
    @Transactional
    public ProjectVO create(ProjectCreateDTO dto) {
        Long userId = UserContext.getUserId();
        // 检查项目名称是否已存在（同一用户下）
        Long existCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getUserId, userId)
                        .eq(Project::getName, dto.getName()));
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目名称【" + dto.getName() + "】已存在，请使用其他名称");
        }

        Project project = new Project();
        project.setUserId(userId);
        project.setName(dto.getName());
        project.setDestination(dto.getDestination());
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        project.setReason(dto.getReason());
        project.setPerson(dto.getPerson());
        project.setDepartment(dto.getDepartment());
        project.setBudget(dto.getBudget());
        project.setRemark(dto.getRemark());
        project.setStatus(0);
        projectMapper.insert(project);

        createDefaultFolders(project.getId(), dto.getName(), userId);
        return ProjectVO.fromEntity(project);
    }

    private void createDefaultFolders(Long projectId, String projectName, Long userId) {
        // 1. 创建数据库记录：主文件夹（与项目同名）
        FolderDTO parentDto = new FolderDTO();
        parentDto.setName(projectName);
        parentDto.setSortOrder(0);
        FolderVO parentFolder = folderService.create(projectId, parentDto, userId);

        // 2. 创建三个子文件夹：发票文件、付款截图、附加材料
        String[] subNames = {"发票文件", "付款截图", "附加材料"};
        String[] subTypes = {"invoice", "screenshot", "attachment"};
        int[] subOrders = {1, 2, 3};

        String[] createdSubFolderNames = new String[3];
        for (int i = 0; i < 3; i++) {
            FolderDTO subDto = new FolderDTO();
            subDto.setName(subNames[i]);
            subDto.setType(subTypes[i]);
            subDto.setParentId(parentFolder.getId());
            subDto.setSortOrder(subOrders[i]);
            folderService.create(projectId, subDto, userId);
            createdSubFolderNames[i] = subNames[i];
        }

        // 3. 在磁盘上创建物理目录结构
        createPhysicalFolders(projectName, createdSubFolderNames, userId);
    }

    /**
     * 在磁盘上创建项目文件夹结构：
     * basePath/{userId}/{projectName}/
     * basePath/{userId}/{projectName}/发票文件/
     * basePath/{userId}/{projectName}/付款截图/
     * basePath/{userId}/{projectName}/附加材料/
     */
    private void createPhysicalFolders(String projectName, String[] subFolderNames, Long userId) {
        try {
            Path mainDir = Paths.get(storageConfig.getBasePath(), String.valueOf(userId), projectName);
            Files.createDirectories(mainDir);

            // 子目录：主目录/子文件夹名/
            for (String subName : subFolderNames) {
                Path subDir = mainDir.resolve(subName);
                Files.createDirectories(subDir);
            }
        } catch (IOException e) {
            // 磁盘目录创建失败不影响业务逻辑，仅记录日志
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "创建项目文件夹失败: " + e.getMessage());
        }
    }

    @Override
    public PageResult<ProjectVO> page(int current, int size, Map<String, Object> params) {
        Long userId = UserContext.getUserId();
        Page<Project> page = new Page<>(current, size);
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getUserId, userId);

        if (params != null) {
            String name = (String) params.get("name");
            Integer status = (Integer) params.get("status");
            if (name != null && !name.isEmpty()) {
                wrapper.like(Project::getName, name);
            }
            if (status != null) {
                wrapper.eq(Project::getStatus, status);
            }
        }

        wrapper.orderByDesc(Project::getCreatedAt);
        IPage<Project> result = projectMapper.selectPage(page, wrapper);

        List<ProjectVO> voList = result.getRecords().stream()
                .map(p -> {
                    ProjectVO vo = ProjectVO.fromEntity(p);
                    Long fileCount = uploadFileMapper.selectCount(
                            new LambdaQueryWrapper<com.aidemo.myaitravelreimbursement.entity.UploadFile>()
                                    .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getProjectId, p.getId())
                    );
                    vo.setFileCount(fileCount);
                    return vo;
                })
                .toList();

        return PageResult.of(result.getTotal(), current, size, voList);
    }

    @Override
    public ProjectDetailVO getDetail(Long id) {
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(userId, project.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
        ProjectDetailVO vo = ProjectDetailVO.fromEntity(project);

        // 查询文件统计
        Long fileCount = uploadFileMapper.selectCount(
                new LambdaQueryWrapper<com.aidemo.myaitravelreimbursement.entity.UploadFile>()
                        .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getProjectId, id)
        );
        Long confirmedCount = uploadFileMapper.selectCount(
                new LambdaQueryWrapper<com.aidemo.myaitravelreimbursement.entity.UploadFile>()
                        .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getProjectId, id)
                        .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getConfirmed, 1)
        );
        Long unconfirmedCount = uploadFileMapper.selectCount(
                new LambdaQueryWrapper<com.aidemo.myaitravelreimbursement.entity.UploadFile>()
                        .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getProjectId, id)
                        .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getConfirmed, 0)
        );

        // 计算总金额（已确认文件的识别结果金额之和）
        vo.setFileCount(fileCount);
        vo.setConfirmedCount(confirmedCount);
        vo.setUnconfirmedCount(unconfirmedCount);
        vo.setTotalAmount(calculateTotalAmount(id));

        // 设置文件夹结构和文件夹列表
        List<FolderVO> folders = folderService.getTree(id);
        vo.setFolders(folders);
        ProjectDetailVO.FolderStructureVO folderStructure = new ProjectDetailVO.FolderStructureVO();
        folderStructure.setFolders(folders);
        vo.setFolderStructure(folderStructure);

        return vo;
    }

    private BigDecimal calculateTotalAmount(Long projectId) {
        // 查询所有已确认的文件及其识别结果
        List<com.aidemo.myaitravelreimbursement.entity.UploadFile> confirmedFiles = uploadFileMapper.selectList(
                new LambdaQueryWrapper<com.aidemo.myaitravelreimbursement.entity.UploadFile>()
                        .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getProjectId, projectId)
                        .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getConfirmed, 1)
        );

        BigDecimal total = BigDecimal.ZERO;
        for (com.aidemo.myaitravelreimbursement.entity.UploadFile file : confirmedFiles) {
            RecognitionResult result = recognitionResultMapper.selectOne(
                    new LambdaQueryWrapper<RecognitionResult>()
                            .eq(RecognitionResult::getFileId, file.getId())
                            .orderByDesc(RecognitionResult::getCreatedAt)
                            .last("LIMIT 1")
            );
            if (result != null) {
                BigDecimal amount = result.getTotalAmount();
                if (amount == null) {
                    amount = result.getTotalConsumption();
                }
                if (amount != null) {
                    total = total.add(amount);
                }
            }
        }
        return total;
    }

    @Override
    public Project getProjectByName(String name) {
        Long userId = UserContext.getUserId();
        return projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getUserId, userId)
                        .eq(Project::getName, name));
    }

    @Override
    @Transactional
    public void markAsProcessed(Long id) {
        Project project = projectMapper.selectById(id);
        Long userId = UserContext.getUserId();
        if (project != null && project.getStatus() == 0 && Objects.equals(userId, project.getUserId())) {
            project.setStatus(1);
            projectMapper.updateById(project);
        }
    }

    @Override
    @Transactional
    public ProjectVO update(Long id, ProjectUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(userId, project.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该项目");
        }

        if (dto.getName() != null) project.setName(dto.getName());
        if (dto.getDestination() != null) project.setDestination(dto.getDestination());
        if (dto.getStartDate() != null) project.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) project.setEndDate(dto.getEndDate());
        if (dto.getReason() != null) project.setReason(dto.getReason());
        if (dto.getPerson() != null) project.setPerson(dto.getPerson());
        if (dto.getDepartment() != null) project.setDepartment(dto.getDepartment());
        if (dto.getBudget() != null) project.setBudget(dto.getBudget());
        if (dto.getRemark() != null) project.setRemark(dto.getRemark());
        if (dto.getStatus() != null) project.setStatus(dto.getStatus());

        projectMapper.updateById(project);
        return ProjectVO.fromEntity(project);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(userId, project.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该项目");
        }

        // 1. 软删除该项目
        projectMapper.deleteById(id);

        // 2. 软删除该项目下的所有文件夹
        folderService.deleteByProjectId(id);

        // 3. 软删除该项目下的所有文件
        uploadFileMapper.delete(new LambdaQueryWrapper<com.aidemo.myaitravelreimbursement.entity.UploadFile>()
                .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getProjectId, id));
    }
}
