package com.aidemo.myaitravelreimbursement.service;

import com.aidemo.myaitravelreimbursement.dto.response.SettingsVO;
import com.aidemo.myaitravelreimbursement.dto.request.SettingsUpdateDTO;

/**
 * 设置服务接口
 */
public interface SettingsService {

    SettingsVO getSettings();

    SettingsVO updateSettings(SettingsUpdateDTO dto);

    String getSettingValue(String key, String defaultValue);
}
