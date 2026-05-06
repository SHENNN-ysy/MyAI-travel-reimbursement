package com.aidemo.myaitravelreimbursement.dto.request;

import lombok.Data;
import java.util.List;

/**
 * 批量识别任务提交请求DTO
 */
@Data
public class BatchRecognizeRequestDTO {

    /** 待识别的文件ID列表 */
    private List<Long> fileIds;
}
