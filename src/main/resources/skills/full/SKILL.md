---
name: full-reimbursement
description: 执行完整的出差报销全流程：识别发票 → 生成报表 → 导出 Excel
---

当用户请求完成报销全流程（如"帮我报销"、"一键报销"、"全流程报销"）时，按以下步骤执行：

1. 调用 `getProjectInfo(projectName)` 确认项目存在，读取项目基本信息。

2. 调用 `listFiles(projectName)` 获取所有待识别的文件列表（status=pending）。

3. 调用 `recognizeFiles(projectName, fileIds)` 批量提交 AI OCR 识别任务。

4. 调用 `getRecognitionTaskProgress(projectName)` 轮询识别进度，间隔 30 秒，最多轮询 5 次（约 150 秒）。每次轮询后检查识别是否完成。

5. 识别完成后，调用 `getRecognitionResults(projectName)` 汇总所有发票的金额、日期、费用类型。

6. 调用 `batchConfirm(projectName)` 批量确认已识别文件，自动生成报表明细。

7. 调用 `exportExcel(projectName)` 生成标准 Excel 报销单文件，返回文件名和下载链接。

8. 调用 `exportPackage(projectName)` 将报销项目打包为 zip 文件并返回下载路径，供用户下载完整的报销资料包。

**参数说明**：
- `projectName`：项目名称，必填。从用户消息中提取。

**错误处理**：
- 如果识别超时（150 秒内未完成），停止执行后续步骤，告诉用户结果并在最终回复中标注超时警告和已识别部分的结果。
- 如果没有待识别文件（步骤 2 返回空列表），停止执行后续步骤，告诉用户结果并询问是否需要重新识别。
