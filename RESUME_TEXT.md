# 简历直接引用文本

---

### 【精简版 — 适合工作经历中的项目描述】（推荐）

> **智能差旅报销助手 / MyAI-travel-reimbursement**（个人项目）
> - 基于 Spring Boot 3.x + LangChain4j 构建企业级差旅报销辅助系统，集成通义千问等大模型实现自然语言驱动的智能报销流程；
> - 实现发票 / 截图的 AI-OCR 识别、报销材料自动整理与分类、一键自动化报销及 Apache POI 动态 Excel 报表生成；
> - 使用 MySQL + MyBatis-Plus 设计多表关联报销业务数据库，支持 JWT 用户认证与分级权限管理；
> - 前端基于 Vue 3 + Vite 构建后台管理界面（团队协作），后端提供 RESTful API 对接；
> - 支持 Docker 容器化部署，具备良好的分层架构与可扩展性。

---

### 【详细版 — 适合简历项目经历模块】

**智能差旅报销助手（MyAI-travel-reimbursement）**

**项目描述：** 基于 Spring Boot + AI 的企业差旅报销辅助系统。用户通过自然语言与 AI 助手对话，系统自动完成发票 / 截图的 OCR 识别、报销材料整理、多维度 Excel 报表生成及一键自动化报销全流程，显著提升报销效率，降低人工成本。

**技术栈：** Java 21、Spring Boot 3.x、MyBatis-Plus、LangChain4j、通义千问（Qwen）、MySQL、Apache POI、JWT、Vite + Vue 3、Docker

**核心工作：**
- 设计并实现基于 LangChain4j 的 AI 对话引擎，支持多轮上下文记忆，实现自然语言报销意图解析与自动化操作编排；
- 集成阿里通义千问大模型 API，完成发票 / 截图的智能 OCR 识别与结构化数据提取；
- 基于 MySQL + MyBatis-Plus 实现报销项目、文件、目录、报表、AI 会话等多表关联业务逻辑；
- 使用 Apache POI 实现动态 Excel 报销报表生成，支持按项目、时间等维度定制；
- 实现一键自动化报销流程，覆盖「凭证上传 → AI 识别 → 数据整理 → 报表生成」全链路闭环；
- 提供 JWT 身份认证与接口鉴权，支持用户注册、登录及权限管理；
- 前端基于 Vue 3 + Vite 构建（团队协作），后端暴露 RESTful API 对接；
- 编写 Dockerfile 支持容器化部署，编写数据库初始化脚本（schema.sql）。

**项目亮点：** AI + 报销场景深度结合，自然语言驱动，无人工干预的全自动化报销闭环，LangChain4j 多模型灵活切换能力，清晰的分层架构与高可扩展性设计。
