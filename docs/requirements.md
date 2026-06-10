# 需求规格说明书

## 项目目标

建设一个面向高校校园招聘的 AI 简历诊断与岗位匹配平台，覆盖学生、企业和学校就业办三类角色。系统通过大模型分析简历和岗位 JD，生成简历诊断、岗位匹配度和模拟面试题，并通过微服务架构支持分布式部署。

## 用户角色

- 学生：维护个人资料，上传简历，查看 AI 诊断，浏览推荐岗位，投递岗位，进行模拟面试并查看回答反馈。
- 企业：维护企业信息，发布岗位，查看候选人匹配度和 AI 初筛建议，推进投递状态。
- 学校就业办：查看学生、企业、岗位、投递和匹配统计，辅助就业指导。

## 功能需求

- 登录认证：支持学生、企业、学校管理员三类演示账号。
- 简历管理：上传简历文件，查看解析摘要，触发 AI 诊断。
- 岗位管理：企业发布岗位，维护岗位要求，触发 JD 分析。
- 智能匹配：基于简历技能和岗位要求生成匹配度、优势、短板和建议。
- AI 候选人初筛：企业查看投递候选人时，可基于简历摘要、项目经历和岗位要求生成初筛分数、推荐结论、优势、风险、面试追问和下一步动作，并查看本企业筛选历史。
- 投递流程：学生投递岗位，企业查看本企业投递列表，并推进为已查看、面试中、已录用或未通过。
- 模拟面试：学生完成岗位匹配或投递后，按目标岗位生成面试题，提交回答并查看评分、优势、不足、改进建议和历史记录。
- 学校看板：展示学生数、岗位数、投递数、平均匹配度、投递状态分布和待处理投递数。
- AI 能力：接入阿里云百炼生成诊断、候选人初筛、面试题与回答反馈；提供 AI 模块状态接口展示 provider、model、配置状态和能力列表；未配置 Key 或调用失败时自动使用 mock 响应，保证演示可用。

## 非功能需求

- 服务可拆分部署，支持三台虚拟机分布式运行。
- 所有接口提供统一响应结构。
- 支持 Docker Compose 一键启动基础设施。
- 国内网络环境可下载依赖和镜像。
- 关键接口具备降级策略，AI 调用失败不阻断主流程。

## 迭代计划

- MVP：完成三端登录、简历上传、岗位发布、AI 诊断、匹配、投递、学校看板。
- v0.2：补齐学生、企业、学校三端投递审核闭环，支持企业投递审核列表、状态推进、学生端中文状态、学校端状态分布。
- v0.3：新增学生端 AI 模拟面试闭环，支持生成面试题、提交回答、获取结构化反馈及离线演示降级。
- v0.4：完善 AI 模块工程化能力，新增状态接口、结构化 JSON 输出约束、服务层编排与更稳定的降级策略。
- v0.5：新增学生端 AI 状态展示和模拟面试历史记录，提交反馈后自动保存本次面试结果。
- v0.6：新增 AI 候选人初筛闭环，企业端可对投递记录生成筛选分数、推荐结论、风险点、面试追问和下一步动作，后端支持结构化 JSON 输出和确定性 mock 降级。
- v0.7：新增 AI 候选人筛选历史闭环，筛选结果在 AI 服务内存中保存，可按企业和投递查询，企业端进入页面自动加载历史并在筛选后刷新。
- v0.8：AI 候选人筛选历史支持 MySQL 持久化和 Redis 查询缓存，默认保留内存回退，Docker Compose 中 ai-service 可直接连接 MySQL/Redis。
- v0.9：完成三虚拟机分布式部署规划与 Compose 编排，VM1 承载 Nacos/Gateway/前端，VM2 承载业务服务，VM3 承载 AI 服务与 MySQL/Redis/MinIO/RocketMQ；前端 Nginx 支持通过环境变量切换 Gateway 上游。
- v1.0：补齐三虚拟机健康检查脚本和运维基线，支持 Windows PowerShell 与 Linux bash 从统一 env 文件读取 VM 地址，检查前端、Gateway、Nacos、业务服务、AI 服务及 MySQL/Redis/MinIO/RocketMQ 基础可达性；文档补充基础监控和日志排障命令。
- v1.1：接入 MinIO 简历文件对象存储，简历上传返回对象 key、provider 和写入状态，Docker Compose 与三机部署可直接指向 MinIO，并保留本地降级。
- v1.2：接入 RocketMQ 投递事件，创建投递和更新状态会生成事件并尝试发布到 `delivery-events` topic，提供最近事件查询与失败降级状态。
- v1.3：补齐自动化回归和部署验收脚本，覆盖三机健康检查、关键 API smoke 和失败出口码，提供 PowerShell 与 bash 两套入口。
- v1.4：企业端 AI 初筛和筛选历史透传简历解析格式、解析状态和正文长度，便于 HR 判断筛选依据质量。
- v1.5：投递记录和投递事件保存简历解析快照，企业投递审核列表可直接看到候选人简历解析质量。
- v1.6：投递记录支持可选 MySQL 持久化和 Redis 企业列表缓存，默认保留内存回退；单机 Compose 与三虚拟机部署可直接将 `delivery-service` 连接到 MySQL/Redis。
- v1.7：岗位发布和 AI 岗位分析结果支持可选 MySQL 持久化和 Redis 岗位列表缓存，默认保留内存回退；单机 Compose 与三虚拟机部署可直接将 `job-service` 连接到 MySQL/Redis。
- v1.8：匹配结果支持可选 MySQL 持久化和 Redis 学生/岗位查询缓存，默认保留内存回退；生成新匹配后清理相关缓存，保证学生端和企业端匹配视图可跨服务重启保留。
- v1.9：简历摘要、诊断结果和抽取正文支持可选 MySQL 持久化和 Redis 详情缓存，默认保留内存回退；服务重启后仍可基于已抽取正文继续 AI 诊断。
- v2.3：完成生产级鉴权基础版，登录成功返回 JWT，前端自动携带 `Authorization: Bearer <token>` 调用受保护 API；新增 `/api/auth/me` token 验证流程；JWT 和 Gateway 鉴权通过 `JWT_SECRET`、`JWT_ISSUER`、`JWT_TTL_SECONDS`、`GATEWAY_AUTH_ENABLED` 等环境配置控制，并保留 `STUDENT`、`COMPANY`、`ADMIN` 三类基础角色边界。

## v2.4 Trusted Identity Requirement

- Business services must prefer gateway-injected `X-User-Id` and `X-User-Role` for student/company owned data after JWT authentication.
- Student-owned flows include profile, resume upload, match generation/query, delivery creation/query, and AI interview questions/feedback/history.
- Company-owned flows include job publishing, company delivery query, AI candidate screening, and candidate screening history.
- Direct service calls without gateway identity headers must keep the existing demo fallback values so each service remains independently runnable.
- `ADMIN` requests keep cross-tenant query behavior for management and review screens.

## v2.5 Frontend Session Identity Requirement

- Login must persist the authenticated `userId` as part of the frontend session together with token, role, display name, and session metadata.
- Student-owned frontend API calls should derive `studentId` from the current session before using the legacy `S001` demo fallback.
- Company-owned frontend API calls should derive `companyId` from the current session before using the legacy `C001` demo fallback.
- Admin workflows may keep explicit target ids for cross-role review and management screens.
- Verification must include frontend unit tests, frontend build, and a browser UI check across student, company, and admin demo sessions.

## v2.6 Production User and RBAC Requirement

- The platform must prepare for a production-grade user system with account lifecycle management, password maintenance, account status control, and fine-grained permission discovery.
- `ADMIN` users can list accounts, create accounts, and update account status for student, company, and admin users.
- Users or authorized admins can change account passwords through a protected API; plaintext passwords must never be returned by any response.
- The frontend must be able to query current permissions for the logged-in user and render RBAC-gated views from permission codes instead of role names alone.
- Permission codes should be stable string identifiers, for example `admin:account:read`, `admin:account:write`, `company:screening:write`, and `student:resume:write`.
- All account and permission APIs continue to use `ApiResponse<T>` and must keep deterministic frontend fallback data so the demo remains usable when no gateway is configured.
- Production credentials and signing secrets must remain environment-driven and must not be hardcoded in frontend or documentation examples.

## v2.7 AI Observability and Intelligent Search Requirement

- Admin users need an AI operations module that summarizes total calls, success/failure counts, mocked calls, success rate, average latency, and recent-call provider/task breakdowns.
- Admin users need a recent AI call list filtered by provider, success flag, and limit for compact troubleshooting from the frontend dashboard.
- Admin users need an intelligent search form that submits a query, optional role, and optional limit, then displays ranked results with type, owner, summary, score, and highlights.
- The frontend client must target `GET /api/ai/observability/summary`, `GET /api/ai/observability/calls`, and `POST /api/ai/search`, and all responses continue to use `ApiResponse<T>`.
- Demo mode must keep deterministic fallback data and avoid calling `fetch` when no gateway or AI proxy is configured.
- Observability and search responses must not expose API keys, tokens, raw prompts, full resume text, or other sensitive payloads.

## v2.8 Resume File and Data Loop Requirement

- Student users need a compact resume lifecycle view that connects resume file upload and parse status, AI diagnosis, delivery snapshots created from that resume, and AI candidate-screening feedback from companies.
- The frontend client must target `GET /api/ai/screenings/my?studentId=S001` through `listMyCandidateScreenRecords(studentId?)`; all responses continue to use `ApiResponse<CandidateScreenRecord[]>`.
- Gateway-injected `STUDENT` identity must override the query `studentId`, so students can only see their own screening feedback. `ADMIN` may query by student id for review.
- Company users keep using `/api/ai/candidates/screenings`; the student lifecycle UI should not depend on company-only endpoints.
- Demo mode must keep deterministic fallback data and avoid calling `fetch` when no gateway or AI proxy is configured.
- Screening feedback displayed to students must not expose raw prompts, API keys, tokens, or hidden employer-only notes.

## v2.9 Admin Audit Data Center Requirement

- Admin users need a compact audit data center at `/admin/audit` for cross-service review of students, jobs, deliveries, AI candidate-screening records, and AI interview records.
- The frontend client must target `GET /api/admin/audit/overview` for query results and `POST /api/admin/audit/export` for CSV export preparation; all responses continue to use `ApiResponse<T>`.
- Audit query filters should include `keyword`, `entityType`, `studentId`, `companyId`, `jobId`, and `limit`, with `entityType` covering `STUDENT`, `JOB`, `DELIVERY`, `AI_SCREENING`, and `AI_INTERVIEW`.
- The UI must remain an admin tool: dense filters, metrics, table rows, and mobile-safe wrapping instead of a marketing layout.
- Demo mode must keep deterministic fallback data and avoid calling `fetch` when no gateway or API proxy is configured.
- Audit and export payloads must not expose API keys, tokens, raw prompts, full resume text, password hashes, or hidden employer-only notes.
- Expected permission codes are `admin:audit:read` and `admin:audit:export`.

## v3.0 AI Async Candidate Screening Requirement

- Company users need an asynchronous candidate-screening flow so clicking the delivery review action creates a task instead of blocking on a synchronous AI result.
- `ai-service` must own async screening task state and keep a bounded in-memory fallback so the service remains independently runnable without RocketMQ, MySQL, or Redis.
- Completed async tasks must call the existing candidate screening logic and write a normal screening history record for compatibility with older pages and audit data.
- When `AI_SCREENING_ROCKETMQ_ENABLED=true`, `ai-service` should consume `DELIVERY_CREATED` events from the configured `delivery-events` topic and create `ROCKETMQ` source screening tasks.
- The frontend client must target `POST /api/ai/candidates/screen/tasks` with the existing `CandidateScreenRequest` body and `GET /api/ai/candidates/screen/tasks?companyId=&deliveryId=` for task history; all responses continue to use `ApiResponse<T>`.
- `CandidateScreenTask` includes `taskId`, `deliveryId`, `companyId`, `studentId`, `resumeId`, `jobId`, `status`, `source`, `message`, optional `result`, `createdAt`, and `updatedAt`.
- Task `status` supports `PENDING`, `RUNNING`, `COMPLETED`, and `FAILED`; `source` supports `DEMO`, `RUNTIME`, and `ROCKETMQ`.
- The company delivery review action should be labeled as asynchronous screening, and the AI screening history view must show task status, source, message, timestamps, and completed `result` details when available.
- The company UI must remain compact and mobile-safe: task identifiers, status tags, and result lists should wrap without forcing horizontal overflow.
- Demo mode must keep deterministic fallback task data, including at least one completed task with `result` and one in-progress task, and avoid calling `fetch` when no gateway or AI proxy is configured.
- No frontend or documentation examples may hardcode AI provider keys, tokens, raw prompts, or full resume text.
- Acceptance commands for this slice: `mvn -s settings.xml.example -pl common,ai-service -am test` from `backend/`, `npm run test:unit` from `frontend/`, and `npm run build` after TypeScript/template changes.

## v3.1 AI Screening Task Recovery Requirement

- Company users need task-level operations on async candidate-screening tasks: refresh a single task and retry a failed task from the company screening view.
- `ai-service` must expose `GET /api/ai/candidates/screen/tasks/{taskId}` and `POST /api/ai/candidates/screen/tasks/{taskId}/retry`, both wrapped in `ApiResponse<T>`.
- Gateway-injected `COMPANY` identity must override query parameters so companies cannot view or retry another company's task.
- Retry is allowed only for `FAILED` tasks; pending, running, completed, missing, or company-mismatched tasks must return a controlled failure response.
- A successful retry should enqueue a new task using the original `CandidateScreenRequest` snapshot and preserve the original task for auditability.
- The frontend client must expose `getCandidateScreenTask(taskId, companyId?)` and `retryCandidateScreenTask(taskId, companyId?)` with deterministic fallback data.
- Demo mode must include at least one failed task so the retry action can be demonstrated without a backend.
- The company UI must keep retry and refresh controls compact and mobile-safe.
- No retry response may expose API keys, raw prompts, full resume text, or hidden employer-only notes.

## v3.2 Distributed AI Screening Completion Requirement

- Async candidate-screening task state must survive `ai-service` restart when `AI_SCREENING_PERSISTENCE_ENABLED=true`.
- Persisted task records must include the original `CandidateScreenRequest` snapshot and optional `CandidateScreenResult` snapshot, without storing API keys, prompts, or full resume text.
- On startup, persisted `PENDING` and `RUNNING` tasks must be converted to retryable `FAILED` tasks so company users can recover them.
- Failed task retry must use the persisted request snapshot and create a new task while preserving the original failed task.
- RocketMQ `DELIVERY_CREATED` consumption must be idempotent for a delivery: repeated messages for the same delivery must return the existing `ROCKETMQ` task instead of creating duplicates.
- Three-VM deployment verification must produce a timestamped report covering vmrun availability, VMX paths, VM running status, Compose configuration, and key service HTTP/TCP checks.
- Acceptance commands for this slice include backend tests, frontend tests/build, Docker Compose config checks, and the three-VM smoke script in dry-run or real mode depending on VM availability.
