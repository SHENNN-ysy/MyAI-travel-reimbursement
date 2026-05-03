package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectCreateDTO;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FolderVO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectDetailVO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectVO;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.FolderService;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目服务实现
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final UploadFileMapper uploadFileMapper;
    private final FolderService folderService;

    @Override
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
        return ProjectVO.fromEntity(project);
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
        vo.setFolders(folderService.getTree(id));
        return vo;
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
        projectMapper.deleteById(id);
    }
}
