# 差旅报销 AI 助手

基于 Spring Boot 3.x + Java 21 + LangChain4j 的**智能差旅报销助手**后端服务。用户可通过自然语言与 AI 助手对话，完成从发票上传、AI 识别、报表明细生成到 Excel 导出报销单的全流程自动化操作。

---

## 核心功能

- **AI 智能助手对话**：内置 AI 助手（基于 LangChain4j），支持自然语言交互，可通过对话发起报销、查询项目、识别发票等操作
- **报销项目全生命周期管理**：创建、编辑、删除、导出报销项目
- **文件管理**：支持上传发票/截图/附件，按目录结构组织
- **AI OCR 发票识别**：对接多模态大模型（支持 Moonshot Kimi、阿里通义千问等），自动识别发票金额、日期、费用类型
- **报表明细自动生成**：识别完成后，一键批量确认，生成报表明细
- **Excel 报销单导出**：基于 EasyExcel 生成标准格式报销单
- **项目资料包打包下载**：将原始发票与报销单打包为 zip 下载
- **用户认证**：基于 JWT 的注册/登录认证体系
- **Docker 部署**：提供 Dockerfile，支持一键容器化部署

---

## 技术栈

| 分类 | 技术 | 说明 |
|------|------|------|
| 基础框架 | Spring Boot 3.5.14 + Java 21 | JDK 21 虚拟线程（预览）支持 |
| ORM | MyBatis-Plus 3.5.7 | 简化 CRUD，含逻辑删除支持 |
| 数据库 | MySQL 8.0 | 主数据存储 |
| AI 框架 | LangChain4j 1.13.0 | AI 能力抽象层 |
| 多模态识别 | Moonshot Kimi / 通义千问 | AI OCR 发票识别 |
| Excel 导出 | EasyExcel 4.0.3 | 高性能 Excel 读写 |
| API 文档 | Knife4j 4.5.0 + springdoc 2.8.8 | Swagger UI 增强版 |
| MCP 集成 | langchain4j-mcp | 通过 MCP 协议调用 Excel MCP 工具 |
| RAG 检索 | langchain4j embeddings (BGE) | 本地向量库，支持报销知识检索 |
| 用户认证 | JWT (jjwt 0.12.5) | Token 鉴权 |
| 工具库 | OkHttp 4.12.0, PDFBox 3.0.3 | HTTP 调用、PDF 渲染 |

---

## 项目结构

```
src/main/java/com/aidemo/myaitravelreimbursement/
├── MyAiTravelReimbursementApplication.java  # Spring Boot 启动类
├── agent/                                   # AI Agent 核心模块
│   ├── ReimbursementAgent.java              # LangChain4j AiServices，定义 Assistant 接口
│   ├── memory/                              # Agent 记忆管理（会话上下文）
│   ├── prompt/                              # 系统提示词
│   ├── RagConfig/                           # RAG 配置（向量检索）
│   └── tools/                               # AI 可调用工具
│       ├── ProjectTools.java                # T1 项目信息 / T10 打包导出
│       ├── FileTools.java                   # T2 文件列表 / T3 上传引导
│       ├── RecognitionTools.java            # T4-T6 发票识别 / 进度 / 结果
│       └── ReportTools.java                 # T7-T9 报表管理 / Excel 导出
├── controller/                             # REST 控制器层
│   ├── AuthController.java                 # 认证：注册 / 登录 / 当前用户
│   ├── ProjectController.java              # 项目管理 + 项目包导出
│   ├── ReportController.java               # 报表明细 + Excel 导出
│   └── FileController.java                 # 文件上传 / 下载 / 识别（见下方）
├── service/                                # 业务逻辑层
├── mapper/                                 # MyBatis-Plus Mapper
├── entity/                                 # 数据库实体
├── dto/                                    # DTO（请求/响应）
├── config/                                 # 配置类（CORS、JWT、存储等）
├── common/                                 # 统一响应、异常处理、用户上下文
├── util/                                   # 工具类
└── constant/                              # 常量定义

src/main/resources/
├── application.yml                         # 主配置文件
├── application-secrets.yml                # 敏感配置（密钥 / API Key）
├── db/schema.sql                          # MySQL 建表脚本
├── docs/AI报销助手使用指南.md              # RAG 知识库文档
└── skills/full/SKILL.md                  # LangChain4j Skill 定义文件
```

---

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+

### 1. 数据库初始化

```bash
mysql -u root -p
source src/main/resources/db/schema.sql
```

### 2. 配置文件

将 `src/main/resources/application-secrets.yml`（或环境变量）中的占位符替换为真实值：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/travel_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
    username: root
    password: your_password

jwt:
  secret: your_jwt_secret_key_here

app:
  base-url: http://localhost:8080/api/v1

storage:
  base-path: D:/myAI-tool/travel-files

ai:
  base-url: https://api.moonshot.cn/v1
  api-key: your_moonshot_api_key
  model: kimi-k2.5

excel-mcp:
  url: http://localhost:8017/mcp

rag:
  docs-path: D:/myAI-tool/docs
```

**环境变量覆盖方式**（推荐生产部署使用）：

| 环境变量 | 说明 |
|----------|------|
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `JWT_SECRET` | JWT 签名密钥 |
| `AI_BASE_URL` | AI API 地址 |
| `AI_API_KEY` | AI API 密钥 |
| `AI_MODEL` | AI 模型名称 |

### 3. 运行

```bash
# 开发环境
./mvnw spring-boot:run

# 打包运行
./mvnw package -DskipTests
java -jar target/MyAI-travel-reimbursement-0.0.1-SNAPSHOT.jar
```

### 4. 访问服务

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/api/v1` | API 根路径 |
| `http://localhost:8080/api/v1/doc.html` | Knife4j API 文档（推荐） |
| `http://localhost:8080/api/v1/swagger-ui.html` | Swagger UI |

---

## API 概览

### 认证 `/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/login` | 登录（返回 JWT） |
| GET | `/auth/me` | 获取当前用户信息 |

### 项目管理 `/projects`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/projects` | 创建报销项目 |
| GET | `/projects` | 分页查询项目列表 |
| GET | `/projects/{id}` | 获取项目详情（含文件统计） |
| PUT | `/projects/{id}` | 更新项目 |
| DELETE | `/projects/{id}` | 删除项目（逻辑删除） |
| GET | `/projects/{id}/export-package` | 打包下载项目所有文件（zip） |

### 文件管理 `/projects/{projectId}/files`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/projects/{projectId}/files` | 获取文件列表（分目录） |
| POST | `/projects/{projectId}/files/upload` | 上传文件（发票/截图/附件） |
| GET | `/projects/{projectId}/files/tree` | 获取项目文件目录树 |
| POST | `/projects/{projectId}/files` | 创建文件夹 |
| PUT | `/projects/{projectId}/files/{fileId}` | 更新文件/文件夹 |
| DELETE | `/projects/{projectId}/files/{fileId}` | 删除文件/文件夹 |
| GET | `/projects/{projectId}/files/{fileId}` | 获取文件详情 |
| GET | `/projects/{projectId}/files/{fileId}/download` | 下载文件 |
| POST | `/projects/{projectId}/files/{fileId}/recognize` | 单文件 AI 识别 |
| POST | `/projects/{projectId}/files/batch/recognize` | 批量识别（异步，轮询进度） |
| POST | `/projects/{projectId}/files/batch/confirm` | 批量确认，生成报表明细 |

### 报表明细 `/projects/{projectId}/reports`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/projects/{projectId}/reports/items` | 分页获取报表明细 |
| GET | `/projects/{projectId}/reports/items/all` | 获取全部报表明细（不分页） |
| POST | `/projects/{projectId}/reports/items` | 手动添加报表明细 |
| PUT | `/projects/{projectId}/reports/items/{itemId}` | 更新报表明细 |
| DELETE | `/projects/{projectId}/reports/items/{itemId}` | 删除报表明细 |
| GET | `/projects/{projectId}/reports/summary` | 获取项目费用汇总 |
| GET | `/projects/{projectId}/reports/export` | 导出 Excel 报销单 |

### AI 对话 `/projects/{projectId}/chat`（可选插件）

支持通过 WebSocket 或 SSE 流式对话与 AI 助手交互，执行报销全流程。

---

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 报销全流程

### 方式一：AI 一键报销（全自动）

在 AI 对话中发送以下任一指令：

- "帮我报销"
- "一键报销"
- "全流程报销"

AI 将自动串联以下步骤：

```
确认项目信息 → 获取待识别文件 → 批量 OCR 识别 → 轮询进度
    → 汇总识别结果 → 批量确认生成报表明细 → 导出 Excel 报销单
    → 打包下载完整资料包
```

### 方式二：手动分步操作

1. **新建项目**：填写项目名称、地点、人员、日期、预算
2. **上传发票**：将发票/截图分区上传（支持 PDF、JPG、PNG 等）
3. **提交识别**：单张识别或批量识别，系统自动 OCR
4. **核对结果**：逐张查看识别结果，有误可手动修正
5. **批量确认**：确认无误后一键生成报表明细
6. **导出文件**：导出 Excel 报销单 + zip 资料包

---

## 费用类型

| type | 说明 |
|------|------|
| `transport` | 交通费 |
| `catering` | 餐饮费 |
| `accommodation` | 住宿费 |
| `purchase` | 采购费 |

---

## Docker 部署

```bash
# 构建镜像
docker build -t myai-travel-reimbursement .

# 运行容器（推荐通过环境变量注入敏感配置）
docker run -d -p 8080:8080 \
  -e DB_HOST=your_mysql_host \
  -e DB_PASSWORD=your_password \
  -e JWT_SECRET=your_secret \
  -e AI_API_KEY=your_api_key \
  -v /opt/travel-reimbursement:/opt/travel-reimbursement \
  myai-travel-reimbursement
```

> **注意**：Dockerfile 中已预装 Node.js 和 npm，用于运行 Excel MCP 服务。

---

## AI 工具能力（10 个内置工具）

| 编号 | 工具 | 功能 |
|------|------|------|
| T1 | `getProjectInfo` | 查询项目基本信息、文件统计 |
| T2 | `listFiles` | 列出项目下所有文件及识别状态 |
| T3 | `uploadFile` | 获取文件上传预签名信息 |
| T4 | `recognizeFiles` | 提交 AI OCR 识别任务（异步） |
| T5 | `getRecognitionTaskProgress` | 查询识别进度（每次阻塞约 30 秒） |
| T6 | `getRecognitionResults` | 获取识别结果汇总（金额、日期、费用类型） |
| T7 | `createReportItem` | 手动新增一条报表明细 |
| T8 | `batchConfirm` | 批量确认已识别文件，自动生成报表明细 |
| T9 | `exportExcel` | 导出标准格式 Excel 报销单 |
| T10 | `exportPackage` | 打包下载完整项目资料包（zip） |

---

## 数据表结构

| 表名 | 说明 |
|------|------|
| `t_user` | 用户表 |
| `t_project` | 报销项目表 |
| `t_folder` | 文件夹目录表（树形结构） |
| `t_upload_file` | 上传文件表（含识别状态） |
| `t_recognition_result` | AI 识别结果表 |
| `t_report_item` | 报表明细表 |
| `t_batch_recognize_task` | 批量识别任务表 |
| `t_settings` | 全局设置表 |
| `t_agent_session` | AI 对话会话表 |
| `t_agent_task_log` | AI 任务执行日志表 |

---

## 许可证

MIT
