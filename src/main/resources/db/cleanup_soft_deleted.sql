-- ============================================================
-- 清理 travel_db 中所有软删除记录（deleted = 1）
-- 执行前请先备份数据库！
-- 执行方式：mysql -u <user> -p travel_db < cleanup_soft_deleted.sql
-- ============================================================

-- 1. t_report_item：先清理（被其他表引用，通过 receipt_file_id 关联）
DELETE FROM t_report_item WHERE deleted = 1;

-- 2. t_recognition_result：清理识别结果
DELETE FROM t_recognition_result WHERE deleted = 1;

-- 3. t_upload_file：清理上传文件（依赖 project_id）
DELETE FROM t_upload_file WHERE deleted = 1;

-- 4. t_folder：清理文件夹（依赖 project_id）
DELETE FROM t_folder WHERE deleted = 1;

-- 5. t_batch_recognize_task：清理批量识别任务
DELETE FROM t_batch_recognize_task WHERE deleted = 1;

-- 6. t_project：最后清理项目
DELETE FROM t_project WHERE deleted = 1;
