# API 文档

知识库新工作台接口、文件阅读、个人记录、练习及发布流程见 [知识库学习工作台](knowledge-workspace.md)。

所有接口返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

## Gateway 认证与请求追踪

- `GATEWAY_PUBLIC_DOCS_ENABLED=false` 时，聚合 OpenAPI、Swagger 配置和文档页面均要求有效的 Bearer Token。
- `/actuator/health` 及其子路径保持公开；其他 Gateway Actuator 路径要求管理员权限。
- `GATEWAY_ALLOWED_ORIGINS` 控制浏览器来源。允许的来源在 401/403 响应中也会获得 CORS 响应头，跨域预检在认证之前处理。
- Gateway 为响应和下游请求设置 `X-Request-Id`，包括健康检查、预检及认证失败响应。客户端可提供 1-64 位字母、数字、点、下划线或连字符；不符合格式时重新生成编号。
- Gateway 清除外部请求中的身份头；受保护请求验证 Token 后才设置可信的用户和角色信息。

## API 文档入口

### 毕设五大核心模块补充

前端仅展示简历诊断、岗位匹配、学习路径、模拟面试和知识问答；岗位维护、账号与知识库管理作为配套。下文其他辅助接口保留兼容，并不表示前端仍提供入口。

所有个人记录以网关验证后注入的 `X-User-Id` 为归属依据，客户端传入的 `studentId` 不得覆盖登录身份。核心持久化写入失败返回失败响应，不能将内存副本作为保存成功。账号及学生资料分别通过 `AUTH_PERSISTENCE_ENABLED`、`USER_PERSISTENCE_ENABLED` 保存，学习路径与面试会话通过 `AI_CORE_PERSISTENCE_ENABLED` 保存。

简历与岗位：

- `PATCH` 或 `PUT /api/resumes/{id}/profile`：本人修正 `education`、`skills`、`projects`，返回更新后的 `ResumeSummary`。字段实际变化时重新计算证据分，当前诊断改为“请重新生成诊断”的提示，已有历史快照保留；无变化保存保留当前报告与评分。
- `GET /api/resumes/{id}/diagnoses`：本人诊断历史；每条记录包含诊断编号、目标岗位、报告、评分、来源和生成时间，后续诊断不覆盖旧记录。
- `POST /api/resumes/{id}/analyze`：保留原接口，按真实正文及目标岗位分析，取消固定高分。无法抽取的扫描文档应先转为文本型 PDF/DOCX。
  - 诊断历史 `source=AI_STRUCTURED` 表示 AI 报告与可识别评分；`AI_TEXT_RULE_SCORE` 保留真实 AI 报告，分数使用规则证据分；`RULE_FALLBACK` 表示模型不可用时的规则建议。报告不会因为缺少数值评分而被丢弃。
- `PUT /api/jobs/{id}`：企业修改本人岗位，使用岗位创建字段；学生不可写入。
- `POST /api/jobs/{id}/status`：请求 `{ "status": "OPEN" }` 或 `{ "status": "CLOSED" }`。关闭岗位不对学生列出或参与新匹配，企业本人仍可管理。
- `POST /api/matches/resume-job`：必须引用存在且有权限的简历和岗位；`score` 为技能覆盖率，不是录用概率。新增 `matchedSkills`、`missingSkills`、`analysisSource`、`resumeSkillsSnapshot`、`requiredSkillsSnapshot`，取消固定 88 分。
  - 关闭的岗位不允许创建匹配，包括管理员请求。岗位未配置任何技能要求时，`analysisSource=RULE_INSUFFICIENT_JOB_SKILLS`，数字字段为兼容返回 `score=0`，页面应展示“匹配依据不足”而非有效的 0% 评分；`gaps` 和 `suggestions` 说明需补充岗位要求。简历未提取到技能时，有要求的岗位会列出全部缺失技能。

学习路径：

- `POST /api/ai/learning/plans`：请求 `resumeId`、`jobId`、可选 `matchId`、`targetRole`、`weeklyHours`、`durationWeeks`。每周 2–40 小时、周期 1–24 周；默认每周 6 小时、8 周，返回 `LearningPlan`。
- `GET /api/ai/learning/plans`、`GET /api/ai/learning/plans/{planId}`：列出本人计划或读取详情。
- `PUT /api/ai/learning/plans/{planId}/tasks/{taskId}`：更新任务 `status` 和 `feedback`，返回 `LearningTask`。支持 `PENDING`、`IN_PROGRESS`、`COMPLETED`、`SKIPPED`；仅 `ACTIVE` 计划可修改。
- `POST /api/ai/learning/plans/{planId}/replan`：请求调整 `reason`、可选 `weeklyHours`、`durationWeeks`、`interviewSessionId`。引用面试报告时，报告必须属于本人、已完成、目标岗位一致，且 `resumeId`、`jobId`、`matchId` 与该计划一致。缩短周期或预算不能排除已完成任务；模型生成或保存失败时保留原计划，成功时在同一事务中新增版本并将旧版标记为 `SUPERSEDED`。
- `GET /api/ai/learning/plans/{planId}/versions`：读取同一学习路径的历史版本。
- `LearningPlan` 包含编号、根计划编号、版本、上一版本、目标岗位、输入快照、周期、状态、任务及 `mocked`。任务包括周次、技能差距、阶段、具体行动、预计用时、验收标准、实践成果及进度反馈。调整只替换未完成部分，保留已完成任务身份和记录。
- 任务更新和重规划使用原记录快照作为条件写入。冲突后重新读取并有限重试，旧页面的整条计划不能覆盖已保存的其他任务；重规划条件写入失败不创建新版本。全部任务完成后计划状态变为 `COMPLETED`，已完成与已替代版本均只读。

模拟面试：

- `POST /api/ai/interview/sessions`：请求 `resumeId`、`jobId`、可选 `matchId`、`targetRole`、`questionCount`；允许 1–8 道主问题，默认 5 道。新增 `mode`（`COACHING`/`MOCK`）、`sourceType`（`JOB`/`PROJECT`/`GAP`）、`sourceId`、`timerMinutes`，具体见“模拟面试来源、练习和复盘”。
- `GET /api/ai/interview/sessions`、`GET /api/ai/interview/sessions/{sessionId}`：读取本人会话列表和详情，可在刷新后继续。
- `PUT /api/ai/interview/sessions/{sessionId}/questions/{questionId}/answer`：请求 `{ "questionId": "...", "answer": "..." }`，按当前题顺序首次提交；相同请求幂等，重答使用新增尝试接口。
- `POST /api/ai/interview/sessions/{sessionId}/finish`：完成面试并返回整场报告，包含总体评分、优势、薄弱项、改进建议及逐题反馈。
- 会话保存目标与简历快照、题目、回答、进度及报告。辅导练习可以查看参考；模拟面试作答期间隐藏参考、评价和追问。每道主问题最多追加一次追问，旧单题接口保留兼容。
- 首次回答按顺序保存，兼容接口不允许覆盖；辅导重答使用独立尝试记录并由学生明确采用。相同文本提交幂等。辅导练习需回答主问题与已生成追问，模拟面试只需回答主问题；已完成会话只读，重复完成返回已保存报告。回答和报告落库使用快照条件写入，避免并发覆盖。

RAG 沿用 `/api/ai/knowledge/*` 接口。检索和回答使用认证角色，正文引用对应真实知识块；文档删除或角色修改后必须同步影响检索结果。

各 MVC 业务服务已集成 Knife4j，网关提供聚合文档页 `/doc.html`，可在一个页面切换查看认证、用户、简历、岗位、匹配、AI/RAG、投递通知等全部微服务接口。单个业务服务也保留 `/doc.html` 调试入口，同时保留 OpenAPI JSON `/v3/api-docs` 和原 Swagger UI `/swagger-ui.html` 兼容入口。

本地常用入口：

- 网关聚合文档：`http://localhost:18080/doc.html` 或 `http://localhost:8080/doc.html`
- `auth-service`: `http://localhost:8101/doc.html`
- `user-service`: `http://localhost:8102/doc.html`
- `resume-service`: `http://localhost:8103/doc.html`
- `job-service`: `http://localhost:8104/doc.html`
- `match-service`: `http://localhost:8105/doc.html`
- `ai-service`: `http://localhost:8106/doc.html`
- `delivery-service`: `http://localhost:8107/doc.html`

网关聚合 OpenAPI JSON：

- `GET /v3/api-docs/swagger-config`：Knife4j 聚合配置。
- `GET /v3/api-docs/auth-service`：认证服务接口。
- `GET /v3/api-docs/user-service`：用户与管理服务接口。
- `GET /v3/api-docs/resume-service`：简历服务接口。
- `GET /v3/api-docs/job-service`：岗位服务接口。
- `GET /v3/api-docs/match-service`：匹配服务接口。
- `GET /v3/api-docs/ai-service`：AI 与 RAG 服务接口。
- `GET /v3/api-docs/delivery-service`：投递、通知与面试日程服务接口。

## Auth

- `POST /api/auth/login`：登录。
- `POST /api/auth/logout`：登出。Gateway 要求携带有效 Bearer Token，并在后续网关请求中拒绝已登出的 Token；GET 和 OPTIONS 请求不会触发登出。失效记录在当前 Gateway 进程中保留至 Token 到期，重启后清空，多个 Gateway 实例之间不共享。每次登录独立签发 Token，重新登录不会复用已登出的 Token。
- `GET /api/auth/me`：获取当前用户。

## Resume

- `POST /api/resumes/upload`：上传简历，支持 PDF、DOC、DOCX；可解析文本的文件会抽取正文并在后续诊断中优先传给 AI。
  - 返回：`ResumeSummary`，包含 `resumeId`、`studentId`、`fileName`、`education`、`skills`、`projects`、`diagnosis`、`score`、`objectKey`、`storageProvider`、`storageStatus`、`sourceFormat`、`parseStatus`、`parsedTextLength`。
  - `storageProvider=local-demo` 且 `storageStatus=SKIPPED` 表示对象存储未开启；`storageProvider=minio` 且 `storageStatus=STORED` 表示文件已写入 MinIO；`FAILED` 表示写入 MinIO 失败但上传主流程已降级继续。
  - 默认使用内存仓储；设置 `RESUME_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `resume_summary_record`，并保存抽取正文供后续 AI 诊断使用。
- `GET /api/resumes`：查看简历列表。
  - 返回：`ResumeSummary[]`，按登录身份返回可见简历摘要；启用持久化时从 MySQL 读取，数据库不可用时返回失败，不降级成内存保存。
- `GET /api/resumes/{id}`：查看简历摘要。
  - 简历详情使用 Redis cache-aside 缓存，key 格式：`resume:summaries:detail:{resumeId}`。
  - `RESUME_DB_HEALTH_ENABLED` 与 `RESUME_REDIS_HEALTH_ENABLED` 默认关闭，避免本地未启动 MySQL/Redis 时影响演示健康状态。
- `DELETE /api/resumes/{id}`：删除简历。
  - 返回：`ApiResponse<Boolean>`；删除成功时 `data=true`，未知 ID 返回 `ApiResponse.fail("Resume not found")`。
  - 启用持久化时会删除 MySQL 行并清理 Redis 详情缓存 `resume:summaries:detail:{resumeId}`。
- `POST /api/resumes/{id}/analyze`：触发 AI 简历诊断。
  - 请求体：`{"targetJob":"小学语文教师"}`；`targetJob` 必填，学生端传当前选择的诊断职业，不预设专业。
  - 服务重启后，若启用持久化，诊断仍会优先使用表内保存的 `parsed_text`。
  - 诊断结果要求为中文 Markdown，建议结构包括 `## 结论`、`## 优势`、`## 短板`、`## 修改建议`、`## 示例改写`，且不包裹 ```markdown 代码块。
  - `resume-service` 通过 OpenFeign 调用 `ai-service` 的 `POST /api/ai/analyze`，地址由 `AI_SERVICE_URI` / `services.ai` 配置；AI 服务不可用时返回本地降级诊断文案。

## Job

- `POST /api/jobs`：发布岗位。
- `GET /api/jobs`：岗位列表。
  - 设置 `DEMO_SEED_ENABLED=true` 时初始化跨行业样例岗位，已有岗位不会被覆盖；普通启动不自动添加演示岗位。
  - 默认使用内存仓储；设置 `JOB_PERSISTENCE_ENABLED=true` 且提供 `SPRING_DATASOURCE_URL` 后写入 MySQL 表 `job_record`。
  - 岗位列表使用 Redis cache-aside 缓存，key 格式：`job:records:list:ALL`。
  - `JOB_DB_HEALTH_ENABLED` 与 `JOB_REDIS_HEALTH_ENABLED` 默认关闭，避免本地未启动 MySQL/Redis 时影响演示健康状态。
- `GET /api/jobs/{id}`：岗位详情。
- `POST /api/jobs/{id}/analyze`：触发 AI 岗位分析。
  - `job-service` 通过 OpenFeign 调用 `ai-service` 的 `POST /api/ai/analyze`，地址由 `AI_SERVICE_URI` / `services.ai` 配置；AI 服务不可用时返回本地降级岗位分析文案。

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
  - `capabilities` 包含 `resume-analysis`、`job-analysis`、`match-analysis`、`resume-rewrite`、`career-planning`、`planning-history`、`coach-advice`、`interview-question-generation`、`interview-feedback`、`candidate-screening`、`observability`、`intelligent-search`；调用方可据此决定是否展示对应入口。
  - 不返回任何 API Key；`configured=false` 时表示会进入离线演示降级。
- `POST /api/ai/analyze`：通用 AI 分析接口，供简历诊断、岗位分析和匹配分析复用。
  - 请求体：`taskType`、`content`、`context`。
  - 返回：`AiAnalyzeResponse`，包含 `taskType`、`provider`、`content`、`mocked`。
- `POST /api/ai/resume/rewrite`：面向学生的 AI 简历改写接口，基于目标岗位、简历摘要、技能和项目经历生成可直接用于简历优化的结构化建议。
  - 请求体：`studentId`、`resumeId`、`targetRole`、`resumeSummary`、`skills`、`projects`。
  - 返回：`ResumeRewriteResponse`，包含 `studentId`、`resumeId`、`targetRole`、`improvedSummary`、`rewrittenProjects`、`keywordSuggestions`、`missingEvidence`、`actionChecklist`、`mocked`。
  - Gateway 已注入 `X-User-Role=STUDENT` 和 `X-User-Id` 时，下游服务必须以注入的学生身份为准，覆盖请求体中的 `studentId`。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时返回确定性的演示改写建议，且 `mocked=true`；响应不得返回 API Key、原始提示词、完整简历正文或其他敏感信息。
- `POST /api/ai/career/plan`：面向学生的 AI 职业规划接口，围绕目标岗位生成阶段里程碑、技能差距、每周行动、作品集任务和面试准备重点。
  - 请求体：`studentId`、`targetRole`、`skills`、`interests`、`resumeSummary`、`timeframeWeeks`。
  - 返回：`CareerPlanResponse`，包含 `studentId`、`targetRole`、`readinessScore`、`summary`、`milestones`、`skillGaps`、`weeklyActions`、`portfolioTasks`、`interviewFocus`、`mocked`。
  - `milestones` 每项包含 `title`、`timeframe`、`goals`。
  - Gateway 已注入 `X-User-Role=STUDENT` 和 `X-User-Id` 时，下游服务必须以注入的学生身份为准，覆盖请求体中的 `studentId`。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时返回确定性的演示规划，且 `mocked=true`；响应不得返回 API Key、原始提示词、完整简历正文或其他敏感信息。
- `POST /api/ai/coach/advice`：面向学生的 AI 求职顾问接口，基于当前求职目标、技能、投递状态和近期面试反馈生成下一步求职建议。
  - 认证：受 Gateway JWT 保护，请求头必须包含 `Authorization: Bearer <token>`；仅 `STUDENT` 和 `ADMIN` 可访问。Gateway 已注入 `X-User-Role=STUDENT` 和 `X-User-Id` 时，`ai-service` 必须以注入的学生身份为准，忽略请求体中冲突的 `studentId`。
  - 请求体：`studentId`、`targetRole`、`skills`、`recentDeliveries`、`interviewWeaknesses`、`careerGoal`、`weeks`。
  - 示例请求：
    ```json
    {
      "studentId": "S001",
      "targetRole": "Java 后端实习生",
      "skills": ["Java", "Spring Boot", "MySQL"],
      "recentDeliveries": ["J001 SUBMITTED", "J002 INTERVIEW"],
      "interviewWeaknesses": ["项目深挖回答不够结构化", "缺少量化指标"],
      "careerGoal": "获得 Java 后端实习 offer",
      "weeks": 6
    }
    ```
  - 返回：`AiCoachAdviceResponse`，包含 `studentId`、`targetRole`、`readinessScore`、`headline`、`priorityActions`、`riskWarnings`、`learningPath`、`interviewDrills`、`searchKeywords`、`mocked`。
  - 所有响应使用 `ApiResponse<AiCoachAdviceResponse>`；未配置 `DASHSCOPE_API_KEY` 或模型调用失败时返回确定性的演示建议，且 `mocked=true`。
  - 响应不得返回 API Key、原始提示词、完整简历正文、JWT、密码或其他敏感信息。
- `POST /api/ai/candidates/screen`：基于投递、简历摘要、项目经历和岗位要求生成候选人初筛结果。
  - 请求体：`deliveryId`、`companyId`、`studentId`、`resumeId`、`jobId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`、`targetRole`、`skills`、`projects`、`jobRequirements`、`resumeSummary`、`jobDescription`。
  - 返回：`CandidateScreenResult`，包含 `deliveryId`、`studentId`、`jobId`、`resumeSourceFormat`、`resumeParseStatus`、`resumeParsedTextLength`、`score`、`recommendation`、`strengths`、`risks`、`interviewQuestions`、`nextActions`、`mocked`。
  - `resumeParseStatus=TEXT_EXTRACTED` 表示初筛参考了已抽取正文；`UNPARSED` 或 `UNKNOWN` 表示简历正文证据不足，AI 会提示 HR 做人工确认。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时，返回确定性的演示初筛结果，且 `mocked=true`。
  - 生成结果会写入筛选历史；默认内存存储，启用持久化后写入 MySQL。
- `POST /api/ai/candidates/screen/tasks`：创建 AI 候选人异步初筛任务。
  - RocketMQ 投递事件消费使用真实简历技能、项目与岗位要求，不把岗位要求当作候选人已具备的技能。上下文读取或任务保存失败时返回重试结果；成功任务按投递去重。无效或缺少必要标识的消息丢弃。
  - 请求体沿用 `CandidateScreenRequest`，字段与同步初筛一致。
  - 返回：`CandidateScreenTask`，包含 `taskId`、`deliveryId`、`companyId`、`studentId`、`resumeId`、`jobId`、`status`、`source`、`message`、`result`、`createdAt`、`updatedAt`。
  - `status` 支持 `PENDING`、`RUNNING`、`COMPLETED`、`FAILED`；`source` 支持 `DEMO`、`RUNTIME`、`ROCKETMQ`。
  - `result` 仅在任务完成且有可展示结果时返回，结构为 `CandidateScreenResult`；前端必须能展示无 `result` 的排队、运行中和失败状态。
  - 所有响应仍使用 `ApiResponse<CandidateScreenTask>`，不得返回 API Key、原始提示词、完整简历正文或其他敏感信息。
- `GET /api/ai/candidates/screen/tasks?companyId=C001&deliveryId=D001`：查询 AI 候选人异步初筛任务。
  - 查询参数：`companyId`、`deliveryId` 均可选；为空时不过滤。
  - 返回：`CandidateScreenTask[]`，字段同创建接口。
  - 企业端投递审核和 AI 筛选历史优先使用该任务列表展示异步状态；仅显式设置 `VITE_DEMO_MODE=true` 且未配置 gateway 或 AI proxy 时，前端返回 `DEMO` 任务数据且不调用 `fetch`；默认模式报告后端错误。
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
  - 请求体：`studentId`、`resumeId`、`jobId`、`targetRole`、`skills`，可选 `questionCount`、`useRag`、`knowledgeLimit`。
  - `questionCount` 默认 3，允许 1 到 20；`useRag` 默认 `true`；`knowledgeLimit` 默认 6，允许 1 到 12。
  - 当 `useRag=true` 时，AI 服务会先用目标岗位、技能和“面试/题目/答题要点”等关键词检索 RAG 知识库，再把检索证据注入题目生成提示词。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时，仍会基于同一批 RAG 检索证据生成确定性的降级面试题。
  - 返回：`InterviewQuestion[]`，每项包含 `questionId`、`category`、`difficulty`、`question`、`referencePoints`、`knowledgeReferences`。
- `POST /api/ai/interview/feedback`：提交模拟面试回答并生成结构化反馈。
  - 请求体：`studentId`、`questionId`、`question`、`answer`、`targetRole`。
  - 返回：`InterviewFeedback`，包含 `score`、`strengths`、`gaps`、`suggestions`、`summary`、`mocked`。
  - 未配置 `DASHSCOPE_API_KEY` 或模型调用失败时，返回确定性的演示反馈，且 `mocked=true`。
- `GET /api/ai/interview/records?studentId=S001`：查看学生模拟面试历史记录。
  - 返回：`InterviewRecord[]`，每项包含 `recordId`、`studentId`、`targetRole`、`questionId`、`question`、`answer`、`score`、`summary`、`suggestions`、`mocked`、`createdAt`。
  - 当前 MVP 使用内存存储，服务重启后记录会清空。

## Delivery

- `POST /api/deliveries`：投递岗位。
  - 企业归属优先读取显式 `DELIVERY_JOB_COMPANY_MAP` 或已启用的演示岗位映射；其他岗位查询 job-service 的真实归属，不再统一分配给默认企业。
  - 岗位不存在返回 HTTP 400；岗位服务或企业归属不可用返回 HTTP 503。错误响应保持 `ApiResponse<T>`，失败时不保存投递。
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
  - `POST /api/ai/resume/rewrite`
  - `POST /api/ai/career/plan`
  - `POST /api/ai/coach/advice`
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
  - Default step order is VM1 Nacos bootstrap first, VM3 data and AI services second, VM2 business services third, VM1 gateway/frontend last, and all-node health/API smoke checks after startup.
  - `steps` items include `order`, `nodeId`, `nodeName`, `title`, `purpose`, `commands`, `verifyUrls`, `expectedResult`, and `troubleshooting`.
  - `acceptanceChecks` items include `name`, `command`, and `expectedResult`.
  - Hosts and ports are generated from `VM1_HOST`, `VM2_HOST`, `VM3_HOST`, `FRONTEND_PORT`, `GATEWAY_PORT`, `NACOS_PORT`, `AUTH_PORT`, `USER_PORT`, `RESUME_PORT`, `JOB_PORT`, `MATCH_PORT`, `AI_PORT`, `DELIVERY_PORT`, `MYSQL_PORT`, `REDIS_PORT`, `MINIO_PORT`, and `ROCKETMQ_PORT`, with defaults aligned to `deploy/three-vm.env.example` and the VM compose files.
  - The endpoint only generates deployment instructions, does not probe network health, and never returns credential values.

- `GET /api/admin/dashboard`：学校端统计看板。
  - 返回：`studentCount`、`companyCount`、`jobCount`、`deliveryCount`、`averageMatchScore`、`deliveryStatusCounts`、`pendingDeliveryCount`、`interviewRate`、`offerRate`、`activeStudentCount`、`highPotentialCandidateCount`、`weeklyDeliveryTrend`、`skillDemandTop`、`conversionFunnel`、`riskAlerts`。
  - `interviewRate` 和 `offerRate` 为整数百分比，用于展示投递进入面试和录用转化情况。
  - `weeklyDeliveryTrend` 每项包含 `label`、`deliveryCount`、`interviewCount`、`offerCount`，用于管理端趋势图。
  - `skillDemandTop` 每项包含 `skill`、`jobCount`、`matchedStudentCount`、`demandScore`，用于展示岗位技能需求和学生供给匹配。
  - `conversionFunnel` 每项包含 `stage`、`label`、`count`、`conversionRate`，用于展示投递到查看、面试、录用的漏斗。
  - `riskAlerts` 为就业办可读的风险提示列表，用于管理数据大屏的预警区域。

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

- `GET /api/admin/audit/export/{exportId}`: download a prepared admin audit CSV export.
  - Returns a UTF-8 `text/csv` attachment containing only the existing redacted audit fields; it excludes API keys, tokens, raw AI prompts, full resume text, and password hashes.
  - Export identifiers are created by `POST /api/admin/audit/export`. In the MVP they are retained in user-service memory for two hours, so an expired, unknown, or post-restart identifier returns HTTP 404 with `ApiResponse.fail("Audit export not found")`.
  - Expected permission: `admin:audit:read`.

- Frontend fallback:
  - Without `VITE_API_BASE_URL` or `VITE_API_PROXY_TARGET` in development, audit client functions return deterministic demo data and do not call `fetch`.
  - `/admin/audit` uses the same Vue route family as other admin modules and is backed by `GET /api/admin/audit/overview` and `POST /api/admin/audit/export` once the backend is available.

## v3.2 AI Screening Task Persistence

- `POST /api/ai/candidates/screen/tasks`, `GET /api/ai/candidates/screen/tasks`, `GET /api/ai/candidates/screen/tasks/{taskId}`, and `POST /api/ai/candidates/screen/tasks/{taskId}/retry` keep the same response contracts.
- When `AI_SCREENING_PERSISTENCE_ENABLED=true` and datasource settings are present, async task state is stored in MySQL table `ai_candidate_screen_task` with the original `CandidateScreenRequest` snapshot and optional `CandidateScreenResult` snapshot.
- On service restart, persisted `PENDING` or `RUNNING` tasks are marked `FAILED` with a retryable message instead of remaining stuck forever.
- RocketMQ-created tasks use a `dedup_key` derived from `DELIVERY_CREATED` and `deliveryId`; repeated delivery messages return the existing task instead of creating duplicates.
- Retry still creates a new task and keeps the original failed task for auditability. The retry uses the persisted request snapshot, so it works after service restart.

## v3.5 AI Planning History and Dashboard Data

- `GET /api/ai/career/history?studentId=S001&limit=20`: list the student's AI planning history.
  - Returns: `ApiResponse<List<AiPlanningRecord>>`.

## v3.7 Boundary Behavior

- `GET /api/resumes/{id}` and `POST /api/resumes/{id}/analyze` return `ApiResponse.fail("Resume not found")` when the resume ID is unknown. They no longer fall back to the seeded demo resume.
- `POST /api/resumes/upload` returns an `ApiResponse` error body for missing multipart file input or invalid upload parameters.
- `PUT /api/deliveries/{id}/status` returns `ApiResponse.fail("Delivery not found")` when the delivery ID is unknown. It no longer updates the seeded demo delivery as a fallback.
- Invalid delivery status values, malformed delivery JSON, and missing required delivery parameters return HTTP 400 with an `ApiResponse` error body.
- `POST /api/ai/coach/advice`, `POST /api/ai/resume/rewrite`, and `/api/ai/career/*` are student-owned AI endpoints at the gateway. `STUDENT` and `ADMIN` can access them; `COMPANY` is rejected by RBAC.
- Deployment boundary checks are automated by `scripts/check-boundary-cases.ps1`, which records authentication, RBAC, trusted identity, resume, delivery, AI, monitoring, and restore-safety results under `reports/deploy`.
  - Query parameters: `studentId` is required for direct service calls; `limit` is optional, defaults to `20`, and is normalized to the `1`-`100` range.
  - `AiPlanningRecord` fields: `recordId`, `studentId`, `operation`, `resumeId`, `targetRole`, `resumeRewrite`, `careerPlan`, `mocked`, and `createdAt`.
  - `operation` identifies the source workflow, currently resume rewrite or career plan. Exactly one of `resumeRewrite` and `careerPlan` is populated for each record.
  - When Gateway has injected `X-User-Role=STUDENT` and `X-User-Id`, `ai-service` uses the injected user id as the effective `studentId` and ignores any conflicting `studentId` query parameter.
  - All responses still use `ApiResponse<T>` and must not include API keys, prompts, full resume text, tokens, or other secrets.

- `GET /api/admin/dashboard`: management dashboard statistics.
  - When `DASHBOARD_REALTIME_ENABLED=true` and the `user-service` MySQL datasource is reachable, the dashboard aggregates real persisted tables including `resume_summary_record`, `job_record`, `match_result_record`, `delivery_record`, `ai_candidate_screen_record`, and `ai_planning_record`.
  - If realtime mode is disabled, datasource configuration is missing, the MySQL driver is unavailable, or MySQL cannot be queried, the endpoint returns the stable deterministic fallback dataset so the admin screen remains usable for demos and smoke tests.

## v3.8 Load Smoke

- `scripts/run-load-smoke.ps1` provides a lightweight performance smoke check for deployed Gateway APIs. It logs in the `student`, `company`, and `admin` demo users, then repeatedly calls `GET /api/jobs`, `GET /api/deliveries/my`, `GET /api/deliveries/company?companyId=C001`, `GET /api/ai/status`, `POST /api/ai/knowledge/search`, `GET /api/ai/knowledge/stats`, `POST /api/ai/knowledge/answer`, and `GET /api/notifications/my`.
- `POST /api/ai/knowledge/answer` is called with `useAi=false` by the smoke script so the test covers RAG retrieval and citation assembly without external model cost.
- `POST /api/ai/knowledge/search`, `GET /api/ai/knowledge/stats`, `POST /api/ai/knowledge/answer`, and `GET /api/notifications/my` are optional compatibility probes in this script. HTTP 404 is recorded as `SKIPPED`; other non-2xx responses or `ApiResponse.code != 0` are recorded as `FAIL`.
- Every run writes `reports/deploy/load-smoke-<timestamp>.md` with PASS/FAIL/SKIPPED counts, average latency, P95 latency, endpoint-level summaries, and failed/skipped details. Demo passwords and bearer tokens are never written to console output or reports.
- `scripts/run-step-load-smoke.ps1` runs the smoke script across stepped user counts, for example `-UserSteps 3,10,30,50`, and writes `reports/deploy/step-load-smoke-<timestamp>.md` plus child reports for each step.

## v3.8 Notifications, Interview Scheduling, RAG, and MQ Events

### Notifications

- `GET /api/notifications/my?studentId=S001`
  - Gateway permission: student profile access; `X-User-Role=STUDENT` and `X-User-Id` override the `studentId` query value.
  - Returns: `ApiResponse<List<NotificationMessage>>`.
- `GET /api/notifications/company?companyId=C001`
  - Gateway permission: company delivery read; `X-User-Role=COMPANY` and `X-User-Id` override the `companyId` query value.
  - Returns: `ApiResponse<List<NotificationMessage>>`.
- `POST /api/notifications/{id}/read`
  - Marks one notification as read. Student/company users can only update their own notifications; admin/direct-service demo calls can update by id.
  - Returns: `ApiResponse<NotificationMessage>` or `ApiResponse.fail("Notification not found")`.
- `NotificationMessage` fields: `notificationId`, `targetRole`, `targetUserId`, `title`, `content`, `sourceType`, `sourceId`, `read`, `createdAt`.

### Interview Schedules

- `POST /api/interviews/schedules`
  - Gateway permission: company delivery read.
  - Request fields: `deliveryId`, `companyId`, `studentId`, `jobId`, `title`, `startTime`, `durationMinutes`, `location`, `meetingUrl`, `note`.
  - Company identity from Gateway overrides the request company id; the target delivery must belong to that company.
  - Creates an interview schedule, a student notification, and an `INTERVIEW_SCHEDULED` lifecycle event on the existing `delivery-events` topic when RocketMQ publishing is enabled.
  - Returns: `ApiResponse<InterviewSchedule>`.
- `GET /api/interviews/schedules/my?studentId=S001`
  - Gateway permission: student interview access; student identity overrides query `studentId`.
  - Returns: `ApiResponse<List<InterviewSchedule>>`.
- `GET /api/interviews/schedules/company?companyId=C001`
  - Gateway permission: company delivery read; company identity overrides query `companyId`.
  - Returns: `ApiResponse<List<InterviewSchedule>>`.
- `PUT /api/interviews/schedules/{id}/status?status=CONFIRMED`
  - Supported statuses: `PROPOSED`, `CONFIRMED`, `DECLINED`, `COMPLETED`, `CANCELLED`.
  - Students can confirm or decline their own proposed schedules. Companies can update schedules for their own company. Admin/direct-service demo calls can update by id.
  - Creates company/student notifications and an `INTERVIEW_STATUS_CHANGED` lifecycle event.
  - Returns: `ApiResponse<InterviewSchedule>` or `ApiResponse.fail(...)`.

### RAG Knowledge Base

- `POST /api/ai/knowledge/documents`
  - Gateway permission: admin AI observability/read capability, effectively admin only.
  - Request fields: `title`, `content`, `category`, `source`, `tags`, `roles`.
  - In `ai-service`, documents are normalized, split into overlapping chunks, embedded with a local hash-vector embedding, and saved through `KnowledgeBaseStore`.
  - When `AI_KNOWLEDGE_PERSISTENCE_ENABLED=true` and `SPRING_DATASOURCE_URL` is configured, documents and chunks are persisted in MySQL tables `ai_knowledge_document` and `ai_knowledge_chunk`; otherwise the service falls back to in-memory storage.
  - Returns: `ApiResponse<KnowledgeDocument>`.
- `GET /api/ai/knowledge/documents?keyword=Redis&role=STUDENT&limit=20`
  - Lists readable knowledge documents with optional keyword and role filtering.
  - Returns: `ApiResponse<List<KnowledgeDocument>>`.
- `PATCH /api/ai/knowledge/documents/{documentId}/roles`
  - Admin updates readable roles for an existing RAG document.
  - Request fields: `roles`, for example `["ADMIN","STUDENT"]` or `["ALL"]`.
  - The service updates both document metadata and its chunks so role-aware retrieval changes immediately.
  - Returns: `ApiResponse<KnowledgeDocument>`.
- `DELETE /api/ai/knowledge/documents/{documentId}`
  - Admin deletes an existing RAG document and all of its chunks.
  - Deleted uploaded/manual documents stop participating in RAG search and answer retrieval immediately; if the deleted ID belongs to a built-in seed corpus document, it may be recreated on the next `ai-service` restart while seeding is enabled.
  - Returns: `ApiResponse<Boolean>` with `data=true`; missing documents return `ApiResponse.fail("Knowledge document not found")`.
- `POST /api/ai/knowledge/documents/batch-delete`
  - Admin deletes multiple RAG documents in one request.
  - Request fields: `documentIds`, for example `["KB-001","KB-002"]`; duplicate and blank IDs are ignored.
  - Returns: `ApiResponse<KnowledgeDocumentBatchDeleteResult>` with `requestedCount`, `deletedCount`, `deletedDocumentIds`, and `missingDocumentIds`.
  - An empty request returns `ApiResponse.fail("documentIds is required")`.
- `GET /api/ai/knowledge/stats`
  - Gateway permission: admin AI observability/read capability, effectively admin only.
  - Returns the current RAG corpus document count, chunk count, top categories, readable role counts, source counts, tag counts, corpus version, seed status, and whether the active store is persistent.
  - Returns: `ApiResponse<KnowledgeBaseStats>`.
- `POST /api/ai/knowledge/search`
  - Gateway permission: AI analyze.
  - Request fields: `query`, `role`, `limit`.
  - Gateway-injected `STUDENT` or `COMPANY` role overrides the request body role to avoid cross-role retrieval.
  - Retrieval ranks readable chunks with local hash-vector cosine similarity plus lexical matching, then returns chunk summaries as `AiSearchResult`.
  - Returns: `ApiResponse<AiSearchResponse>` where each result uses `AiSearchResult` with `type="knowledge"`.
- `POST /api/ai/knowledge/answer`
  - Gateway permission: AI analyze.
  - Request fields: `query`, `role`, `limit`, optional `useAi`.
  - Gateway-injected `STUDENT` or `COMPANY` role overrides the request body role.
  - Retrieves top chunks, returns citations, and calls DashScope only when `useAi` is not `false` and `DASHSCOPE_API_KEY` is configured. Missing key, model failure, empty model output, no evidence, or `useAi=false` returns a deterministic `mocked=true`, `provider=local-rag-fallback` local answer with available citations. The local response is an evidence summary; an empty model reply is never presented as successful AI generation.
  - Fallback answers explain the condition in Chinese and preserve readable citations without exposing raw provider exceptions or connection details. `useAi=false` never invokes the model.
  - Answers are Markdown-oriented and use longer retrieved context per chunk. `DASHSCOPE_MAX_TOKENS` controls the DashScope output token budget; the service no longer truncates generated answers in this endpoint.
  - Returns: `ApiResponse<KnowledgeAnswerResponse>`.
- `KnowledgeDocument` fields: `documentId`, `title`, `content`, `category`, `source`, `tags`, `roles`, `createdBy`, `createdAt`.
- `KnowledgeBaseStats` fields: `documentCount`, `chunkCount`, `categoryCounts`, `roleCounts`, `sourceCounts`, `tagCounts`, `corpusVersion`, `seedEnabled`, `persistentStore`, `generatedAt`.
- `KnowledgeAnswerResponse` fields: `query`, `answer`, `citations`, `mocked`, `provider`, `generatedAt`.
- `KnowledgeCitation` fields: `documentId`, `chunkId`, `title`, `source`, `score`, `snippet`.

### MQ Event Expansion

- Existing `delivery.events.rocketmq.*` settings are reused; no new service or topic is required.
- New event types written through the existing publisher:
  - `NOTIFICATION_CREATED`
  - `INTERVIEW_SCHEDULED`
  - `INTERVIEW_STATUS_CHANGED`
- The publisher still records recent events at `GET /api/deliveries/events` with `publishStatus=DISABLED`, RocketMQ send status, or `FAILED`.
# v3.11 RAG File Ingestion and Milvus Vector Index

- `POST /api/ai/knowledge/files`
  - Uploads a RAG source file as `multipart/form-data`.
  - Form fields: `file` is required and supports `.txt`, `.md`, `.pdf`, `.doc`, `.docx`; `title`, `category`, `source`, `tags`, and `roles` are optional metadata.
  - `tags` and `roles` are comma-separated strings.
  - Returns: `ApiResponse<KnowledgeFileIngestionJob>`.
  - Job status values: `UPLOADED`, `PARSING`, `INDEXING`, `READY`, `FAILED`, `DUPLICATE`.
  - Duplicate files are detected by SHA-256 only when the referenced knowledge document still exists or an upload is still being processed. A repeated upload returns a new job with `status=DUPLICATE` and references the existing document/chunk counts; after the document is deleted, uploading the same file starts a fresh ingestion job.
  - Original files are written to MinIO when `AI_KNOWLEDGE_OBJECT_STORAGE_ENABLED=true`; otherwise `storageProvider=local-demo` and `storageStatus=SKIPPED`.
  - Invalid format, empty file, or oversized upload returns an `ApiResponse` error body and HTTP 400.

- `GET /api/ai/knowledge/ingestions?status=READY&limit=20`
  - Lists recent RAG ingestion jobs.
  - Returns: `ApiResponse<List<KnowledgeFileIngestionJob>>`.
  - `limit` is normalized to `1..200`.

- `GET /api/ai/knowledge/vector/status`
  - Returns the current vector index provider status.
  - Returns: `ApiResponse<KnowledgeVectorStatus>`.
  - Fields: `provider`, `enabled`, `available`, `endpoint`, `collection`, `dimension`, `indexedChunkCount`, `fallbackReason`, `checkedAt`.
  - When Milvus is disabled or unreachable, search and answer endpoints continue to use local hash-vector retrieval.

- `KnowledgeFileIngestionJob` fields:
  - `jobId`, `documentId`, `fileName`, `fileFormat`, `fileSize`, `sha256`, `title`, `category`, `source`, `status`, `message`, `objectKey`, `storageProvider`, `storageStatus`, `chunkCount`, `vectorCount`, `error`, `createdBy`, `createdAt`, `updatedAt`.

- Gateway permissions:
  - Upload, ingestion list, vector status, document create/list, and stats require `admin:ai-observability:read` capability through the current admin role policy.

## v3.12 Core deepening APIs

本节是当前实现的补充约定，优先于前面的历史 RAG 说明。所有响应仍使用 `ApiResponse<T>`，时间字段使用 ISO-8601。

### 可信身份

Gateway 验证 Bearer Token 后清理外部身份头，再注入 `X-User-Id` 和 `X-User-Role`。知识检索、问答和学习、面试记录均按该身份隔离。客户端请求体中的 `role`、`studentId` 不能提升权限或改变资源归属。直接调用 `ai-service` 进行联调时，也必须设置可信身份头；生产环境只允许 Gateway 访问业务接口。

### 岗位匹配证据

`POST /api/matches/resume-job` 使用规则服务计算技能声明覆盖率和材料证据覆盖率，不调用模型。`MatchDetails` 追加 `metadata`、`jobSnapshot`、`profileSnapshot` 和 `stale`；每个 `MatchRequirement` 包含 `declared`、`supported`、`status`、`evidence` 和建议。

- `declared` 表示学生资料中的技能声明。
- `supported` 只有在简历正文或学生确认项目中找到具体已发生动作时才为真。
- “未使用”“计划学习”“准备实现”等否定或计划描述不能成为实践证据。
- 技能别名按 `SkillOntology` 的精确别名表归一化；相近技术不会自动视为目标技能。
- `conditions` 单独返回学历、地点和实习时间，状态是 `SATISFIED`、`NOT_SATISFIED` 或 `UNKNOWN`。

### 学习路径成果与确认

- `POST /api/ai/learning/plans/{planId}/tasks/{taskId}/evidence`：保存成果说明和作品链接。请求为 `{ "description": "...", "links": ["https://..."] }`。链接只作为引用，不能单独证明能力；成果评价失败不删除已保存成果。
- `POST /api/ai/learning/plans/{planId}/replan`：传 `previewOnly: true` 时生成 `DRAFT` 修订预览，原计划仍保持 `ACTIVE`。预览应保存 `revisionId`，前端展示调整原因后再确认。
- `POST /api/ai/learning/plans/{originalPlanId}/confirm`：请求 `{ "revisionId": "..." }`，只允许本人确认属于原计划根版本的草稿；成功后原计划变为 `SUPERSEDED`，确认的修订变为 `ACTIVE`。重复确认返回已保存的当前结果。

学习完成状态不会自动提升岗位匹配证据。下一次匹配会继续要求正文、项目或成果中存在可核对材料。

### 模拟面试来源、练习和复盘

所有接口使用登录学生的服务端身份并返回 `ApiResponse<T>`。岗位资料由服务端读取，项目、学习成果、匹配差距和历史面试薄弱项必须属于本人；伪造 `studentId` 或来源 ID 不能扩大权限。

| 接口 | 行为与返回 |
| --- | --- |
| `GET /api/ai/interview/sessions/sources?resumeId=...&jobId=...&matchId=...` | 可选上下文筛选。返回服务端可选来源列表：`sourceType`、`sourceId`、`sourceKind`、`label`、`description` 及对应简历、岗位、匹配、目标岗位。创建请求复用该来源 ID。 |
| `POST /api/ai/interview/sessions` | 创建 `InterviewSession`。旧请求默认为 `COACHING` + `JOB`。`PROJECT`、`GAP` 需选择可访问来源；无简历也可以使用通用岗位练习。 |
| `PUT /api/ai/interview/sessions/{id}/questions/{questionId}/answer` | 兼容首次保存，先保存不可变回答再评价；同题相同文本幂等，不能覆盖历史回答。 |
| `POST /api/ai/interview/sessions/{id}/questions/{questionId}/attempts` | 辅导练习新增重答；请求 `{ "questionId": "...", "answer": "..." }`，返回更新后的会话。每次尝试独立编号、指纹和评价状态。 |
| `POST /api/ai/interview/sessions/{id}/questions/{questionId}/evaluate` | 评价该题当前采用的回答；成功结果按输入指纹复用。 |
| `POST /api/ai/interview/sessions/{id}/questions/{questionId}/attempts/{attemptId}/evaluate` | 评价指定已保存尝试或失败重试。保存失败与评价失败分开；评价失败不删除回答。 |
| `POST /api/ai/interview/sessions/{id}/questions/{questionId}/attempts/{attemptId}/select` | 学生明确采用回答，可提交 `{ "reason": "..." }`。首次回答默认采用，重答不自动覆盖报告版本。 |
| `POST /api/ai/interview/sessions/{id}/pause`、`/resume` | 暂停/恢复，返回更新后的会话和服务端计时。暂停期间不计入作答时间。 |
| `POST /api/ai/interview/sessions/{id}/partial-report` | 返回 `PARTIAL` 阶段报告，标记已完成范围与未答题；会话仍可继续，不进入历史成绩比较。 |
| `POST /api/ai/interview/sessions/{id}/finish` | 辅导练习的主问题与追问、或模拟面试的主问题完成后返回 `FINAL` 报告；复用选定尝试的逐题评价，重复完成幂等。 |
| `GET /api/ai/interview/sessions/{id}/next-actions` | 返回报告关联的练习、知识资料、材料补充及学习计划调整行动。 |
| `POST /api/ai/interview/sessions/{id}/next-actions/preview` | 请求 `{ "actionId": "...", "planId": "..." }`，其中 `planId` 按所选行动可选；仅生成影响和时间预览。 |
| `POST /api/ai/interview/sessions/{id}/next-actions/confirm` | 请求 `{ "previewId": "..." }`，确认后创建专项练习或学习计划调整草稿；重复确认返回同一创建结果。学习草稿仍须通过现有计划确认接口切换版本。 |
| `POST /api/ai/interview/sessions/{id}/resume-candidate` | 面试服务的候选材料接口，请求 `{ "questionId": "...", "attemptId": "..." }`，返回来源原句与待补数据；不写入简历资料。 |
| `POST /api/resumes/master-profile/interview-candidate` | 请求 `{ "sessionId": "...", "questionId": "...", "attemptId": "..." }`。服务端读取本人已评价回答，返回未确认的简历候选经历；不会自动保存主资料。 |

创建参数 `timerMinutes` 在 `COACHING` 模式为空；`MOCK` 未提供时默认 20，允许 5–60，`0` 明确关闭。服务端保存 `startedAt`、`pausedAt`、`runningSince`、`accumulatedSeconds`、`pausedSeconds` 和 `timeoutReached`；到时提醒但不自动结束。会话查询恢复真实计时，浏览器倒计时仅用于显示。

行动预览保存 `reportFingerprint`、`skillGap` 和来源引用，确认使用预览绑定的薄弱项。阶段报告更新、继续答题或重新评价后，未确认的旧预览返回业务错误，需重新预览。旧记录中已确认的行动继续返回原结果；没有版本绑定的历史未确认预览只读并要求重新预览。专项练习使用预览派生的稳定会话 ID，练习已经保存但确认记录写入中断时，重试会复用同一练习；学习调整复用相同输入的已保存 `DRAFT`。

辅导模式提交后留在本题查看评价，可重答、采用版本及继续。模拟模式作答期间 API 和页面均隐藏参考点、评分、评价与追问；阶段或最终报告统一复盘。每道主问题最多一次基于遗漏、矛盾、个人职责或验证证据的追问，不按回答长度机械触发。

项目和薄弱项练习只使用选定来源的正文与原句，岗位及知识资料作为背景。薄弱项题目必须围绕选定缺口；项目题不能将未提供的技术、职责或成果当作既有经历。生成结果偏离来源时退回对应来源的规则题，标记演示来源，不追加模型调用。题目生成提示版本为 `interview-generation-context-v3`。阶段报告的“继续答题”恢复未提交草稿，否则进入首道未答题。

`InterviewAnswerAttempt` 包含 `attemptId`、`questionId`、`attemptNo`、`answer`、`submittedAt`、`evaluationStatus`、`evaluation`、`evaluationError`、`inputFingerprint`、`selectedForReport`、`selectedAt`、`selectionReason`。四个评分维度仍为 `ACCURACY`、`ANALYSIS`、`EVIDENCE`、`STRUCTURE`；结论区分 `SUPPORTED`、`INCORRECT`、`INSUFFICIENT_EVIDENCE`，引用必须逐字存在于对应回答或授权材料中，不能将回答声明等同为已核实成果。

会话及题目保存 `sourceReferences`、来源正文、岗位要求、薄弱项和 rubric 版本。报告记录 `reportType`、完成范围、未答题、采用的尝试与重答差异；只比较相同目标岗位、模式、rubric 版本的最终报告，并展示难度差异。模拟面试查看阶段反馈后继续作答会记录 `feedbackViewedAfterPartial`，该最终报告不与独立模拟成绩直接比较。旧会话按辅导模式、单次回答和历史报告兼容读取。模型不可用时确定性规则评价标记为演示评价；失败状态允许重试。计时、回答尝试、报告和行动预览均保存在已有版本化会话快照中，无新增必需基础设施。

`FAILED` 即使带有规则兜底 `evaluation`，辅导答题页仍提供评价重试，原回答和提交时间不变；模拟作答期间不显示评价或重试按钮，生成报告时统一评价。逐题反馈默认显示分数、摘要和首条建议，四维评分、引用和评价版本可展开核对。

学习成果仅在评价通过且学生确认后可成为项目练习来源；面试候选经历经学生确认并保存主资料、重新生成简历后才参与材料证据。面试分数、任务完成和候选创建均不会自动提高技能覆盖率。

### RAG 检索与引用

当前 RAG 版本为 `semantic-rag-v2`。

- 文档按标题、段落和句子边界切分，保存 `startOffset`、`endOffset`、`heading` 和 `chunkIndex`。偏移是原始正文的左闭右开字符范围。
- 关键词和真实向量各自召回前 20 条，合并去重后使用 RRF，固定 `k=60`；融合前 20 条再执行排序，默认返回 5 条。
- 默认向量为 DashScope `text-embedding-v4`、1024 维；默认排序为 `gte-rerank-v2`。模型、路径和维度由环境变量配置，不写入密钥。
- `AiSearchResult.id` 是 `chunkId`，`AiSearchResult.citation.documentId` 可用于文档级 Recall@5。`KnowledgeCitation` 包含 `chunkId`、文档 ID、原文偏移、标题、角色和片段。
- `AiSearchResponse` 新增 `retrievalMode`、`algorithmVersion`、`evidenceStatus`、`permissionVersion` 和 `metadata`。常见模式为 `HYBRID_RRF_RERANK`、`HYBRID_RRF`、`KEYWORD_RERANK`、`KEYWORD_ONLY`。
- `KnowledgeAnswerResponse` 新增 `generationMode`、`evidenceStatus`、`claims`、`inputFingerprint` 和 `metadata`。AI 回答必须返回可核对的结构化事实；无法核对时保留检索摘要并标记 `INSUFFICIENT`。
- 知识片段被当作数据传入模型，片段中的系统指令、角色要求或命令不会改变问答系统提示。
- 候选检索、模型上下文和缓存都执行角色权限检查。文档删除、角色变更、正文变化或权限版本变化会清理相关缓存。
- `GET /api/ai/knowledge/revision`：供已认证用户只读核对知识资料和访问权限的当前版本，返回 `ApiResponse<String>`；版本值与检索响应中的 `permissionVersion` 一致。资料新增、删除、正文更新或角色权限变更时版本改变；资料未变化时保持稳定。该接口只计算资料指纹，不返回资料内容，不执行检索、向量化、重排或模型调用。浏览器恢复历史结果前应核对此版本，版本不同则清除旧回答与引用，提示重新检索。

### RAG 索引管理

以下接口位于独立的 `KnowledgeIndexController`，必须提供 `X-User-Role: ADMIN`：

- `POST /api/ai/knowledge/index/rebuild`：启动新版本索引，返回 `KnowledgeIndexRebuildStatus`。
- `GET /api/ai/knowledge/index/rebuild/{jobId}`：读取重建进度。
- `GET /api/ai/knowledge/index/status`：读取最近一次重建状态。

重建先对完整资料集生成新向量，成功后原子切换；失败、服务重启或资料版本变化不会破坏旧资料和旧索引。进度写入 `ai_knowledge_index_rebuild`。新增偏移及向量元数据写入 `ai_knowledge_chunk_metadata`，旧知识表字段和旧构造器继续可读。

### RAG 响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "query": "Redis 缓存怎么验证",
    "retrievalMode": "HYBRID_RRF_RERANK",
    "evidenceStatus": "RETRIEVED",
    "results": [{
      "id": "KB-001-CH-001",
      "score": 92,
      "citation": {
        "documentId": "KB-001",
        "chunkId": "KB-001-CH-001",
        "chunkIndex": 1,
        "startOffset": 0,
        "endOffset": 42,
        "heading": "缓存验证",
        "roles": ["STUDENT"]
      }
    }]
  }
}
```

### 错误、降级与重试

- 读取不到模型密钥、向量或排序请求失败时仍可使用关键词检索；响应的 `retrievalMode` 会说明降级状态。
- `useAi: false` 不调用模型，直接返回带引用的检索摘要。
- 外部模型失败不会把异常、URL、Bearer Token 或密钥写入学生响应；客户端可重新发起同一请求。
- 管理员重建失败返回失败状态而不是清空旧索引。新模型维度必须使用独立 collection/version，不能与旧 hash 向量混用。


## 简历工作区、生成与导出

简历工作区接口统一通过 Gateway 的用户身份头校验所有权。导入原件只生成待确认候选资料，学生确认后才写入主资料。

- `GET /api/resumes/master-profile`：读取当前用户的主资料。
- `PUT /api/resumes/master-profile`：保存主资料，支持 `expectedRevision`、`data`、`sourceResumeId` 和 `confirmed` 字段。
- `POST /api/resumes/master-profile/import`：传入 `{ "resumeId": "R..." }`，返回待确认的导入候选资料。
- `POST /api/resumes/master-profile/photo`：以 `multipart/form-data` 上传 JPG/PNG 个人照片。
- `GET /api/resumes/templates`：获取首批启用的 T01-T08 适配模板及适用岗位信息。
- `POST /api/resumes/drafts`：按 `templateId`、`targetRole`、可选 `jobId` 和 `profileRevision` 创建岗位版本草稿。
- `GET/PATCH /api/resumes/drafts/{id}`：读取或编辑草稿；PATCH 使用 `expectedRevision`，并支持 `data` 与 `confirm=true`。
- `POST /api/resumes/drafts/{id}/diagnose`：依据该草稿事实和岗位快照生成可定位的优先建议，并保存新修订。
- `GET /api/resumes/drafts/{id}/revisions`：查看草稿修订历史。
- `POST /api/resumes/drafts/{id}/suggestions/apply`：采纳一条已核对的诊断建议并生成新修订。
- `POST /api/resumes/drafts/{id}/restore`：恢复到指定历史修订。
- `POST /api/resumes/drafts/{id}/exports`：按指定草稿版本创建 Word/PDF 导出任务。
- `GET /api/resumes/exports/{id}`：查询 `QUEUED`、`RUNNING`、`SUCCEEDED`、`NEEDS_EDIT` 或 `FAILED` 状态，并取得文件摘要、排版问题和短期下载地址。
- `GET /api/resumes/exports/{id}/files/{format}`：使用当前学生的 Bearer Token 获取已成功导出的固定版本文件，`format` 仅允许 `pdf` 或 `docx`。PDF 返回 `application/pdf` 和 `inline`，Word 返回 DOCX 类型和 `attachment`；中文文件名来自导出时的资料快照，采用 UTF-8 `Content-Disposition`。响应为文件字节，缓存策略为 `private, no-store`，不暴露存储路径。无身份或非学生返回 401；文件不存在、格式无效、非本人、导出未成功或文件与快照不一致时返回 404，错误仍使用 `ApiResponse<T>`。预览和下载应通过此同源接口读取，避免依赖浏览器访问存储服务。
- `POST /api/matches/compare`：使用同一份简历比较 2-3 个服务端岗位的覆盖率、材料证据、岗位条件和待确认项。

导出使用同一份可编辑 DOCX 转换 PDF；内容超出模板页数时返回 `NEEDS_EDIT`，保留草稿并提示精简、隐藏内容或切换双页模板。

自动预览可以直接导出全部可见条目均已确认的生成草稿，不修改草稿总确认状态，也不将预览操作记为新增简历证据。可见条目包含未确认内容时返回 400；待采纳建议和补充问题不进入文件。导出复用按草稿 ID、修订号及当前渲染/模板版本隔离。服务启动时增量增加 `render_version`、`docx_key` 和 `pdf_key`，历史记录标记为 `legacy` 并保留原文件；升级后的旧草稿可以直接重新导出，原正文、修订和历史下载不变。


### 所有权、确认与版本

业务 JSON 返回 `ApiResponse<T>`。使用 Gateway Bearer Token，由网关写入用户身份头；
工作区接口仅允许非空 `X-User-Id` 与 `X-User-Role: STUDENT`。
草稿、原件、修订、照片和导出检查同一学生的所有权，越权对象返回 404。
直接调用 AI 草稿接口同样需要学生身份，`userId` 必须与身份相同。

主资料由 `basics`、`education[]`、`skills[]`、`experiences[]`、`credentials[]` 和
`availability` 组成。资料项使用稳定 `id` 与 `source`：
`kind`、`sourceId`、`quote`、`confirmed`、`assessment`。
导入候选中的 `confirmed=false`，读候选不会写入主资料。
保存时明确 `confirmed=true`；每段经历另需学生确认。
学习成果引用保留原评价和链接，不根据任务完成状态推断掌握技能。

```json
{
  "expectedRevision": 0,
  "confirmed": true,
  "data": {
    "basics": {"name": "李同学", "phone": "", "email": "", "city": "上海", "portfolioUrl": ""},
    "education": [],
    "skills": [],
    "experiences": [],
    "credentials": [],
    "availability": {"cities": ["上海"], "earliestStartDate": "", "daysPerWeek": null, "continuousMonths": null, "graduationDate": ""}
  }
}
```

`expectedRevision` 必须匹配服务器版本，不匹配返回 HTTP 409，客户端保留本地输入。
草稿 PATCH 的 `data` 包含 `blocks[]`、`questions[]`、`suggestions[]`、`warnings[]` 和
`generationSource`。区块和条目都有 `visible`；条目另有 `factIds`、`confirmed`、
`bullets` 和 `links`。隐藏条目不进入导出。
学生确认草稿后，该草稿关联独立 `resumeId`，不覆盖上传原件。

```json
{"templateId":"T05","targetRole":"Java 开发实习生","jobId":"J...","profileRevision":1}
```

提供 `jobId` 时服务端读取真实岗位资料；草稿保存岗位、资料和模板版本快照。
只提供岗位名称则使用通用岗位建议。主资料更新后 `sourceStale=true`，已有编辑和历史保持。
同一资料、目标、模板和分析版本复用已有草稿；切换模板复用成功生成内容，不额外调用模型。
`AI_DASHSCOPE:` 为已执行模型组织，`RULES:` / `RULE_FALLBACK` 为基础整理或降级，
正文始终来自确认事实。模型建议必须通过逐句引用校验，并由学生采纳。

### 采纳、撤销与导出

采纳请求：`{"expectedRevision":2,"suggestionId":"suggest-..."}`。
原句必须精确匹配当前正文；已修改的原句或已采纳建议不重复执行。
恢复请求：`{"expectedRevision":3,"revision":2}`，恢复生成新修订而不删除历史。
撤销使用同一恢复接口，客户端先保存当前未保存输入。

导出请求：`{"expectedRevision":4}`。所有可见条目必须已确认；自动预览不会改变草稿总确认状态。
同一草稿修订的重复请求复用导出任务；失败后重复请求可重试。
返回状态与处理方式：

| 状态 | 页面处理 |
| --- | --- |
| `QUEUED` / `RUNNING` | 定时查询，保留编辑输入 |
| `SUCCEEDED` | 显示最终 PDF，提供 Word/PDF 下载 |
| `NEEDS_EDIT` | 显示页数和 `layoutIssues`，精简、隐藏内容或切换模板后重新确认 |
| `FAILED` | 显示失败原因，保留草稿，提供重试 |

成功文件包含 `fileName`、`contentType`、`url`、`sha256`。
查询成功任务会刷新 15 分钟的 MinIO 签名地址；签名地址不替代任务所有权校验。
超页数不返回下载文件，不把澄清问题或待填占位提示写入正文。

岗位比较请求：`{"resumeId":"workspace-...","jobIds":["J1","J2"]}`，支持 2-3 个互异岗位。
`requirements` 返回 `REQUIRED` / `PREFERRED` / `UNSPECIFIED` 及岗位原句；
`availableEvidence` 分别提供主资料技能声明、主资料实践支撑、当前简历支撑和来源。
`conditions` 比较学历、地点、毕业、到岗、每周出勤和连续时长，
无法可靠判断时为 `UNKNOWN`。覆盖率保持规则含义，比较不调用模型。

### Learning path execution (schedule and evidence)

Learning plan creation accepts optional `startDate` (`yyyy-MM-dd`), `studyDays` (weekday names, default Monday-Friday), and `dailyMinutesCap`. The service normalizes a weekend start to the next selected study day, assigns each task a `taskDate` and `estimatedMinutes`, and rejects schedules that exceed weekly or daily budgets. Legacy requests remain valid.

Structured AI tasks retain exact `estimatedMinutes`. Positive fractional `estimatedHours` are converted to minutes without integer truncation (for example, `0.5` hours becomes `30` minutes); legacy whole-hour inputs remain supported. Weekly budgets and preserved completed work use minutes. The older `estimatedHours` field remains an integer estimate rounded up for display and must not be used to sum a plan's budget.

- `GET /api/ai/learning/plans/{planId}/today?date=yyyy-MM-dd`: today's tasks, planned/actual minutes, and reminders.
- `GET /api/ai/learning/plans/{planId}/reminders`: server-computed in-app reminders for due work and evidence needing attention.
- `GET /api/ai/learning/plans/{planId}/reviews` and `POST /api/ai/learning/plans/{planId}/reviews`: list or save a student's weekly retrospective. The POST body may include `week`, `actualMinutes`, `incompleteReason`, `hardestTask`, `needsSplit`, `mastery`, `nextWeekMinutes`, and `newProblems`.
- `GET /api/ai/learning/plans/{planId}/tasks/{taskId}/evidence`: list evidence history.
- `POST /api/ai/learning/plans/{planId}/tasks/{taskId}/evidence/{evidenceId}/retry`: retry failed or revision-needed evaluation while retaining the original submission.
- `POST .../{evidenceId}/accept` (alias `confirm`): student confirmation is required before a result can be used as a resume candidate.
- `POST .../{evidenceId}/resume-candidate`: mark a confirmed successful result as a resume candidate.

Evidence states are `RECORDED`, `EVALUATING`, `NEEDS_REVISION`, `SUCCEEDED`, and `FAILED`. A task completion flag is self-reported progress and never upgrades skill evidence automatically.
