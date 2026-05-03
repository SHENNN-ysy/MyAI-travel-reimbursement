package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.dto.request.SettingsUpdateDTO;
import com.aidemo.myaitravelreimbursement.dto.response.SettingsVO;
import com.aidemo.myaitravelreimbursement.entity.Settings;
import com.aidemo.myaitravelreimbursement.mapper.SettingsMapper;
import com.aidemo.myaitravelreimbursement.service.SettingsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置服务实现
 */
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private final SettingsMapper settingsMapper;

    @Override
    public SettingsVO getSettings() {
        List<Settings> all = settingsMapper.selectList(null);
        Map<String, String> map = new HashMap<>();
        for (Settings s : all) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }

        SettingsVO vo = new SettingsVO();
        vo.setAppName(map.getOrDefault("app_name", "差旅报销助手"));
        vo.setAutoRecognize(Boolean.parseBoolean(map.getOrDefault("auto_recognize", "false")));
        vo.setAutoArchive(Boolean.parseBoolean(map.getOrDefault("auto_archive", "true")));
        return vo;
    }

    @Override
    @Transactional
    public SettingsVO updateSettings(SettingsUpdateDTO dto) {
        if (dto.getAppName() != null) {
            upsertSetting("app_name", dto.getAppName(), "应用名称");
        }
        if (dto.getAutoRecognize() != null) {
            upsertSetting("auto_recognize", String.valueOf(dto.getAutoRecognize()), "上传后自动识别");
        }
        if (dto.getAutoArchive() != null) {
            upsertSetting("auto_archive", String.valueOf(dto.getAutoArchive()), "识别后自动归档");
        }
        return getSettings();
    }

    @Override
    public String getSettingValue(String key, String defaultValue) {
        Settings setting = settingsMapper.selectOne(
                new LambdaQueryWrapper<Settings>().eq(Settings::getSettingKey, key)
        );
        return setting != null ? setting.getSettingValue() : defaultValue;
    }

    private void upsertSetting(String key, String value, String description) {
        Settings existing = settingsMapper.selectOne(
                new LambdaQueryWrapper<Settings>().eq(Settings::getSettingKey, key)
        );
        if (existing != null) {
            existing.setSettingValue(value);
            settingsMapper.updateById(existing);
        } else {
            Settings newSetting = new Settings();
            newSetting.setSettingKey(key);
            newSetting.setSettingValue(value);
            newSetting.setDescription(description);
            settingsMapper.insert(newSetting);
        }
    }
}
