# 系统全面测试报告

- 日期：2026-10-07
- 环境：Docker Compose，本地网关 `http://localhost:18080`，前端 `http://localhost`
- 镜像源：Docker、Maven、npm 均使用项目现有中国镜像配置

## 构建与自动化测试

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 后端 | `cd backend; mvn -s settings.xml.example clean verify` | BUILD SUCCESS；9 个服务共 509 项测试，0 failures、0 errors，1 项既有 skipped |
| 前端单测 | `cd frontend; npm run test:unit` | 162 passed |
| 前端生产构建 | `cd frontend; npm run build` | 成功；仅保留 Vite 大 chunk 提示 |
| API 冒烟 | `scripts/check-api-smoke.ps1 -BaseUrl http://localhost:18080 -CoreOnly` | 全部通过 |
| 边界测试 | `scripts/check-boundary-cases.ps1 -BaseUrl http://localhost:18080 -SkipMonitoring -SkipRestoreSafety` | 28 PASS、0 FAIL、8 项按参数跳过 |
| 压力测试 | `scripts/run-load-smoke.ps1 -BaseUrl http://localhost:18080 -Users 5 -Iterations 10` | 415 PASS、0 FAIL；平均 30.96ms，P95 102.46ms |

边界覆盖登录、错误凭证、缺失及伪造令牌、角色隔离、可信身份覆盖、DOCX 提取、缺文件上传、不存在记录、非法状态、负数归一化、空和超长 AI 输入。监控与恢复检查本轮按命令参数跳过，未计入通过数。

## 浏览器验收

- 知识库：118 checks、31 screenshots、0 browser errors，报告目录为 `output/playwright/knowledge-workspace/2026-10-07T06-47-45-484Z`。
- 简历：生成、PDF/Word 下载、编辑恢复、双页模板、320/390px 移动端均通过，0 JavaScript/CSP errors，报告目录为 `output/playwright/resume-workspace/2026-10-07T06-49-41-697Z`。
- 模拟面试韧性：16 checks、6 screenshots、0 browser errors，覆盖资料请求重试、延迟旧响应、评价失败回退和刷新恢复。
- 模拟面试草稿专项：真实浏览器验证“用户资料后到达、未提交重答、刷新后答案仍在输入框”通过，返回值为 `刷新恢复验证：这是尚未提交的改进回答。`。

## 本轮修复

1. 面试草稿读写改为使用当前登录用户，并在会话先返回、用户资料后返回时重新恢复草稿，避免刷新丢失未提交重答或写入错误账号。
2. AI 搜索、面试答案、行动预览/确认和候选接口拒绝 JSON `null` 请求体，统一返回 400 `ApiResponse`。
3. 知识库统计合并大小写不同的技能键，避免合法 JSON 被大小写不敏感客户端误判为重复键。
4. 边界脚本默认 AI 请求超时调整为 90 秒，覆盖正常 DashScope 延迟，避免将健康请求误报为失败。

首次边界运行使用 15 秒超时，单个真实 AI 请求被误报超时；提高测试超时后同一场景通过，未发现服务端业务错误。

## 运行状态

AI、网关、前端、认证、用户、岗位、匹配、简历、投递及基础设施容器均为 healthy。AI 和前端镜像已按本次代码重新构建并启动。
