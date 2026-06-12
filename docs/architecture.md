# 架构设计

## 服务拆分

- `gateway-service`：统一入口和路由。
- `auth-service`：登录、登出、当前用户信息。
- `user-service`：学生、企业和管理员资料。
- `resume-service`：简历上传、摘要、诊断入口。
- `job-service`：岗位发布、岗位分析入口。
- `match-service`：简历与岗位匹配评分。
- `ai-service`：阿里云百炼封装、mock 降级、结构化结果，提供简历诊断、简历改写、职业规划、模拟面试、候选人初筛和 AI 可观测能力。
- `delivery-service`：投递记录和状态流转。

## 数据流

1. 学生上传简历到 `resume-service`，Docker 部署时文件写入 VM3 MinIO，并在摘要中返回对象 key 和存储状态。
2. `resume-service` 调用 `ai-service` 生成简历诊断。
3. 学生在前端 AI 求职规划页调用 `ai-service` 的简历改写和职业规划接口，生成简历优化建议、阶段里程碑、技能差距、每周行动、作品集任务和面试准备重点。
4. 企业在 `job-service` 发布岗位并可触发 JD 分析。
5. `match-service` 根据简历摘要和岗位要求生成匹配结果。
6. 学生通过 `delivery-service` 投递岗位，服务生成投递事件并尝试发布到 RocketMQ。
7. `ai-service` 可消费投递事件创建异步候选人初筛任务，并将完成结果写入筛选历史。
8. 学校端通过 `user-service` 管理看板聚合学生、企业、岗位、投递、匹配和漏斗指标，展示周投递趋势、技能需求排行、转化漏斗和风险预警。

## 基础设施

- MySQL：业务数据持久化，MVP 可先用内存仓储演示。
- Redis：缓存热点岗位、匹配结果和验证码。
- Nacos：注册中心和配置中心。
- Sentinel：限流、熔断和降级。
- RocketMQ：投递创建和状态变更事件；后续可扩展简历解析、AI 评分、通知等异步任务。
- MinIO：简历文件对象存储。
- Docker：本机和虚拟机部署。

## 三虚拟机拓扑

- VM1：前端、Gateway、Nacos、Sentinel。
- VM2：业务微服务。
- VM3：MySQL、Redis、RocketMQ、MinIO、AI 服务、监控组件。
# v3.11 RAG Ingestion and Vector Architecture

- VM3 now also hosts Milvus for RAG chunk vector search. The compose file keeps the business MinIO and the Milvus internal MinIO separate to avoid port and bucket coupling.
- `ai-service` uploads original knowledge files to MinIO when `AI_KNOWLEDGE_OBJECT_STORAGE_ENABLED=true`.
- `ai-service` tracks ingestion jobs in MySQL table `ai_knowledge_ingestion_job` when knowledge persistence is enabled.
- `ai-service` extracts text from `.txt`, `.md`, `.pdf`, `.doc`, and `.docx`, saves normalized documents/chunks, then upserts chunk vectors into Milvus when `AI_KNOWLEDGE_VECTOR_ENABLED=true`.
- RAG search asks Milvus first and automatically falls back to local hash-vector retrieval if Milvus is disabled, unavailable, or empty.
- New VM3 middleware: MySQL, Redis, RocketMQ, business MinIO, Milvus etcd, Milvus internal MinIO, Milvus standalone, and `ai-service`.
