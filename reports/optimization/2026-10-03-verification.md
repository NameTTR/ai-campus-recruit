# 项目优化验收记录

- 日期: 2026-10-03 (Asia/Shanghai)
- 工作区: `D:\project\bishe`
- 分支: `feat/graduation-core-mvp`
- 范围: 完成已有功能的可靠性、错误处理、访问控制及部署配置优化，没有新增业务功能。
- 状态: 截至最终提交前，Docker Desktop 故障已修复，完整构建、真实核心流程、重启恢复及浏览器验收已通过。最终提交与推送由主会话负责。

本记录供开发者和部署维护者复核优化结果，区分实际运行验证与仍存在的工程边界。
此前受限执行账户造成的 Docker、Maven、浏览器和 Git 阻塞已解除，不再作为当前验收结论。

## 已完成的修复

- Gateway 在请求进入时统一认证和清理外部身份头，覆盖自身文档、Actuator、编码路径和路径参数。健康检查保持公开，其他管理接口限制管理员。
- CORS 在认证前处理，允许的浏览器来源可以读取 401/403 和请求编号；健康检查、预检和失败响应也带 `X-Request-Id`。
- 仅 POST 登出作废令牌；每次登录签发带随机 `jti` 的独立 JWT，解决同一秒内重新登录拿到已作废令牌的问题。旧 JWT 仍兼容。
- 简历上传失败不保存元数据；数据库保存失败补偿删除已上传文件；数据库删除失败不先删除文件。上传成功与失败都关闭输入流。
- 投递的企业归属使用显式映射、已启用的演示映射或真实岗位数据。岗位不存在返回 400；依赖故障返回 503，不保存投递。
- 异步初筛分别读取真实简历技能、项目和岗位要求；依赖或任务保存失败交给 RocketMQ 重试，已提交任务按投递去重。
- AI 调用增加超时、并发上限和熔断；等待并发许可后重新检查状态，旧请求的晚到结果不能改变新熔断窗口。
- 实时大屏无法访问数据源时返回 503；前端默认显示真实服务错误，演示 fallback 需要显式开启。查询接口也统一清理失效登录会话。
- Docker 配置增加健康检查、重启策略和依赖就绪条件；本地业务端口保持在容器网络，基础设施端口绑定回环地址。
- 中国源继续使用 DaoCloud 镜像、阿里云 Maven 和 npmmirror。补齐三机 AI 参数、投递岗位服务地址以及前端演示构建参数。
- 三机 Prometheus 的 Gateway 抓取配置配套使用管理员 Bearer Token，通过 `credentials_file` 从只读挂载的本机 secrets 目录读取。凭据文件忽略版本管理，部署文档说明生成和刷新方式，保持 Gateway 管理端点鉴权。
- Nginx 请求体上限为 `12m`，连接超时为 `5s`，读写超时为 `180s`。代理为后端 10 MB 文件限制预留 multipart 开销，并允许真实 AI 请求等待响应。
- CI 的浏览器 smoke 显式使用演示模式，忽略历史真实数据文件并清空代理；真实验证关闭演示模式。
- 浏览器验收脚本明确选择 fixture 简历、完整计划标签和精确面试 session，避免多份真实数据使默认选择与测试假设不一致。

## 验证结果

| 验证项 | 结果 | 证据 |
| --- | --- | --- |
| 完整后端编译与测试 | 通过: 47 个测试类、246 项测试，0 失败、0 错误、0 跳过 | `logs/validation/backend-final-tests.log`、`backend-final-summary.json` |
| 前端单元测试 | 通过: 118 项测试 | `npm.cmd --prefix frontend run test:unit` |
| 前端生产构建 | 通过: 类型检查和 Vite 构建 | `npm.cmd --prefix frontend run build` |
| 后端可运行 JAR 打包 | 通过: 从阿里云补齐插件，全部模块 `BUILD SUCCESS` | `logs/validation/backend-full-access-package.log` |
| 四套 Compose 配置 | 本地、VM1、VM2、VM3 全部通过 | `logs/validation/deployment-final-summary.json` |
| 部署参数集成 | 投递岗位地址、健康依赖、AI 参数和前端构建参数已校验 | 同上 |
| 三机监控鉴权配套 | 两份 Prometheus 配置增加 Bearer 凭据文件；VM1 Compose、15 个文档 PowerShell 代码块、凭据目录忽略和差异检查通过 | `deploy/monitoring/prometheus.yml`、模板、VM1 Compose 和三机部署文档 |
| 部署示例配置检查 | 通过: 示例凭据按模板模式提示，不代表生产凭据已验证 | `scripts/check-security-hardening.ps1` |
| Docker 构建和启动 | 通过: `up -d --build --wait --wait-timeout 600` 完成，15 个项目容器全部运行且健康 | `logs/validation/docker-full-access-start.log` |
| Nginx 最终镜像 | 已重新构建部署，`nginx -T` 确认请求体限制与超时生效，语法检查通过 | `logs/validation/docker-frontend-final-build.log` |
| 前端健康检查 | `http://127.0.0.1/health` 返回 200 | `logs/validation/final-deployment-check.json` |
| Gateway 健康检查 | `http://127.0.0.1:18080/actuator/health` 返回 `UP` | `logs/validation/final-deployment-check.json` |
| 真实核心 API 流程 | 通过: 经 Nginx、Gateway 和业务服务执行 | `logs/validation/docker-core-mvp.log` |
| 真实 AI 调用 | 学习计划、面试和 RAG 均为 `mocked=false`，RAG provider 为 `dashscope` | `logs/core-mvp-verification.json` |
| 八个后端及 Gateway 服务重启恢复 | 通过: 账号、档案、诊断、岗位、匹配、学习进度、面试报告和知识库数据恢复 | `logs/validation/docker-core-restart.log` |
| Gateway 安全与登录 | 通过: 编码/矩阵路径、伪造身份、CORS、请求编号、登出和立即重登录 | `logs/validation/docker-gateway-security.log` |
| Nginx 上传代理 | 通过: 2 MB 无效 fixture 到达 API 校验，没有创建简历 | `logs/validation/docker-upload-proxy.log` |
| 真实投递与 RocketMQ 初筛 | 通过: 动态企业归属正确，恰好一个任务完成，`mocked=false`、score 72，数据库输入快照匹配真实 API 数据 | `logs/validation/docker-delivery-screening.log`、`docker-delivery-screening.json` |
| 演示浏览器 E2E | 通过: 24 张截图 | `frontend/.e2e-artifacts/` |
| 真实 Docker 浏览器 E2E | 通过: `VITE_DEMO_MODE=false`，Chrome，退出码 0 | `frontend/.e2e-artifacts/docker-live/e2e-success.log` |
| 浏览器脚本语法 | 通过 | `node --check frontend/scripts/e2e-smoke.cjs` |
| 修改的空白格式检查 | 通过 | `git diff --check` |
| Git 连接与交付 | 提交前差异与凭据检查通过，提交号及远端结果见主会话最终交付 | 当前分支 `feat/graduation-core-mvp` |

后端各模块测试数:

| 模块 | 通过项数 |
| --- | --- |
| ai-service | 98 |
| auth-service | 13 |
| common | 6 |
| delivery-service | 29 |
| gateway-service | 31 |
| job-service | 9 |
| match-service | 13 |
| resume-service | 25 |
| user-service | 22 |

真实核心验证覆盖简历诊断、67% 岗位技能覆盖率、Java/前端/非技术岗位证据、学习计划、面试、RAG、
跨用户权限、知识库角色权限变更及文件导入。诊断记录的来源为 `AI_TEXT_RULE_SCORE`；学习计划、面试和 RAG 的真实调用标志分别核验。

真实浏览器 E2E 覆盖简历取消删除、草稿跨页刷新、匹配恢复及上下文、学习任务恢复、503 时保留草稿和重试、
历史只读、面试报告逐题反馈、仅检索 RAG，以及学生、企业、管理员的桌面和手机页面。
`docker-live` 中旧的 `failure.png` 和 `failure-text.txt` 是测试脚本修复前的复现记录，最终结果以 `e2e-success.log` 为准。

真实投递验证还确认了不存在岗位返回 HTTP 400 且投递数不变，fixture 岗位使用实际企业归属而非默认 `C001`。
已完成任务的 `request_snapshot` 中，简历技能、项目、格式以及岗位标题、要求、描述与真实 API 数据一致。

日志、截图和验证元数据均留在本地忽略目录，不加入版本管理；元数据文件不保存 token。
投递联调命令为 `node logs/validation/check-live-screening.cjs`；该脚本属于本地验收材料，随 `logs` 忽略，不加入版本管理。

## Docker Desktop 故障修复

本机 Docker Desktop `4.54.0` 启动时崩溃，日志中的具体错误为:

```text
starting services: initializing Inference manager:
...Docker\run\dockerInference...
The file cannot be accessed by the system.
```

相同症状的公开参考见 [Docker Desktop Windows Inference manager 启动失败](https://github.com/docker/desktop-feedback/issues/531)。本次故障判断以本机日志为依据，修复结果以引擎版本和容器健康验证为依据。
修复退出 Docker Desktop 后的运行目录和可选 Docker AI 设置，没有重置 Docker 或清理数据。

1. 将普通目录 `C:\Users\G5080\AppData\Local\Docker\run` 重命名为 `run.before-repair-20261003-114124`。
2. 将普通目录 `C:\Users\G5080\AppData\Local\docker-secrets-engine` 重命名为 `docker-secrets-engine.before-repair-20261003-114124`。
3. 备份 `settings-store.json` 为 `C:\Users\G5080\AppData\Roaming\Docker\settings-store.before-repair-20261003-114124.json`。
4. 仅将原设置中的 `EnableDockerAI` 改为 `false`，随后重新启动 Docker Desktop。

修复后 Docker Server `29.1.2` 可用，WSL 中的 `docker-desktop` 正常运行。
原有镜像、容器和数据卷保留，没有对其他项目执行停容器或清理操作。
备份清单见 [docker-repair-backups.json](../../logs/validation/docker-repair-backups.json)。

此设置关闭 Docker Desktop 的可选 Docker AI；项目通过环境变量配置的 DashScope 调用已独立验证，不受该设置影响。
复现条件、安全备份步骤和恢复方法见 [Docker 国内源与启动故障处理](../../docs/deploy/docker-mirror.md)。

## 可复现的验证命令

以下命令在仓库根目录执行，后端使用 JDK 17。先确认 `docker version` 同时显示 Client 和 Server。
本机 `.env` 已配置真实 AI 及管理员凭据，不将该文件或凭据写入验收报告。

```powershell
mvn -B -o -f backend/pom.xml -s backend/settings.xml.example test
mvn -B -f backend/pom.xml -s backend/settings.xml.example package -DskipTests
npm.cmd --prefix frontend run test:unit
npm.cmd --prefix frontend run build
node --check frontend/scripts/e2e-smoke.cjs
docker compose config --quiet
docker compose --env-file deploy/three-vm.env.example -f deploy/docker-compose.vm1.yml config --quiet
docker compose --env-file deploy/three-vm.env.example -f deploy/docker-compose.vm2.yml config --quiet
docker compose --env-file deploy/three-vm.env.example -f deploy/docker-compose.vm3.yml config --quiet
.\scripts\check-security-hardening.ps1 -EnvFile .\deploy\three-vm.env.example -AllowExampleDefaults
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1/health'
Invoke-RestMethod 'http://127.0.0.1:18080/actuator/health'
git diff --check
```

完整测试和可运行 JAR 打包分别验证，`package -DskipTests` 没有替代前面的 246 项测试。
若本地没有完整 Maven 缓存，移除测试命令的 `-o`，继续使用仓库内的阿里云配置。

核心脚本会创建独立测试账号和内容，并将不含 token 的 fixture 元数据保存在 `logs/core-mvp-verification.json`。
管理员和测试账号配置应与实际环境一致；非默认验证配置通过脚本支持的 `MVP_ADMIN_USER`、`MVP_ADMIN_PASSWORD` 和 `MVP_SMOKE_PASSWORD` 环境变量传入。
先运行核心流程，再运行重启恢复及浏览器验收:

```powershell
$env:MVP_BASE_URL = 'http://127.0.0.1'
node scripts/check-core-mvp.cjs
docker compose restart auth-service user-service resume-service job-service match-service ai-service delivery-service gateway-service
docker compose up -d --wait --wait-timeout 600
node scripts/check-core-mvp.cjs --verify-restart
$env:VITE_DEMO_MODE = 'false'
$env:E2E_BASE_URL = 'http://127.0.0.1'
$env:E2E_ARTIFACTS_DIR = Join-Path (Get-Location).Path 'frontend\.e2e-artifacts\docker-live'
npm.cmd --prefix frontend run test:e2e
```

浏览器脚本自动寻找本机 Chrome/Edge，也可通过 `E2E_BROWSER` 指定已安装浏览器的完整路径。
启动流程见 [README](../../README.md)，业务验收细节见 [核心 MVP 验收](../../docs/core-mvp.md)。

## 实际保证的边界

- 当前验证的是本机 Docker Compose 部署。VM1/VM2/VM3 配置解析通过，不代表三个物理节点已经部署运行。
- 容器健康、真实核心流程和重启恢复均已验证，但没有据此声称生产负载、全部依赖故障或消息重试场景都已验收。
- Gateway 登出失效记录保存在当前进程，重启后清空，多个 Gateway 实例不共享。
- 三机监控抓取依赖本机提供的管理员 Token 文件；Token 到期或被撤销后需要按 [三机部署文档](../../docs/deploy/three-vm-deploy.md) 重新生成和替换。本轮没有生成真实监控 Token，也没有部署三台物理机器，此配置配套不代表三机 Prometheus 抓取已经实测。
- 对象删除属于尽力补偿；MinIO 删除失败会留下日志，需要后续清理。
- 2 MB 无效上传验证代理放行和后端拒绝行为，没有声称无效文件成功导入。
- 演示 E2E 与真实 Docker E2E 分开运行；真实运行的模型标志和持久化结果以核心脚本日志及 fixture 元数据为证。
- 代码审查、最终差异检查、提交和推送由主会话负责，真实 `.env`、AI Key 和本机备份继续留在版本管理之外。
