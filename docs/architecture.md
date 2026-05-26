# 架构设计

## 服务拆分

- `gateway-service`：统一入口和路由。
- `auth-service`：登录、登出、当前用户信息。
- `user-service`：学生、企业和管理员资料。
- `resume-service`：简历上传、摘要、诊断入口。
- `job-service`：岗位发布、岗位分析入口。
- `match-service`：简历与岗位匹配评分。
- `ai-service`：阿里云百炼封装、mock 降级、结构化结果。
- `delivery-service`：投递记录和状态流转。

## 数据流

1. 学生上传简历到 `resume-service`。
2. `resume-service` 调用 `ai-service` 生成简历诊断。
3. 企业在 `job-service` 发布岗位并可触发 JD 分析。
4. `match-service` 根据简历摘要和岗位要求生成匹配结果。
5. 学生通过 `delivery-service` 投递岗位。
6. 学校端聚合岗位、投递和匹配统计。

## 基础设施

- MySQL：业务数据持久化，MVP 可先用内存仓储演示。
- Redis：缓存热点岗位、匹配结果和验证码。
- Nacos：注册中心和配置中心。
- Sentinel：限流、熔断和降级。
- RocketMQ：简历解析、AI 评分、通知等异步任务。
- MinIO：简历文件对象存储。
- Docker：本机和虚拟机部署。

## 三虚拟机拓扑

- VM1：前端、Gateway、Nacos、Sentinel。
- VM2：业务微服务。
- VM3：MySQL、Redis、RocketMQ、MinIO、AI 服务、监控组件。

