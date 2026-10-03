# AI Campus Recruit

基于 Spring Cloud Alibaba 与阿里云百炼的 AI 简历诊断与校园招聘匹配平台。

## Modules

- `backend`: Spring Boot 3 / Spring Cloud Alibaba 多模块后端
- `frontend`: Vue 3 三端前端，包含学生端、企业端、学校端
- `docs`: 需求、架构、API、部署和 AI 开发规范
- `docker-compose.yml`: 本地和虚拟机部署编排

## Quick Start

```powershell
cd D:\project\bishe
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
cd backend
mvn -s settings.xml.example clean package
cd ..\frontend
npm config set registry https://registry.npmmirror.com
npm install
npm run dev
```

后端可先分别启动 `ai-service`、`auth-service`、`resume-service`、`job-service`、`match-service`、`delivery-service` 和 `gateway-service`。

也可以直接启动本地开发环境：

```powershell
.\scripts\start-local-dev.ps1
```

使用 Docker Compose 启动完整环境（镜像、Maven 和 npm 已配置为中国可访问源）：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --build
docker compose ps
Invoke-WebRequest http://localhost/health
Invoke-WebRequest http://localhost:8080/actuator/health
```

本地 Compose 的前端端口默认 `80`，Gateway 默认 `8080`，可在 `.env` 中分别设置
`FRONTEND_HOST_PORT` 和 `GATEWAY_HOST_PORT`；修改后按实际端口访问和检查健康状态。
基础设施的宿主机端口绑定到 `127.0.0.1`，业务服务通过 Docker 网络互相访问。
需要调试单个业务服务时，添加独立的 Compose 覆盖文件发布该服务端口。
三机部署的业务端口需供虚拟机间访问，部署规则见下方链接。

浏览器来源由 `GATEWAY_ALLOWED_ORIGINS` 控制，Knife4j/OpenAPI 公开访问由
`GATEWAY_PUBLIC_DOCS_ENABLED` 控制；示例配置默认要求登录后访问接口文档。
前端 API fallback 只有构建变量 `VITE_DEMO_MODE=true` 时启用，正常环境直接报告后端错误。
修改该变量后执行 `docker compose up -d --build frontend` 重新构建前端。

首次启动前，在 `.env` 中设置足够长的随机 `JWT_SECRET` 和 `BOOTSTRAP_ADMIN_PASSWORD`。
不要提交真实凭据。若需要已有的演示账号，可显式设置 `DEMO_SEED_ENABLED=true`；默认不开启。

没有运行 Nacos、MySQL、Redis、RocketMQ、MinIO 或 Milvus 时，使用本地内存演示模式：

```powershell
.\scripts\start-local-dev.ps1 -DemoMode
```

该模式仍会读取 `.env`，但会在启动前关闭外部中间件、持久化、对象存储和向量库开关。数据仅保存在各服务进程内存中，服务停止或重启后会丢失，不能用于保存真实业务数据。

毕设版本聚焦五个学生模块：简历诊断、岗位匹配、AI 学习路径、AI 模拟面试、RAG 知识问答。企业端负责岗位维护，管理端负责账号及知识库管理。投递、初筛、通知、审计及部署等辅助页面下线，后端接口和历史数据继续保留。

需要保留业务数据时，先启动 MySQL、Redis，再使用正常启动模式：

```powershell
docker compose up -d mysql redis
.\scripts\start-local-dev.ps1
```

`AUTH_PERSISTENCE_ENABLED`、`USER_PERSISTENCE_ENABLED`、`AI_CORE_PERSISTENCE_ENABLED` 在本地启动脚本中默认开启，分别保存账号、学生资料、学习计划和面试会话。其他业务持久化开关沿用 `.env`。数据库不可用时，核心写入会报错，不能当作已保存。

首次使用可在 `.env` 设置 `BOOTSTRAP_ADMIN_PASSWORD` 创建管理员 `admin`，或明确执行 `.\scripts\start-local-dev.ps1 -SeedDemoData` 加入样例账号和资料，同时保留数据库存储。普通启动默认不新建演示数据；`-DemoMode` 会明确启用演示种子。已有记录不会因重复初始化被覆盖。

MySQL 主机端口由 `MYSQL_HOST_PORT` 指定；Redis 主机端口由 `REDIS_HOST_PORT` 指定，本地 Java 的 `SPRING_DATA_REDIS_PORT` 应与其一致。如果已有其他项目占用 6379，可将这两个 Redis 端口都设置为 16379。Compose 内的服务仍通过 `redis:6379` 连接。

如果已存在独立创建的 `recruit-mysql` 容器，改用 `docker start recruit-mysql` 恢复原数据库，再运行 `docker compose up -d redis`。完整演示流程和重启验证见 [毕设核心功能说明](docs/core-mvp.md)。

访问：

- 前端：`http://localhost:5173`
- 网关：`http://localhost:8080`
- Knife4j 聚合接口文档：`http://localhost:8080/doc.html`（如 8080 被占用，本地脚本会使用 `http://localhost:18080/doc.html`）
- 单服务 Knife4j：业务服务按端口 `8101`-`8107` 访问 `/doc.html`

三台虚拟机分布式部署见：`docs/deploy/three-vm-deploy.md`。对应 compose 文件在 `deploy/` 目录：

- `deploy/docker-compose.vm1.yml`：Nacos、Gateway、前端
- `deploy/docker-compose.vm2.yml`：业务服务
- `deploy/docker-compose.vm3.yml`：AI 服务和中间件

三机启动后可从 Windows 宿主机或任意能访问三台 VM 的 Linux 机器执行健康检查和业务 smoke：

```powershell
.\scripts\check-three-vm-health.ps1 -EnvFile .\deploy\three-vm.env -TimeoutSeconds 5
.\scripts\check-api-smoke.ps1 -BaseUrl http://<VM1_IP>:8080
```

```bash
bash scripts/check-three-vm-health.sh --env-file deploy/three-vm.env --timeout 5
bash scripts/check-api-smoke.sh --base-url http://<VM1_IP>:8080
```

发布说明见 `docs/releases/`：

- `docs/releases/v1.0.md`：三机健康检查与运维基线
- `docs/releases/v1.1.md`：MinIO 简历对象存储
- `docs/releases/v1.2.md`：RocketMQ 投递事件
- `docs/releases/v1.3.md`：API smoke 与部署验收脚本

## GitHub

本机已安装 GitHub CLI。首次推送前先登录：

```powershell
gh auth login
gh repo create ai-campus-recruit --public --source . --remote origin --push
```

## Demo Accounts

- 学生：`student / 123456`
- 企业：`company / 123456`
- 学校管理员：`admin / 123456`

## Verification

```powershell
cd backend
mvn -s settings.xml.example test
cd ..\frontend
npm run test:unit
npm run build
cd ..
docker compose config --quiet
```

## AI Configuration

真实调用阿里云百炼时配置：

```env
DASHSCOPE_API_KEY=your_api_key
DASHSCOPE_MODEL=qwen-plus
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
DASHSCOPE_MAX_CONCURRENCY=8
DASHSCOPE_ACQUIRE_TIMEOUT=2s
DASHSCOPE_FAILURE_THRESHOLD=3
DASHSCOPE_CIRCUIT_OPEN_TIME=30s
```

未配置 Key 时，`ai-service` 会返回可演示的 mock 结果。
