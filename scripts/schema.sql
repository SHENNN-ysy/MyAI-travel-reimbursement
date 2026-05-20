-- =============================================
-- Travel Reimbursement AI - 数据库建表脚本
-- 数据库: travel_db
-- =============================================

CREATE DATABASE IF NOT EXISTS travel_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE travel_db;

-- 1. 报销项目表
CREATE TABLE IF NOT EXISTS t_project (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  name         VARCHAR(100) NOT NULL COMMENT '项目名称',
  destination  VARCHAR(200) COMMENT '目的地',
  start_date   DATE COMMENT '开始日期',
  end_date     DATE COMMENT '结束日期',
  reason       VARCHAR(500) COMMENT '出差事由',
  person       VARCHAR(50) COMMENT '出差人',
  department   VARCHAR(100) COMMENT '部门',
  budget       VARCHAR(100) COMMENT '预算项目名称（文本）',
  remark       VARCHAR(500) COMMENT '备注',
  status       TINYINT DEFAULT 0 COMMENT '状态: 0-待处理 1-已完成',
  deleted      TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报销项目表';

-- 2. 文件夹目录表
CREATE TABLE IF NOT EXISTS t_folder (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  project_id BIGINT NOT NULL COMMENT '所属项目ID',
  name       VARCHAR(100) NOT NULL COMMENT '文件夹名称',
  type       VARCHAR(20) COMMENT '文件夹类型: invoice/screenshot/attachment',
  parent_id  BIGINT DEFAULT 0 COMMENT '父文件夹ID, 0表示根目录',
  sort_order INT DEFAULT 0 COMMENT '排序',
  deleted    TINYINT DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES t_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件夹目录表';

-- 3. 上传文件表
CREATE TABLE IF NOT EXISTS t_upload_file (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  project_id          BIGINT NOT NULL COMMENT '所属项目ID',
  folder_id           BIGINT COMMENT '所属文件夹ID',
  name                VARCHAR(255) NOT NULL COMMENT '存储文件名',
  original_name       VARCHAR(255) NOT NULL COMMENT '原始文件名',
  size                BIGINT COMMENT '文件大小(字节)',
  type                VARCHAR(20) COMMENT '文件类型: invoice/screenshot/attachment',
  mime_type           VARCHAR(100) COMMENT 'MIME类型',
  storage_path        VARCHAR(500) COMMENT '存储路径',
  status              TINYINT DEFAULT 0 COMMENT '识别状态: 0-待识别 1-识别中 2-成功 3-失败',
  remark              VARCHAR(500) COMMENT '备注',
  confirmed           TINYINT DEFAULT 0 COMMENT '是否已确认: 0-未确认 1-已确认',
  deleted             TINYINT DEFAULT 0 COMMENT '逻辑删除',
  created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES t_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传文件表';

-- 4. AI识别结果表
CREATE TABLE IF NOT EXISTS t_recognition_result (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  project_id      BIGINT NOT NULL COMMENT '所属项目ID(冗余字段)',
  file_id         BIGINT NOT NULL UNIQUE COMMENT '关联文件ID',
  type            VARCHAR(20) COMMENT '识别类型: invoice/screenshot',
  expense_type    VARCHAR(50) COMMENT '费用类型: transport/catering/accommodation/purchase',
  ai_filename     VARCHAR(255) COMMENT 'AI建议的文件名',
  description     TEXT COMMENT '文件简述',
  invoice_number  VARCHAR(100) COMMENT '发票号码',
  invoice_date    DATE COMMENT '发票日期',
  total_amount    DECIMAL(12,2) COMMENT '价税合计金额',
  seller          VARCHAR(200) COMMENT '销售方名称',
  buyer           VARCHAR(200) COMMENT '购买方名称',
  consumption_count VARCHAR(50) COMMENT '消费次数(截图)',
  consumption_date  DATE COMMENT '消费日期(截图)',
  total_consumption DECIMAL(12,2) COMMENT '总额(截图)',
  confidence      DECIMAL(5,4) COMMENT '识别置信度',
  raw_response    TEXT COMMENT 'AI返回原始JSON',
  deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (file_id) REFERENCES t_upload_file(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI识别结果表';

-- 5. 报表明细表
CREATE TABLE IF NOT EXISTS t_report_item (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id          BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  project_id       BIGINT NOT NULL COMMENT '所属项目ID',
  date             DATE NOT NULL COMMENT '报销日期',
  receipt_type     VARCHAR(50) NOT NULL COMMENT '票据类型: 发票/截图',
  expense_type     VARCHAR(50) NOT NULL COMMENT '消费类型: transport/catering/accommodation/purchase',
  summary          VARCHAR(500) COMMENT '摘要',
  amount           DECIMAL(12,2) NOT NULL COMMENT '金额',
  remark           VARCHAR(500) COMMENT '备注',
  has_receipt      TINYINT NOT NULL DEFAULT 1 COMMENT '是否有票据: 0-无 1-有',
  receipt_file     VARCHAR(200) NOT NULL COMMENT '票据文件名',
  receipt_file_id  BIGINT COMMENT '关联票据文件ID',
  deleted          TINYINT DEFAULT 0 COMMENT '逻辑删除',
  created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES t_project(id) ON DELETE CASCADE,
  FOREIGN KEY (receipt_file_id) REFERENCES t_upload_file(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表明细表';

-- 5.1 批量识别任务表
CREATE TABLE IF NOT EXISTS t_batch_recognize_task (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  task_id     VARCHAR(64) NOT NULL UNIQUE COMMENT '任务ID(UUID)',
  project_id  BIGINT NOT NULL COMMENT '所属项目ID',
  file_ids    TEXT COMMENT '待识别文件ID列表(JSON)',
  total       INT DEFAULT 0 COMMENT '总任务数',
  processed   INT DEFAULT 0 COMMENT '已处理数',
  status      VARCHAR(20) DEFAULT 'pending' COMMENT '任务状态: pending/processing/completed/failed',
  results     TEXT COMMENT '识别结果列表(JSON)',
  error_msg   TEXT COMMENT '错误信息',
  deleted     TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批量识别任务表';

-- 6. 全局设置表
CREATE TABLE IF NOT EXISTS t_settings (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(100) NOT NULL UNIQUE COMMENT '设置键',
  setting_value TEXT COMMENT '设置值',
  description VARCHAR(255) COMMENT '描述',
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局设置表';

-- 初始化默认设置
INSERT INTO t_settings (setting_key, setting_value, description) VALUES
  ('app_name', '出差报销AI助手', '应用名称'),
  ('auto_recognize', 'false', '上传后自动识别'),
  ('auto_archive', 'false', '识别后自动归档'),
  ('notifications', 'true', '消息通知'),
  ('invoice_max_size', '10', '发票单文件大小上限(MB)'),
  ('screenshot_max_size', '5', '截图单文件大小上限(MB)'),
  ('attachment_max_size', '20', '附件单文件大小上限(MB)');

-- 创建 Agent 会话表（每行存储一条对话，id升序=时间正序，role区分用户/AI）
CREATE TABLE IF NOT EXISTS t_agent_session (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id      BIGINT NOT NULL COMMENT '所属项目ID',
  user_id         BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  session_id      VARCHAR(64) NOT NULL COMMENT '会话唯一ID（UUID）',
  role            VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '消息角色: user=用户消息, assistant=AI回复',
  last_message    TEXT COMMENT '对话内容',
  status          TINYINT DEFAULT 0 COMMENT '0-活跃 1-已完成',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES t_project(id) ON DELETE CASCADE,
  INDEX idx_session_id (session_id),
  INDEX idx_project_session (project_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent会话表（每行一条对话）';

-- 创建 Agent 任务执行日志表
CREATE TABLE IF NOT EXISTS t_agent_task_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id      VARCHAR(64) NOT NULL COMMENT '会话ID',
  step_order      INT COMMENT '执行步骤序号',
  tool_name       VARCHAR(100) COMMENT '工具名称',
  tool_input      TEXT COMMENT '工具输入参数',
  tool_output     TEXT COMMENT '工具输出结果',
  execution_time  INT COMMENT '执行耗时(ms)',
  result_status   TINYINT COMMENT '0-成功 1-失败',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent任务执行日志表';

-- =============================================
-- 数据库升级 ALTER 语句（用于已有数据库升级）
-- =============================================

-- t_user: 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码（明文存储）',
    nickname    VARCHAR(50)  DEFAULT '' COMMENT '昵称',
    email       VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    status      TINYINT      DEFAULT 1 COMMENT '1=启用 0=禁用',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- t_project: budget 从 DECIMAL 改为 VARCHAR
-- ALTER TABLE t_project MODIFY COLUMN budget VARCHAR(100) COMMENT '预算项目名称（文本）';

-- t_folder: 添加 type 字段
-- ALTER TABLE t_folder ADD COLUMN type VARCHAR(20) COMMENT '文件夹类型: invoice/screenshot/attachment' AFTER name;

-- t_report_item: 添加 receipt_file 字段
-- ALTER TABLE t_report_item ADD COLUMN receipt_file VARCHAR(200) NOT NULL DEFAULT '' COMMENT '票据文件名' AFTER has_receipt;

-- t_report_item: has_receipt 改为 NOT NULL
-- ALTER TABLE t_report_item MODIFY COLUMN has_receipt TINYINT NOT NULL DEFAULT 1 COMMENT '是否有票据: 0-无 1-有';

-- t_report_item: 添加 expense_type（消费类型）字段
-- ALTER TABLE t_report_item ADD COLUMN expense_type VARCHAR(50) NOT NULL DEFAULT 'transport' COMMENT '消费类型: transport/catering/accommodation/purchase' AFTER receipt_type;

-- 创建批量识别任务表
-- CREATE TABLE IF NOT EXISTS t_batch_recognize_task (
--   id          BIGINT PRIMARY KEY AUTO_INCREMENT,
--   task_id     VARCHAR(64) NOT NULL UNIQUE,
--   project_id  BIGINT NOT NULL,
--   status      VARCHAR(20) DEFAULT 'pending',
--   total       INT DEFAULT 0,
--   processed   INT DEFAULT 0,
--   error_msg   TEXT,
--   created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
--   updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
-- );

