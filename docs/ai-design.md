# AI 功能设计

## 目标

AI 在本系统中定位为教师和学生的辅助能力，不直接替代教师确认、评分和发布决策。当前重点是把大模型能力接入高频低价值劳动：题目录入、课程资料出题、主观题评分建议和错题讲解。

## 配置

| 变量 | 说明 |
|---|---|
| `OPENAI_BASE_URL` | OpenAI 兼容接口地址，默认 `https://api.openai.com/v1` |
| `OPENAI_API_KEY` | 大模型 API Key |
| `OPENAI_MODEL` | 模型名称，默认 `gpt-4o-mini` |
| `AI_MOCK_ENABLED` | 是否使用本地模拟/规则兜底 |
| `AI_TIMEOUT_SECONDS` | 请求超时时间 |
| `UPLOAD_MAX_FILE_SIZE` | 单文件上传上限 |
| `UPLOAD_MAX_REQUEST_SIZE` | 上传请求上限 |

未配置 API Key 或启用模拟模式时，系统仍提供本地规则兜底，核心考试流程不受影响。

## 教师侧题库 AI

### 直接生成题目

接口：`POST /api/ai/questions/generate`

教师选择科目、知识点、题型、难度、数量和默认分值，AI 返回结构化题目草稿。前端展示预览，教师可修改后保存。

### 上传题目文档识别

接口：`POST /api/ai/questions/import-document`

支持上传 `txt`、`md`、`docx`、`doc`、`pptx`、`ppt`、`pdf` 等文档，后端先抽取文本，再识别题干、选项、答案和解析。适合把已有 Word、PPT、PDF 或 TXT 题目批量转入题库。

### 上传课程资料生成题目

接口：`POST /api/ai/questions/generate-from-material`

教师上传课程讲义、复习资料或知识点文档，并分别设置单选、多选、判断、填空、主观题数量。AI 根据资料内容生成关联知识点的题目草稿。

### 保存 AI 草稿

接口：`POST /api/ai/questions/save`

AI 结果不会自动发布。保存后进入题库草稿状态，由教师继续编辑、确认和发布。入库题目会保留 `source_type` 和 `source_detail`，区分普通手动题、AI 直接生成、题目文档识别和课程材料生成。

## 调用审计

接口：`GET /api/monitor/ai-logs`

远程大模型调用、本地模拟和规则兜底都会写入 `ai_usage_log`。管理员可在系统日志页查看 AI 场景、调用人、提示词、响应、成功状态和失败原因，便于排查提示词质量、服务可用性和题目来源。

## 学生侧错题讲解

接口：`POST /api/ai/wrong-question/explain`

学生在错题本中请求 AI 讲解，系统结合题干、选项、正确答案、学生答案、解析和知识点生成学习化解释。

## 阅卷辅助

接口：`POST /api/ai/suggest-review`

教师批阅主观题时，可请求 AI 给出评分建议和评语。最终分数仍由教师提交。

## 文档抽取边界

文档文本抽取由 `DocumentTextExtractorService` 完成：

- `txt`、`md`：按 UTF-8/GB18030 文本读取。
- `docx`：读取 Word XML 文本。
- `pptx`：读取 PowerPoint 幻灯片、备注、母版和版式 XML 文本。
- `pdf`：尽力解析文本流和可读字符串。
- `doc`、`ppt`：按旧二进制 Office 文档做可读文本提取。

扫描版 PDF、图片型文档、加密文档和复杂排版文档可能无法完整识别。识别失败时前端会提示教师调整文件或改用文本化文档。
