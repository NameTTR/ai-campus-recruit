# 五大核心功能深入优化

## 文档目的与状态

本文面向项目负责人、后端开发者、测试人员和部署维护者，说明五个核心模块的深入优化边界、数据证据约定、评估方式和上线操作。目标是把学生的声明、资料证据、练习成果和面试表现串成可追溯的求职上下文。

实现状态以代码和可重复命令为准。固定集当前为匿名合成材料，标签由设计者依据材料语义拟定，`reviewStatus=OWNER_REVIEW_PENDING`。任何 Recall、模型生成质量、三机网络和 Docker 重启结果，都必须在实际运行后写入报告，不能提前标记为通过。

## 五模块公共证据模型

系统分别记录以下来源：

| 来源 | 可以说明什么 | 不能自动说明什么 |
| --- | --- | --- |
| 技能声明 | 学生在资料中声明接触过某项技能 | 没有提供具体实践证据 |
| 简历或确认项目 | 原文中存在已经发生的职责、动作和验证描述 | 不能补造没有提供的数字或结果 |
| 学习成果 | 学生提交的练习说明和作品链接 | 勾选完成不能自动提升岗位证据 |
| 面试回答 | 回答中的方法、推理、案例和表达 | 回答长度不能代替充分证据 |

岗位匹配中的 `skillsCoverage` 只计算规范化后的声明，`evidenceCoverage` 只计算能够定位的材料证据。否定描述和计划描述保持不支持状态，例如“未使用 Redis”“计划学习 MySQL”“准备开发 Vue 页面”。相近技术不自动等价，技能别名只使用 `SkillOntology` 的精确别名表。

## 简历诊断与岗位匹配

诊断请求优先使用服务端读取的 `jobId` 岗位快照。结构化诊断包含输入指纹、算法版本、模型和岗位快照；资料变化后旧诊断标记 `stale`，历史快照仍可查看。每条 finding 的 `originalQuote` 必须能在正文、教育或项目字段定位，`suggestedRewrite` 保留已有原句，缺失数据用待填写提示。

匹配不以录用概率命名或解释。每项要求返回声明、支持状态、证据来源和下一步建议；学历、地点、实习时间分别返回 `SATISFIED`、`NOT_SATISFIED` 或 `UNKNOWN`。缺失资料不能被当作不满足。

## 学习路径与模拟面试

每项学习任务包含技能目标、前置条件、预计小时、练习、验收标准和成果形式。任务成果请求支持文本说明和 URL 列表，URL 只作为可追溯引用。成果评价失败时保留提交内容，以便重新评价。

重规划支持预览和确认两个阶段。`previewOnly=true` 生成 `DRAFT` 修订，原计划继续有效；确认接口只接受同一根计划下的 `revisionId`，确认后才把旧版本标记为 `SUPERSEDED`。已完成任务身份和成果记录不能被缩短周期或降低预算的重规划删除。

面试答案保存和评价分离。评价固定包含 `ACCURACY`、`ANALYSIS`、`EVIDENCE`、`STRUCTURE` 四项，并要求反馈中的 quote 出现在已保存答案中。生成失败不会丢失答案，成功评价可幂等重试。每道主问题最多一次追问，追问根据遗漏关键点、矛盾或项目证据触发，不根据字符数触发。

## RAG 语义检索

当前算法版本是 `semantic-rag-v2`：

1. 文档按标题、段落和中英文句子边界切分，保留原文字符偏移、标题和角色。
2. 关键词和真实向量检索独立取前 20 条，按 chunk ID 合并去重。
3. 使用固定 RRF `k=60` 排序，取融合后的前 20 条候选。
4. 可用时调用 DashScope 排序模型，默认返回 5 条；排序失败保留融合候选。
5. 默认使用 `text-embedding-v4`、1024 维；向量模型、维度、版本不匹配时不能参加语义检索。

`AiSearchResult.id` 是 chunk ID，`citation.documentId` 负责文档级评估。引用中的 `startOffset/endOffset` 是原始文档左闭右开范围，评估脚本会重新读取文档并核对片段。问答模型只接收被标记为数据的文档片段，要求以结构化 claim 返回逐字证据；无法通过 citation ID、权限和连续原文校验时，服务返回检索摘要并标记证据不足。

权限检查覆盖候选检索、重排上下文、回答上下文和缓存键。权限或正文 revision 变化后旧缓存失效。关键词检索不依赖模型密钥，向量或排序调用失败时响应的 `retrievalMode` 会标记实际降级路径。

## 索引重建与数据迁移

管理员使用以下接口启动和观察重建：

```text
POST /api/ai/knowledge/index/rebuild
GET  /api/ai/knowledge/index/rebuild/{jobId}
GET  /api/ai/knowledge/index/status
```

请求必须带 Gateway 注入的 `X-User-Role: ADMIN`。重建进度写入 `ai_knowledge_index_rebuild`，偏移、模型、维度和索引版本写入 `ai_knowledge_chunk_metadata`。所有文档向量化和校验成功后才原子切换，失败或重启不会删除旧资料。

迁移现有环境时按以下顺序操作：

1. 先备份 MySQL，再执行服务启动时的只增 schema 初始化。
2. 保持 `AI_KNOWLEDGE_PERSISTENCE_ENABLED=true`，确认原 `ai_knowledge_document` 和 `ai_knowledge_chunk` 可读。
3. 使用新的 1024 维 collection/version 重建，不把旧 96 维 hash 向量混入新索引。
4. 检查管理员重建状态为 `SUCCEEDED` 后，再将 `AI_KNOWLEDGE_VECTOR_ENABLED` 设为目标值。
5. 用学生角色查询受限文档和无答案问题，确认不会返回旧缓存或受限片段。

没有可用 Milvus 时不需要新增基础设施；服务可以使用本地真实向量和关键词候选。没有 DashScope 密钥时，文档导入和关键词检索仍可用，AI 回答会保留检索摘要。

## 固定评估集

评估文件位于 `evaluation/fixtures/`：

- `resume-job.json`：90 组简历岗位配对，Java、前端、运营各 30 组，包含校准和测试分集。
- `interview-answers.json`：45 份固定题目回答，包含简短充分、长但证据不足、错误、否定和项目证据场景。
- `knowledge-queries.json`：60 个知识查询及 51 份对应合成知识文档，包含有答案、无答案和权限隔离场景。

先做静态固定集检查：

```powershell
node scripts/evaluate-core-deepening.cjs --mode validate
```

然后构建后执行真实生产规则类评估：

```powershell
node scripts/evaluate-core-deepening.cjs --mode resume
node scripts/evaluate-core-deepening.cjs --mode interview
```

在 Gateway 可访问且管理员密码通过环境变量提供时执行 RAG API 评估：

```powershell
node scripts/evaluate-core-deepening.cjs --mode rag --env-file .env --output evaluation/reports/rag-acceptance.json
```

RAG 的有答案 test 集单独计算 Recall@5，首轮目标为 `>= 0.85`；无答案和权限集不进入该分母。阈值建议只能根据 calibration 输出，脚本不会读取 test 结果调参。报告不写入密码、token、API key 或模型原始响应。

## 本机验收记录

以下结果于 2026-10-03 在本机 Docker 环境取得。自动测试通过不代表合成样例标签已由项目负责人复核。

| 检查 | 结果 |
| --- | --- |
| 后端全量 `clean install` | 10 个模块构建通过，302 项测试通过 |
| 匹配补充回归 | 2 项通过；企业返回脱敏、同输入并发去重 |
| 前端单元测试和生产构建 | 123 项测试通过，类型检查和 Vite 构建通过 |
| 简历岗位固定集 | 90 组生产规则不变量检查通过，引用可定位 |
| 面试固定集 | 45 份生产评价不变量检查通过；规则回退的语义标签差异作为 advisory 保留 |
| RAG 有答案 test 集 | Top5 命中 28/30，Recall@5 = 93.33%，达到 85% 目标 |
| RAG 无答案、权限和引用 | 正确拒答 6/6，权限泄漏 0，引用校验失败 0 |
| 三类岗位真实模型流程 | Java、前端、运营诊断、任务成果、面试逐题评价和重规划通过 |
| 浏览器与持久化 | 五模块实际页面已验证，快照、成果和面试报告在服务重启后保留 |
| Docker | 15 个容器健康；前端等待网关就绪，并使用 Docker DNS 解析重建后的网关地址 |

全量构建与最后两项回归的命令如下；Maven 继续使用阿里云镜像：

```powershell
mvn.cmd -B -f backend/pom.xml -s backend/settings.xml.example clean install
mvn.cmd -B -f backend/pom.xml -s backend/settings.xml.example -pl match-service -am "-Dtest=MatchEvidenceFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
npm.cmd --prefix frontend run test:unit
npm.cmd --prefix frontend run build
```

真实模型流程验收使用 `scripts/check-core-deepening.cjs`。`MVP_BASE_URL` 指向网关或前端入口，管理员登录信息从环境变量提供。该脚本会创建带“核心验收”前缀的合成岗位和测试账号，并在忽略提交的 `logs/core-deepening-verification.json` 保存业务 ID，供重启后复查。

```powershell
node scripts/check-core-deepening.cjs
# 服务重启并通过健康检查后，仅回读已有资料，不生成新的模型结果。
node scripts/check-core-deepening.cjs --verify-restart
```

RAG 报告保存在忽略提交的 `evaluation/reports/rag-acceptance.json`。不同评估模式可以通过 `--output` 指定独立文件，避免覆盖已有验收证据。

简历诊断与资料更新、删除使用同一实例锁；远程分析返回后再次检查原资料是否仍存在、内容是否改变。分析期间发生的资料更新或删除会返回可重试错误，保留新资料。企业侧匹配查询只清除返回值中的完整简历正文，学生、管理员和持久化原快照仍完整保留。

## 仍需负责人复核的事项

- 合成固定集的人工标签复核和校准阈值确认，仍为 `OWNER_REVIEW_PENDING`。
- 45 份面试样例的模型语义质量评估：规则回退的不变量检查不能代替模型准确性验收。
- 三台虚拟机之间的网络与可选 Milvus 部署尚未在本机验收范围内执行。
- DashScope 长时间限流、熔断恢复和账户配额的持续观察。
