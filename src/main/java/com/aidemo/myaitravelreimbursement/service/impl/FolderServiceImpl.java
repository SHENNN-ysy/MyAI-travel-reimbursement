package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.dto.request.FolderDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FolderVO;
import com.aidemo.myaitravelreimbursement.entity.Folder;
import com.aidemo.myaitravelreimbursement.mapper.FolderMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.FolderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件夹服务实现
 */
@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderMapper folderMapper;
    private final UploadFileMapper uploadFileMapper;

    @Override
    public FolderVO create(Long projectId, FolderDTO dto) {
        Folder folder = new Folder();
        folder.setProjectId(projectId);
        folder.setName(dto.getName());
        folder.setType(dto.getType());
        folder.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        folder.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        folderMapper.insert(folder);
        return FolderVO.fromEntity(folder);
    }

    @Override
    public List<FolderVO> getTree(Long projectId) {
        LambdaQueryWrapper<Folder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Folder::getProjectId, projectId)
                .orderByAsc(Folder::getSortOrder)
                .orderByAsc(Folder::getCreatedAt);
        List<Folder> folders = folderMapper.selectList(wrapper);

        // Step 1: 创建所有 VO，并建立 id -> VO 的映射
        Map<Long, FolderVO> voMap = new LinkedHashMap<>();
        for (Folder folder : folders) {
            FolderVO vo = FolderVO.fromEntity(folder);
            Long fileCount = uploadFileMapper.selectCount(
                    new LambdaQueryWrapper<com.aidemo.myaitravelreimbursement.entity.UploadFile>()
                            .eq(com.aidemo.myaitravelreimbursement.entity.UploadFile::getFolderId, folder.getId())
            );
            vo.setFileCount(fileCount);
            vo.setChildren(new ArrayList<>());
            voMap.put(folder.getId(), vo);
        }

        // Step 2: 按 parentId 分类，构建父子关系
        List<FolderVO> rootFolders = new ArrayList<>();
        for (Folder folder : folders) {
            Long parentId = folder.getParentId();
            FolderVO vo = voMap.get(folder.getId());
            if (parentId == null || parentId == 0) {
                rootFolders.add(vo);
            } else {
                FolderVO parent = voMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(vo);
                }
            }
        }

        return rootFolders;
    }

    @Override
    public FolderVO update(Long id, FolderDTO dto) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "文件夹不存在");
        }
        if (dto.getName() != null) {
            folder.setName(dto.getName());
        }
        if (dto.getType() != null) {
            folder.setType(dto.getType());
        }
        if (dto.getSortOrder() != null) {
            folder.setSortOrder(dto.getSortOrder());
        }
        folderMapper.updateById(folder);
        return FolderVO.fromEntity(folder);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (folderMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "文件夹不存在");
        }
        folderMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteByProjectId(Long projectId) {
        folderMapper.delete(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getProjectId, projectId));
    }
}
