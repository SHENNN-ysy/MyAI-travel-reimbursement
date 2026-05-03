package com.aidemo.myaitravelreimbursement.service;

import com.aidemo.myaitravelreimbursement.dto.request.FolderDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FolderVO;

import java.util.List;

/**
 * 文件夹服务接口
 */
public interface FolderService {

    FolderVO create(Long projectId, FolderDTO dto);

    List<FolderVO> getTree(Long projectId);

    FolderVO update(Long id, FolderDTO dto);

    void delete(Long id);
}
