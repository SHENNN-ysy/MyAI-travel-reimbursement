package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.dto.request.FolderDTO;
import com.aidemo.myaitravelreimbursement.dto.response.FolderVO;
import com.aidemo.myaitravelreimbursement.entity.Folder;
import com.aidemo.myaitravelreimbursement.mapper.FolderMapper;
import com.aidemo.myaitravelreimbursement.service.FolderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件夹服务实现
 */
@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderMapper folderMapper;

    @Override
    public FolderVO create(Long projectId, FolderDTO dto) {
        Folder folder = new Folder();
        folder.setProjectId(projectId);
        folder.setName(dto.getName());
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

        List<FolderVO> allVos = folders.stream()
                .map(FolderVO::fromEntity)
                .toList();

        Map<Long, List<FolderVO>> childrenMap = allVos.stream()
                .filter(f -> f.getParentId() != null && f.getParentId() != 0)
                .collect(Collectors.groupingBy(FolderVO::getParentId));

        List<FolderVO> rootFolders = new ArrayList<>();
        for (FolderVO vo : allVos) {
            Long parentId = vo.getParentId();
            if (parentId == null || parentId == 0) {
                rootFolders.add(vo);
            } else {
                List<FolderVO> children = childrenMap.get(vo.getId());
                if (children != null) {
                    vo.setChildren(children);
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
}
