# 差旅报销助手 - Java 后端

基于 Spring Boot 3.x + Java 21 的差旅报销 AI 助手后端服务。

## 技术栈

- Spring Boot 3.5.14 + Java 21
- MyBatis-Plus 3.5.7 (ORM)
- MySQL 8.0 (数据库)
- EasyExcel 4.0.3 (Excel 导出)
- Knife4j (Swagger API 文档)
- OkHttp 4.12.0 (AI API 调用)
- Lombok (简化代码)

## 项目结构

```
src/main/java/com/aidemo/myaitravelreimbursement/
├── MyAiTravelReimbursementApplication.java  # 启动类
├── config/          # 配置类
├── common/         # 通用层（统一响应、异常处理）
├── controller/     # 控制器层
├── service/       # 服务层
├── mapper/        # MyBatis Mapper
├── entity/        # 实体类
├── dto/           # 数据传输对象
├── util/          # 工具类
└── constant/      # 常量
```







## 快速开始

### 1. 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+

### 2. 数据库初始化

```bash
# 登录 MySQL
mysql -u root -p

# 执行建表脚本
source src/main/resources/db/schema.sql
```

或者在 MySQL 中手动执行 `src/main/resources/db/schema.sql` 中的 SQL 语句。

### 3. 修改配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/travel_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
    username: root
    password: your_password   # 修改为你的 MySQL 密码，或设置环境变量 DB_PASSWORD
```

配置 AI API（可选，用于发票识别）：

```yaml
ai:
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  api-key: ${AI_API_KEY:your-api-key}   # 设置环境变量 AI_API_KEY
  model: qwen-vl-max
```

### 4. 运行项目

```bash
# 开发环境运行
./mvnw spring-boot:run

# 或打包后运行
./mvnw package -DskipTests
java -jar target/MyAI-travel-reimbursement-0.0.1-SNAPSHOT.jar
```

### 5. 访问服务

- API 基础路径: `http://localhost:8080/api/v1`
- Knife4j API 文档: `http://localhost:8080/api/v1/doc.html`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

## API 文档

### 项目管理 `/projects`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/projects` | 创建项目 |
| GET | `/projects` | 分页查询项目列表 |
| GET | `/projects/{id}` | 获取项目详情 |
| PUT | `/projects/{id}` | 更新项目 |
| DELETE | `/projects/{id}` | 删除项目 |

### 文件夹管理 `/projects/{projectId}/folders`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/projects/{projectId}/folders` | 获取文件夹树 |
| POST | `/projects/{projectId}/folders` | 创建文件夹 |
| PUT | `/projects/{projectId}/folders/{id}` | 更新文件夹 |
| DELETE | `/projects/{projectId}/folders/{id}` | 删除文件夹 |

### 文件管理 `/projects/{projectId}/files`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/projects/{projectId}/files/upload` | 上传文件 |
| GET | `/projects/{projectId}/files/{fileId}` | 获取文件信息 |
| PATCH | `/projects/{projectId}/files/{fileId}` | 更新文件 |
| DELETE | `/projects/{projectId}/files/{fileId}` | 删除文件 |
| POST | `/projects/{projectId}/files/{fileId}/recognize` | 单文件AI识别 |
| POST | `/projects/{projectId}/files/batch/recognize` | 批量识别 |
| POST | `/projects/{projectId}/files/batch/confirm` | 批量确认 |
| GET | `/projects/{projectId}/files/{fileId}/download` | 下载文件 |

### 报表管理 `/projects/{projectId}/reports`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/projects/{projectId}/reports/items` | 获取报表明细列表 |
| POST | `/projects/{projectId}/reports/items` | 添加报表明细 |
| PUT | `/projects/{projectId}/reports/items/{itemId}` | 更新报表明细 |
| DELETE | `/projects/{projectId}/reports/items/{itemId}` | 删除报表明细 |
| GET | `/projects/{projectId}/reports/summary` | 获取项目汇总 |
| GET | `/projects/{projectId}/reports/export` | 导出Excel报销单 |

### 设置 `/settings`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/settings` | 获取全局设置 |
| PUT | `/settings` | 更新全局设置 |

## 统一响应格式


```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

错误码：
- 200: 成功
- 400: 参数错误
- 401: 未认证
- 403: 无权限
- 404: 资源不存在
- 500: 服务器内部错误

## 费用类型

| 类型 | 说明 |
|------|------|
| transport | 交通费 |
| catering | 餐饮费 |
| accommodation | 住宿费 |
| purchase | 采购费 |

## 文件类型

| 类型 | 说明 |
|------|------|
| invoice | 发票 |
| screenshot | 截图 |
| attachment | 附件 |

## 文件存储

文件默认存储在 `D:/myAI-tool/travel-files/` 目录下，结构为：
```
D:/myAI-tool/travel-files/
├── {projectId}/
│   ├── {folderId}/
│   │   └── {filename}
│   └── 0/   # 根目录文件
```

## 许可证

MIT
