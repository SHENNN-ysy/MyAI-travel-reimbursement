package com.aidemo.myaitravelreimbursement.service;

import com.aidemo.myaitravelreimbursement.common.PageResult;
import com.aidemo.myaitravelreimbursement.dto.request.ReportItemDTO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportItemVO;
import com.aidemo.myaitravelreimbursement.dto.response.ReportSummaryVO;

import java.io.OutputStream;
import java.util.List;

/**
 * 报表服务接口
 */
public interface ReportService {

    PageResult<ReportItemVO> pageItems(Long projectId, int current, int size, String receiptType);

    List<ReportItemVO> listItems(Long projectId, String receiptType);

    ReportItemVO createItem(Long projectId, ReportItemDTO dto);

    ReportItemVO updateItem(Long id, ReportItemDTO dto);

    void deleteItem(Long id);

    ReportSummaryVO getSummary(Long projectId);

    void exportExcel(Long projectId, OutputStream outputStream);
}
