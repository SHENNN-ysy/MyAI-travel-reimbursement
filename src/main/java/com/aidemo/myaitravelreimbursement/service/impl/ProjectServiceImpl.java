package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.PageResult;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    @Override
    @Transactional
    public ProjectVO create(ProjectCreateDTO dto) {
        Project project = new Project();
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

        createDefaultFolders(project.getId(), dto.getName());
        return ProjectVO.fromEntity(project);
    }

    private void createDefaultFolders(Long projectId, String projectName) {
        FolderDTO parentDto = new FolderDTO();
        parentDto.setName(projectName);
        parentDto.setSortOrder(0);
        FolderVO parentFolder = folderService.create(projectId, parentDto);

        // 创建三个默认文件夹：发票文件、付款截图、附加材料
        FolderDTO invoiceDto = new FolderDTO();
        invoiceDto.setName("发票文件");
        invoiceDto.setType("invoice");
        invoiceDto.setParentId(parentFolder.getId());
        invoiceDto.setSortOrder(1);
        folderService.create(projectId, invoiceDto);

        FolderDTO screenshotDto = new FolderDTO();
        screenshotDto.setName("付款截图");
        screenshotDto.setType("screenshot");
        screenshotDto.setParentId(parentFolder.getId());
        screenshotDto.setSortOrder(2);
        folderService.create(projectId, screenshotDto);

        FolderDTO attachmentDto = new FolderDTO();
        attachmentDto.setName("附加材料");
        attachmentDto.setType("attachment");
        attachmentDto.setParentId(parentFolder.getId());
        attachmentDto.setSortOrder(3);
        folderService.create(projectId, attachmentDto);
    }

    @Override
    public PageResult<ProjectVO> page(int current, int size, Map<String, Object> params) {
        Page<Project> page = new Page<>(current, size);
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();

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
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
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
    @Transactional
    public ProjectVO update(Long id, ProjectUpdateDTO dto) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
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
        if (projectMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
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
