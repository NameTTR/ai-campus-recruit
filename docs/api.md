# API 文档

所有接口返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

## Auth

- `POST /api/auth/login`：登录。
- `POST /api/auth/logout`：登出。
- `GET /api/auth/me`：获取当前用户。

## Resume

- `POST /api/resumes/upload`：上传简历，支持 PDF、DOC、DOCX；DOC/DOCX 会抽取正文并在后续诊断中优先传给 AI。
  - 返回：`ResumeSummary`，包含 `resumeId`、`studentId`、`fileName`、`education`、`skills`、`projects`、`diagnosis`、`score`、`objectKey`、`storageProvider`、`storageStatus`。
  - `storageProvider=local-demo` 且 `storageStatus=SKIPPED` 表示对象存储未开启；`storageProvider=minio` 且 `storageStatus=STORED` 表示文件已写入 MinIO；`FAILED` 表示写入 MinIO 失败但上传主流程已降级继续。
- `GET /api/resumes/{id}`：查看简历摘要。
- `POST /api/resumes/{id}/analyze`：触发 AI 简历诊断。

## Job

- `POST /api/jobs`：发布岗位。
- `GET /api/jobs`：岗位列表。
- `GET /api/jobs/{id}`：岗位详情。
- `POST /api/jobs/{id}/analyze`：触发 AI 岗位分析。

## Match

- `POST /api/matches/resume-job`：生成简历和岗位匹配结果。
- `GET /api/matches/student/{studentId}`：学生匹配结果。
- `GET /api/matches/job/{jobId}`：岗位候选人匹配结果。

## AI Interview

- `GET /api/ai/status`：查看 AI 模块配置与能力状态。
  - 返回：`AiModuleStatus`，包含 `provider`、`model`、`configured`、`baseUrl`、`capabilities`、`fallbackReason`。
  - 不返回任何 API Key；`configured=false` 时表示会进入离线演示降级。
- `POST /api/ai/analyze`：通用 AI 分析接口，供简历诊断、岗位分析和匹配分析复用。
  - 请求体：`taskType`、`content`、`context`。
  - 返回：`AiAnalyzeResponse`，包含 `taskType`、`provider`、`content`、`mocked`。
- `POST /api/ai/candidates/screen`：基于投递、简历摘要、项目经历和岗位要求生成候选人初筛结果。
  - 请求体：`deliveryId`、`companyId`、`studentId`、`resumeId`、`jobId`、`targetRole`、`skills`、`projects`、`jobRequirements`、`resumeSummary`、`jobDescription`。
  - 返回：`CandidateScreenResult`，包含 `deliveryId`、`studentId`、`jobId`、`score`、`recommendation`、`strengths`、`risks`、`interviewQuestions`、`nextActions`、`mocked`。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时，返回确定性的演示初筛结果，且 `mocked=true`。
  - 生成结果会写入筛选历史；默认内存存储，启用持久化后写入 MySQL。
- `GET /api/ai/candidates/screenings?companyId=C001&deliveryId=D001`：查询 AI 候选人初筛历史。
  - 查询参数：`companyId`、`deliveryId` 均可选；为空时不过滤。
  - 返回：`CandidateScreenRecord[]`，每项包含 `screeningId`、`companyId`、`deliveryId`、`studentId`、`jobId`、`score`、`recommendation`、`strengths`、`risks`、`interviewQuestions`、`nextActions`、`mocked`、`createdAt`。
  - 默认使用内存回退；设置 `AI_SCREENING_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `ai_candidate_screen_record`，查询结果通过 Redis cache-aside 缓存。
  - Redis key 格式：`ai:screening:records:company:{companyId|ALL}:delivery:{deliveryId|ALL}`。
  - `AI_SCREENING_DB_HEALTH_ENABLED` 默认关闭，避免 MySQL 临时不可用时影响演示接口健康状态。
- `POST /api/ai/interview/questions`：基于学生、简历和目标岗位生成模拟面试题。
  - 请求体：`studentId`、`resumeId`、`jobId`、`targetRole`、`skills`。
  - 返回：`InterviewQuestion[]`，每项包含 `questionId`、`category`、`difficulty`、`question`、`referencePoints`。
- `POST /api/ai/interview/feedback`：提交模拟面试回答并生成结构化反馈。
  - 请求体：`studentId`、`questionId`、`question`、`answer`、`targetRole`。
  - 返回：`InterviewFeedback`，包含 `score`、`strengths`、`gaps`、`suggestions`、`summary`、`mocked`。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时，返回确定性的演示反馈，且 `mocked=true`。
- `GET /api/ai/interview/records?studentId=S001`：查看学生模拟面试历史记录。
  - 返回：`InterviewRecord[]`，每项包含 `recordId`、`studentId`、`targetRole`、`questionId`、`question`、`answer`、`score`、`summary`、`suggestions`、`mocked`、`createdAt`。
  - 当前 MVP 使用内存存储，服务重启后记录会清空。

## Delivery

- `POST /api/deliveries`：投递岗位。
  - 请求体：`studentId`、`resumeId`、`jobId`。
  - 返回：`DeliveryRecord`，包含 `deliveryId`、`studentId`、`resumeId`、`jobId`、`companyId`、`status`、`createdAt`。
- `GET /api/deliveries/my`：我的投递。
- `GET /api/deliveries/company?companyId=C001`：企业查看本企业投递列表。
- `GET /api/deliveries/statistics`：投递状态统计。
  - 返回：`totalCount`、`statusCounts`、`pendingCount`。
- `GET /api/deliveries/events`：查看最近投递事件。
  - 返回：`DeliveryEvent[]`，每项包含 `eventId`、`eventType`、`deliveryId`、`studentId`、`resumeId`、`jobId`、`companyId`、`deliveryStatus`、`publishStatus`、`createdAt`。
  - `publishStatus=DISABLED` 表示 RocketMQ 发布关闭；`SEND_OK` 表示发布成功；`FAILED` 表示发布失败但投递主流程已降级继续。
- `PUT /api/deliveries/{id}/status?status=INTERVIEW`：更新投递状态。
  - 支持状态：`SUBMITTED`、`VIEWED`、`INTERVIEW`、`OFFER`、`REJECTED`。

## Admin

- `GET /api/admin/dashboard`：学校端统计看板。
  - 返回：`studentCount`、`companyCount`、`jobCount`、`deliveryCount`、`averageMatchScore`、`deliveryStatusCounts`、`pendingDeliveryCount`。
