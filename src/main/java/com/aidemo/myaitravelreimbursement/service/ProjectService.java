package com.aidemo.myaitravelreimbursement.service;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectCreateDTO;
import com.aidemo.myaitravelreimbursement.dto.request.ProjectUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectDetailVO;
import com.aidemo.myaitravelreimbursement.dto.response.ProjectVO;
import com.aidemo.myaitravelreimbursement.entity.Project;

import java.util.Map;

/**
 * 项目服务接口
 */
public interface ProjectService {

    ProjectVO create(ProjectCreateDTO dto);

    PageResult<ProjectVO> page(int current, int size, Map<String, Object> params);

    ProjectDetailVO getDetail(Long id);

    Project getProjectByName(String name);

    ProjectVO update(Long id, ProjectUpdateDTO dto);

    void delete(Long id);
}
