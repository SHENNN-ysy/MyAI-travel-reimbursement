package com.aidemo.myaitravelreimbursement.mapper;

import com.aidemo.myaitravelreimbursement.entity.ReportItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 报表明细 Mapper
 */
@Mapper
public interface ReportItemMapper extends BaseMapper<ReportItem> {

    /**
     * 软删除报表明细（将 deleted 置为 1）
     */
    @Update("UPDATE t_report_item SET deleted = 1, updated_at = NOW() WHERE receipt_file_id = #{receiptFileId} AND deleted = 0")
    int softDeleteByReceiptFileId(@Param("receiptFileId") Long receiptFileId);

    /**
     * 恢复已软删除的报表明细（将 deleted 置为 0）
     */
    @Update("UPDATE t_report_item SET deleted = 0, updated_at = NOW() WHERE receipt_file_id = #{receiptFileId} AND deleted = 1")
    int restoreByReceiptFileId(@Param("receiptFileId") Long receiptFileId);
}
