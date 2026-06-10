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

- `POST /api/resumes/upload`：上传简历，支持 PDF、DOC、DOCX；可解析文本的文件会抽取正文并在后续诊断中优先传给 AI。
  - 返回：`ResumeSummary`，包含 `resumeId`、`studentId`、`fileName`、`education`、`skills`、`projects`、`diagnosis`、`score`、`objectKey`、`storageProvider`、`storageStatus`、`sourceFormat`、`parseStatus`、`parsedTextLength`。
  - `storageProvider=local-demo` 且 `storageStatus=SKIPPED` 表示对象存储未开启；`storageProvider=minio` 且 `storageStatus=STORED` 表示文件已写入 MinIO；`FAILED` 表示写入 MinIO 失败但上传主流程已降级继续。
  - 默认使用内存仓储；设置 `RESUME_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `resume_summary_record`，并保存抽取正文供后续 AI 诊断使用。
- `GET /api/resumes/{id}`：查看简历摘要。
  - 简历详情使用 Redis cache-aside 缓存，key 格式：`resume:summaries:detail:{resumeId}`。
  - `RESUME_DB_HEALTH_ENABLED` 与 `RESUME_REDIS_HEALTH_ENABLED` 默认关闭，避免本地未启动 MySQL/Redis 时影响演示健康状态。
- `POST /api/resumes/{id}/analyze`：触发 AI 简历诊断。
  - 服务重启后，若启用持久化，诊断仍会优先使用表内保存的 `parsed_text`。

## Job

- `POST /api/jobs`：发布岗位。
- `GET /api/jobs`：岗位列表。
  - 默认使用内存仓储；设置 `JOB_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `job_record`。
  - 岗位列表使用 Redis cache-aside 缓存，key 格式：`job:records:list:ALL`。
  - `JOB_DB_HEALTH_ENABLED` 与 `JOB_REDIS_HEALTH_ENABLED` 默认关闭，避免本地未启动 MySQL/Redis 时影响演示健康状态。
- `GET /api/jobs/{id}`：岗位详情。
- `POST /api/jobs/{id}/analyze`：触发 AI 岗位分析。

## Match

- `POST /api/matches/resume-job`：生成简历和岗位匹配结果。
  - 默认使用内存仓储；设置 `MATCH_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `match_result_record`。
  - 生成新匹配后会清理学生、岗位和全量匹配结果缓存。
- `GET /api/matches/student/{studentId}`：学生匹配结果。
  - 学生匹配结果使用 Redis cache-aside 缓存，key 格式：`match:results:student:{studentId}`。
- `GET /api/matches/job/{jobId}`：岗位候选人匹配结果。
  - 岗位候选人匹配结果使用 Redis cache-aside 缓存，key 格式：`match:results:job:{jobId}`。
  - `MATCH_DB_HEALTH_ENABLED` 与 `MATCH_REDIS_HEALTH_ENABLED` 默认关闭，避免本地未启动 MySQL/Redis 时影响演示健康状态。
- `GET /api/matches`：全部匹配结果。
  - 全量匹配结果使用 Redis cache-aside 缓存，key 格式：`match:results:list:ALL`。

## AI Interview

- `GET /api/ai/status`：查看 AI 模块配置与能力状态。
  - 返回：`AiModuleStatus`，包含 `provider`、`model`、`configured`、`baseUrl`、`capabilities`、`fallbackReason`。
  - 不返回任何 API Key；`configured=false` 时表示会进入离线演示降级。
- `POST /api/ai/analyze`：通用 AI 分析接口，供简历诊断、岗位分析和匹配分析复用。
  - 请求体：`taskType`、`content`、`context`。
  - 返回：`AiAnalyzeResponse`，包含 `taskType`、`provider`、`content`、`mocked`。
- `POST /api/ai/candidates/screen`：基于投递、简历摘要、项目经历和岗位要求生成候选人初筛结果。
  - 请求体：`deliveryId`、`companyId`、`studentId`、`resumeId`、`jobId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`、`targetRole`、`skills`、`projects`、`jobRequirements`、`resumeSummary`、`jobDescription`。
  - 返回：`CandidateScreenResult`，包含 `deliveryId`、`studentId`、`jobId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`、`score`、`recommendation`、`strengths`、`risks`、`interviewQuestions`、`nextActions`、`mocked`。
  - `resumeParseStatus=TEXT_EXTRACTED` 表示初筛参考了已抽取正文；`UNPARSED` 或 `UNKNOWN` 表示简历正文证据不足，AI 会提示 HR 做人工确认。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时，返回确定性的演示初筛结果，且 `mocked=true`。
  - 生成结果会写入筛选历史；默认内存存储，启用持久化后写入 MySQL。
- `POST /api/ai/candidates/screen/tasks`：创建 AI 候选人异步初筛任务。
  - 请求体沿用 `CandidateScreenRequest`，字段与同步初筛一致。
  - 返回：`CandidateScreenTask`，包含 `taskId`、`deliveryId`、`companyId`、`studentId`、`resumeId`、`jobId`、`status`、`source`、`message`、`result`、`createdAt`、`updatedAt`。
  - `status` 支持 `PENDING`、`RUNNING`、`COMPLETED`、`FAILED`；`source` 支持 `DEMO`、`RUNTIME`、`ROCKETMQ`。
  - `result` 仅在任务完成且有可展示结果时返回，结构为 `CandidateScreenResult`；前端必须能展示无 `result` 的排队、运行中和失败状态。
  - 所有响应仍使用 `ApiResponse<CandidateScreenTask>`，不得返回 API Key、原始提示词、完整简历正文或其他敏感信息。
- `GET /api/ai/candidates/screen/tasks?companyId=C001&deliveryId=D001`：查询 AI 候选人异步初筛任务。
  - 查询参数：`companyId`、`deliveryId` 均可选；为空时不过滤。
  - 返回：`CandidateScreenTask[]`，字段同创建接口。
  - 企业端投递审核和 AI 筛选历史优先使用该任务列表展示异步状态；开发模式未配置 gateway 或 AI proxy 时前端返回确定性的 `DEMO` 任务数据，且不调用 `fetch`。
- `GET /api/ai/candidates/screen/tasks/{taskId}?companyId=C001`：查询单个 AI 候选人异步初筛任务。
  - 路径参数：`taskId`。
  - 查询参数：`companyId` 可选；Gateway 注入 `X-User-Role=COMPANY` 和 `X-User-Id` 时，下游服务必须以注入的企业身份为准。
  - 返回：`ApiResponse<CandidateScreenTask>`；任务不存在或企业身份不匹配时返回失败响应，不暴露其他企业任务。
- `POST /api/ai/candidates/screen/tasks/{taskId}/retry?companyId=C001`：重试失败的 AI 候选人异步初筛任务。
  - 仅允许 `FAILED` 任务重试；`PENDING`、`RUNNING`、`COMPLETED`、任务不存在或企业身份不匹配时返回失败响应。
  - 重试成功后返回新的 `CandidateScreenTask`，通常为 `PENDING` 状态，`source` 沿用原任务来源。
  - 前端企业端任务卡片使用该接口提供失败任务重试操作；开发模式未配置 gateway 或 AI proxy 时返回确定性的重试 fallback。
- `GET /api/ai/candidates/screenings?companyId=C001&deliveryId=D001`：查询 AI 候选人初筛历史。
  - 查询参数：`companyId`、`deliveryId` 均可选；为空时不过滤。
  - 返回：`CandidateScreenRecord[]`，每项包含 `screeningId`、`companyId`、`deliveryId`、`studentId`、`jobId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`、`score`、`recommendation`、`strengths`、`risks`、`interviewQuestions`、`nextActions`、`mocked`、`createdAt`。
  - 默认使用内存回退；设置 `AI_SCREENING_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `ai_candidate_screen_record`，查询结果通过 Redis cache-aside 缓存。
  - Redis key 格式：`ai:screening:records:company:{companyId|ALL}:delivery:{deliveryId|ALL}`。
  - `AI_SCREENING_DB_HEALTH_ENABLED` 默认关闭，避免 MySQL 临时不可用时影响演示接口健康状态。
- `GET /api/ai/screenings/my?studentId=S001`：学生查看自己的 AI 候选人初筛反馈。
  - 返回：`CandidateScreenRecord[]`，字段与企业初筛历史一致。
  - Gateway 已注入 `X-User-Role=STUDENT` 和 `X-User-Id` 时，下游服务必须以注入的学生身份为准，忽略查询参数中的其他 `studentId`。
  - `ADMIN` 可通过 `studentId` 参数查询指定学生记录；`COMPANY` 仍应使用 `/api/ai/candidates/screenings`，前端学生闭环页面不向企业角色展示该入口。
  - 前端开发模式未配置 gateway 或 AI proxy 时返回确定性的演示数据，且不调用 `fetch`。
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
  - 请求体：`studentId`、`resumeId`、`jobId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`。
  - 返回：`DeliveryRecord`，包含 `deliveryId`、`studentId`、`resumeId`、`jobId`、`companyId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`、`status`、`createdAt`。
  - 未传简历解析字段时会降级为 `resumeSourceFormat=UNKNOWN`、`resumeParseStatus=UNKNOWN`、`resumeParsedTextLength=0`，保证旧调用方兼容。
- `GET /api/deliveries/my`：我的投递。
- `GET /api/deliveries/company?companyId=C001`：企业查看本企业投递列表。
  - 默认使用内存仓储；设置 `DELIVERY_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `delivery_record`。
  - 企业投递列表使用 Redis cache-aside 缓存，key 格式：`delivery:records:company:{companyId|ALL}`。
  - `DELIVERY_DB_HEALTH_ENABLED` 与 `DELIVERY_REDIS_HEALTH_ENABLED` 默认关闭，避免本地未启动 MySQL/Redis 时影响演示健康状态。
- `GET /api/deliveries/statistics`：投递状态统计。
  - 返回：`totalCount`、`statusCounts`、`pendingCount`。
- `GET /api/deliveries/events`：查看最近投递事件。
  - 返回：`DeliveryEvent[]`，每项包含 `eventId`、`eventType`、`deliveryId`、`studentId`、`resumeId`、`jobId`、`companyId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`、`deliveryStatus`、`publishStatus`、`createdAt`。
  - `publishStatus=DISABLED` 表示 RocketMQ 发布关闭；`SEND_OK` 表示发布成功；`FAILED` 表示发布失败但投递主流程已降级继续。
- `PUT /api/deliveries/{id}/status?status=INTERVIEW`：更新投递状态。
  - 支持状态：`SUBMITTED`、`VIEWED`、`INTERVIEW`、`OFFER`、`REJECTED`。

## v2.3 JWT/Gateway 鉴权

- `POST /api/auth/login`：登录成功后返回 `LoginResponse`，其中 `token` 为 JWT；前端应保存该 token，并在后续受保护 API 中使用 `Authorization: Bearer <token>`。
- 受保护 API：除登录、健康检查、静态资源和明确放行的公开接口外，业务 API 默认需要携带 Bearer Token；缺失、过期或签名无效时返回统一 `ApiResponse<T>` 错误结构。
- `GET /api/auth/me`：用于验证当前 JWT 并返回当前登录用户信息；请求头必须包含 `Authorization: Bearer <token>`。
- Gateway 鉴权：开启后由 Gateway 校验 Bearer Token，并向下游服务透传已认证用户上下文；业务服务仍保持独立可运行。
- 配置项：
  - `JWT_SECRET`：JWT 签名密钥，必须通过环境变量或安全配置注入，禁止提交真实密钥。
  - `JWT_ISSUER`：JWT 签发方，用于签发和校验时的一致性检查。
  - `JWT_TTL_SECONDS`：JWT 有效期，单位秒。
  - `GATEWAY_AUTH_ENABLED`：Gateway 鉴权开关；本地演示可关闭，生产环境应开启。
- 基础角色规则：`STUDENT` 可访问学生个人资料、简历、投递、岗位浏览和模拟面试能力；`COMPANY` 可访问企业岗位发布/分析、投递审核和候选人初筛能力；`ADMIN` 可访问学校看板和系统管理能力。

## v2.4 Gateway Trusted Identity

- Gateway-authenticated requests pass `X-User-Id` and `X-User-Role` to downstream business services after JWT verification.
- Business services still run independently for local demo and direct-service debugging. When the identity headers are missing, they keep the old request/body/default fallback behavior.
- Student-owned endpoints prefer `X-User-Id` when `X-User-Role=STUDENT`:
  - `GET /api/students/profile`
  - `PUT /api/students/profile`
  - `POST /api/resumes/upload`
  - `POST /api/matches/resume-job`
  - `GET /api/matches/student/{studentId}`
  - `POST /api/deliveries`
  - `GET /api/deliveries/my`
  - `POST /api/ai/interview/questions`
  - `POST /api/ai/interview/feedback`
  - `GET /api/ai/interview/records`
- Company-owned endpoints prefer `X-User-Id` when `X-User-Role=COMPANY`:
  - `POST /api/jobs`
  - `GET /api/deliveries/company`
  - `POST /api/ai/candidates/screen`
  - `GET /api/ai/candidates/screenings`
- `ADMIN` keeps cross-tenant query behavior for review and management screens; request parameters such as `studentId` and `companyId` are still honored for admin calls.

## v2.6 Account and RBAC APIs

- `GET /api/admin/accounts?role=ADMIN&status=ACTIVE&keyword=admin`: list accounts for admin management screens.
  - Query parameters are optional: `role` supports `STUDENT`, `COMPANY`, `ADMIN`; `status` supports `ACTIVE`, `DISABLED`, `LOCKED`; `keyword` matches username or display name.
  - Returns: `AccountSummary[]`.
  - `AccountSummary` fields: `accountId`, `username`, `displayName`, `role`, `status`, `permissions`, `createdAt`, `updatedAt`.
  - Required permission: `admin:account:read`.

- `POST /api/admin/accounts`: create an account.
  - Request body: `username`, `password`, `displayName`, `role`, optional `status`, optional `permissions`.
  - Returns: `AccountSummary`.
  - Responses must not include plaintext password or password hash fields.
  - Required permission: `admin:account:write`.

- `PUT /api/admin/accounts/{accountId}/status`: update account status.
  - Request body: `{ "status": "ACTIVE" | "DISABLED" | "LOCKED" }`.
  - Returns: updated `AccountSummary`.
  - Required permission: `admin:account:write`.

- `PUT /api/accounts/{accountId}/password`: change an account password.
  - Request body: `accountId`, optional `oldPassword`, `newPassword`.
  - Returns: `boolean` success flag.
  - Self-service password changes require the authenticated user to match `accountId`; admin resets require `admin:account:write`.
  - Responses must not include plaintext password or password hash fields.

- `GET /api/auth/permissions`: get current authenticated permissions.
  - Returns: `CurrentPermissions` with `userId`, `role`, and `permissions`.
  - Frontend views should prefer these permission codes for RBAC gating and keep role checks only as a coarse fallback.

- Frontend fallback:
  - Without `VITE_API_BASE_URL` or `VITE_API_PROXY_TARGET` in development, account and RBAC client functions return deterministic demo data and do not call `fetch`.
  - Demo role permission defaults are `STUDENT`: `student:profile:read`, `student:resume:write`, `student:delivery:write`, `student:interview:write`; `COMPANY`: `company:job:write`, `company:delivery:read`, `company:screening:write`; `ADMIN`: `admin:dashboard:read`, `admin:account:read`, `admin:account:write`, `admin:rbac:read`, `admin:ai-observability:read`.

## Admin

- `GET /api/ai/observability/summary`: AI observability summary for the admin console.
  - Returns: `AiObservabilitySummary` with `provider`, `model`, `configured`, `totalCalls`, `successCalls`, `failedCalls`, `mockedCalls`, `successRate`, `averageLatencyMs`, `recentCalls`, and `generatedAt`.
  - All responses use `ApiResponse<AiObservabilitySummary>`.
  - The endpoint must not return prompt bodies, API keys, tokens, or other secret values.

- `GET /api/ai/observability/calls?limit=20&provider=&success=`: recent AI call records for troubleshooting.
  - Query parameters: `limit` defaults to 20; `provider` is optional; `success` is optional and accepts `true` or `false`.
  - Returns: `AiCallRecord[]`.
  - `AiCallRecord` fields: `callId`, `operation`, `provider`, `model`, `success`, `mocked`, `durationMs`, `promptChars`, `responseChars`, optional `fallbackReason`, and `createdAt`.
  - The endpoint must redact prompts, resume text, job descriptions, credentials, and tokens.

- `POST /api/ai/search`: intelligent search across recruitment data.
  - Request body: `{ "query": "Java backend", "role": "ADMIN", "limit": 5 }`; `role` and `limit` are optional.
  - Returns: `AiSearchResponse` with `query`, `results`, and `generatedAt`.
  - Each result includes `id`, `type`, `title`, `owner`, `summary`, `score`, and `highlights`.
  - Frontend demo mode returns deterministic fallback results when no gateway or AI proxy is configured.

- `GET /api/admin/system/status`: backend management status summary for the admin console.
  - Returns: `generatedAt`, `applicationName`, `profile`, stable service entries for `gateway/auth/user/resume/job/match/ai/delivery`, persistence settings, infrastructure settings, and warnings.
  - `services` items include `name`, `displayName`, `defaultPort`, `port`, `healthPath`, `status`, and `note`; `port` reflects the configured `*_SERVICE_URI` port when present.
  - `persistence` items cover `resume/job/match/delivery/aiScreening` with `module`, `enabled`, `database`, `cacheKeyPrefix`, `note`, and `notes`.
  - `infrastructure` items cover `nacos/mysql/redis/minio/rocketmq` with `name`, `host`, `port`, `configured`, `status`, and `note`.
  - The endpoint reads configuration from environment/properties and never returns password, secret, or API key values.

- `GET /api/admin/system/topology`: deployment topology summary for the three-VM admin console.
  - Returns: `generatedAt`, `profile`, `environment`, `nodes`, and `warnings`.
  - `nodes` items include `id`, `name`, `host`, `role`, and `services`; default hosts are `VM1_HOST=192.168.56.11`, `VM2_HOST=192.168.56.12`, and `VM3_HOST=192.168.56.13`.
  - Default distribution follows `deploy/docker-compose.vm1.yml`, `vm2.yml`, `vm3.yml`, and `deploy/three-vm.env.example`: VM1 has `frontend/gateway-service/nacos`; VM2 has `auth-service/user-service/resume-service/job-service/match-service/delivery-service`; VM3 has `mysql/redis/minio/rocketmq/ai-service`.
  - `services` items include `name`, `displayName`, `port`, `healthUrl`, `status`, and `note`; `FRONTEND_PORT`, `GATEWAY_PORT`, `NACOS_PORT`, `AUTH_PORT`, `USER_PORT`, `RESUME_PORT`, `JOB_PORT`, `MATCH_PORT`, `DELIVERY_PORT`, `MYSQL_PORT`, `REDIS_PORT`, `MINIO_PORT`, `ROCKETMQ_PORT`, and `AI_PORT` can override displayed ports.
  - The endpoint is configuration-only, does not probe network health, and never returns password, secret, token, or API key values.

- `GET /api/admin/system/deployment-guide`: generated startup guide for the three-VM deployment.
  - Returns: `generatedAt`, `environment`, `summary`, `steps`, `acceptanceChecks`, and `warnings`.
  - Default step order is VM3 data and AI services first, VM1 discovery/gateway/frontend second, VM2 business services third, and all-node health/API smoke checks last.
  - `steps` items include `order`, `nodeId`, `nodeName`, `title`, `purpose`, `commands`, `verifyUrls`, `expectedResult`, and `troubleshooting`.
  - `acceptanceChecks` items include `name`, `command`, and `expectedResult`.
  - Hosts and ports are generated from `VM1_HOST`, `VM2_HOST`, `VM3_HOST`, `FRONTEND_PORT`, `GATEWAY_PORT`, `NACOS_PORT`, `AUTH_PORT`, `USER_PORT`, `RESUME_PORT`, `JOB_PORT`, `MATCH_PORT`, `AI_PORT`, `DELIVERY_PORT`, `MYSQL_PORT`, `REDIS_PORT`, `MINIO_PORT`, and `ROCKETMQ_PORT`, with defaults aligned to `deploy/three-vm.env.example` and the VM compose files.
  - The endpoint only generates deployment instructions, does not probe network health, and never returns credential values.

- `GET /api/admin/dashboard`：学校端统计看板。
  - 返回：`studentCount`、`companyCount`、`jobCount`、`deliveryCount`、`averageMatchScore`、`deliveryStatusCounts`、`pendingDeliveryCount`。

## v2.9 Admin Audit Data Center

- `GET /api/admin/audit/overview`: cross-service audit overview for the admin console.
  - Query parameters are optional: `keyword`, `entityType`, `studentId`, `companyId`, `jobId`, and `limit`.
  - `entityType` supports `STUDENT`, `JOB`, `DELIVERY`, `AI_SCREENING`, and `AI_INTERVIEW`.
  - Returns: `ApiResponse<AdminAuditOverview>`.
  - `AdminAuditOverview` fields: `generatedAt`, `source`, `query`, `metrics`, `records`, and `warnings`.
  - `metrics` items include `key`, `label`, `value`, and optional `unit`.
  - `records` items include `auditId`, `entityType`, `entityId`, `title`, `ownerId`, optional `studentId`, optional `companyId`, optional `jobId`, `service`, `status`, `riskLevel`, optional `score`, `summary`, `tags`, and `occurredAt`.
  - The endpoint must not return API keys, tokens, raw AI prompts, full resume text, password hashes, or other secret values.
  - Expected permission: `admin:audit:read`.

- `POST /api/admin/audit/export`: create an admin audit export task.
  - Request body accepts the same filters as overview plus `format`, initially `CSV`.
  - Returns: `ApiResponse<AdminAuditExportResult>`.
  - `AdminAuditExportResult` fields: `exportId`, `format`, `fileName`, `downloadUrl`, `expiresAt`, `rowCount`, `generatedAt`, and `query`.
  - The export must apply the same redaction rules as the overview endpoint.
  - Expected permission: `admin:audit:export`.

- Frontend fallback:
  - Without `VITE_API_BASE_URL` or `VITE_API_PROXY_TARGET` in development, audit client functions return deterministic demo data and do not call `fetch`.
  - `/admin/audit` uses the same Vue route family as other admin modules and is backed by `GET /api/admin/audit/overview` and `POST /api/admin/audit/export` once the backend is available.

## v3.2 AI Screening Task Persistence

- `POST /api/ai/candidates/screen/tasks`, `GET /api/ai/candidates/screen/tasks`, `GET /api/ai/candidates/screen/tasks/{taskId}`, and `POST /api/ai/candidates/screen/tasks/{taskId}/retry` keep the same response contracts.
- When `AI_SCREENING_PERSISTENCE_ENABLED=true` and datasource settings are present, async task state is stored in MySQL table `ai_candidate_screen_task` with the original `CandidateScreenRequest` snapshot and optional `CandidateScreenResult` snapshot.
- On service restart, persisted `PENDING` or `RUNNING` tasks are marked `FAILED` with a retryable message instead of remaining stuck forever.
- RocketMQ-created tasks use a `dedup_key` derived from `DELIVERY_CREATED` and `deliveryId`; repeated delivery messages return the existing task instead of creating duplicates.
- Retry still creates a new task and keeps the original failed task for auditability. The retry uses the persisted request snapshot, so it works after service restart.
