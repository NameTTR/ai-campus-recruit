# v0.9 三虚拟机部署手册

本文档面向三台 Ubuntu 虚拟机部署。三台机器都克隆同一份仓库，并使用同一份 `deploy/three-vm.env`。根目录 `docker-compose.yml` 仍保留为单机编排；三机部署使用 `deploy/docker-compose.vm1.yml`、`deploy/docker-compose.vm2.yml`、`deploy/docker-compose.vm3.yml`。

不要把真实 `.env`、`deploy/three-vm.env` 或 AI API Key 提交到仓库。

## VM 角色

| VM | 示例 IP | 角色 | 服务 |
| --- | --- | --- | --- |
| VM1 | `192.168.56.11` | 入口与注册中心 | Nacos、`gateway-service`、前端 Nginx |
| VM2 | `192.168.56.12` | 业务服务 | `auth-service`、`user-service`、`resume-service`、`job-service`、`match-service`、`delivery-service` |
| VM3 | `192.168.56.13` | 数据、中间件与 AI | MySQL、Redis、MinIO、RocketMQ、`ai-service` |

当前 v0.9 Compose 未包含 Sentinel Dashboard、Prometheus、Grafana。监控组件后续单独部署。

## 端口

| VM | 服务 | 端口 | 访问范围 | 验证路径 |
| --- | --- | --- | --- | --- |
| VM1 | 前端 Nginx | `80` | 浏览器 | `http://<VM1_IP>/` |
| VM1 | `gateway-service` | `8080` | 前端、运维调试 | `/actuator/health` |
| VM1 | Nacos HTTP | `8848` | VM2、VM3、运维机 | `/nacos/` |
| VM1 | Nacos gRPC | `9848` | VM2、VM3 | 服务注册发现 |
| VM2 | `auth-service` | `8101` | VM1 Gateway | `/actuator/health`、`/swagger-ui.html`、`/v3/api-docs` |
| VM2 | `user-service` | `8102` | VM1 Gateway | `/actuator/health`、`/swagger-ui.html`、`/v3/api-docs` |
| VM2 | `resume-service` | `8103` | VM1 Gateway | `/actuator/health`、`/swagger-ui.html`、`/v3/api-docs` |
| VM2 | `job-service` | `8104` | VM1 Gateway | `/actuator/health`、`/swagger-ui.html`、`/v3/api-docs` |
| VM2 | `match-service` | `8105` | VM1 Gateway | `/actuator/health`、`/swagger-ui.html`、`/v3/api-docs` |
| VM2 | `delivery-service` | `8107` | VM1 Gateway | `/actuator/health`、`/swagger-ui.html`、`/v3/api-docs` |
| VM3 | `ai-service` | `8106` | VM1 Gateway、VM2 简历/岗位服务 | `/actuator/health`、`/api/ai/status`、`/swagger-ui.html` |
| VM3 | MySQL | `3306` | VM3 本机或受限内网 | `mysqladmin ping` |
| VM3 | Redis | `6379` | VM3 本机或受限内网 | `redis-cli ping` |
| VM3 | MinIO S3 API | `9000` | 内网 | `/minio/health/ready` |
| VM3 | MinIO Console | `9001` | 运维机 | `http://<VM3_IP>:9001` |
| VM3 | RocketMQ NameServer | `9876` | 内网 | 容器日志 |
| VM3 | RocketMQ Broker | `10909`、`10911` | 内网 | 容器日志 |

最小防火墙策略：

- VM1：对浏览器开放 `80`；对 VM2、VM3 开放 `8848`、`9848`；按需对运维机开放 `8080`。
- VM2：只对 VM1 开放 `8101`、`8102`、`8103`、`8104`、`8105`、`8107`。
- VM3：对 VM1、VM2 开放 `8106`；按需对运维机开放 `9001`；MySQL、Redis、RocketMQ、MinIO API 优先限制在内网。

## 国内镜像源

三台 VM 都安装 Docker 和 Docker Compose v2。Docker daemon 建议配置国内镜像源，详见 `docs/deploy/docker-mirror.md`。

项目内已做国内源兼容：

- Docker 镜像使用 `docker.m.daocloud.io`。
- 后端构建使用 `backend/settings.xml.example`，内置阿里云 Maven 镜像。
- 前端 Dockerfile 使用 `https://registry.npmmirror.com`。

Ubuntu 可按发行版替换清华源或阿里云源。替换前先备份：

```bash
sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak
sudo apt update
```

## 环境变量

三台 VM 都执行：

```bash
cp deploy/three-vm.env.example deploy/three-vm.env
```

编辑 `deploy/three-vm.env`：

```env
VM1_HOST=192.168.56.11
VM2_HOST=192.168.56.12
VM3_HOST=192.168.56.13

MYSQL_ROOT_PASSWORD=<strong_mysql_password>
MYSQL_DATABASE=ai_campus_recruit

DASHSCOPE_API_KEY=
DASHSCOPE_MODEL=qwen-plus
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

AI_SCREENING_PERSISTENCE_ENABLED=true
AI_SCREENING_CACHE_TTL=10m

FRONTEND_PORT=80
GATEWAY_PORT=8080
```

关键变量说明：

| 变量 | 用途 |
| --- | --- |
| `VM1_HOST` | Nacos、Gateway、前端所在 VM 的内网地址 |
| `VM2_HOST` | 业务服务所在 VM 的内网地址，Gateway 会转发到这里 |
| `VM3_HOST` | AI 服务和中间件所在 VM 的内网地址 |
| `MYSQL_ROOT_PASSWORD` | VM3 MySQL root 密码 |
| `MYSQL_DATABASE` | 默认业务数据库，当前为 `ai_campus_recruit` |
| `DASHSCOPE_API_KEY` | 阿里云百炼 API Key；为空时 AI 服务进入 mock 演示模式 |
| `DASHSCOPE_MODEL` | 默认 `qwen-plus` |
| `DASHSCOPE_BASE_URL` | 默认 `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `AI_SCREENING_PERSISTENCE_ENABLED` | AI 候选人初筛历史是否写入 MySQL，v0.9 建议为 `true` |
| `AI_SCREENING_CACHE_TTL` | AI 初筛历史 Redis 缓存 TTL |
| `FRONTEND_PORT` | VM1 前端暴露端口 |
| `GATEWAY_PORT` | VM1 Gateway 暴露端口 |

服务内实际使用的关键环境变量：

- VM1 `gateway-service`：`AUTH_SERVICE_URI=http://${VM2_HOST}:8101`、`USER_SERVICE_URI=http://${VM2_HOST}:8102`、`RESUME_SERVICE_URI=http://${VM2_HOST}:8103`、`JOB_SERVICE_URI=http://${VM2_HOST}:8104`、`MATCH_SERVICE_URI=http://${VM2_HOST}:8105`、`DELIVERY_SERVICE_URI=http://${VM2_HOST}:8107`、`AI_SERVICE_URI=http://${VM3_HOST}:8106`。
- VM2 业务服务：`NACOS_ENABLED=true`、`NACOS_SERVER_ADDR=${VM1_HOST}:8848`；`resume-service` 和 `job-service` 额外使用 `AI_SERVICE_URI=http://${VM3_HOST}:8106`。
- VM3 `ai-service`：`NACOS_ENABLED=true`、`NACOS_SERVER_ADDR=${VM1_HOST}:8848`、`SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/${MYSQL_DATABASE}`、`SPRING_DATA_REDIS_HOST=redis`。

## 启动顺序

启动前每台 VM 都先校验本机 Compose 文件：

```bash
# VM1
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml config --quiet

# VM2
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml config --quiet

# VM3
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml config --quiet
```

### 1. VM1 启动 Nacos

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d nacos
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml ps nacos
curl -f http://127.0.0.1:8848/nacos/
```

在 VM2、VM3 上确认能访问 VM1 Nacos：

```bash
curl -f http://<VM1_IP>:8848/nacos/
```

### 2. VM3 启动数据组件

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d \
  mysql redis minio rocketmq-namesrv rocketmq-broker
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml ps
```

验证：

```bash
set -a
. ./deploy/three-vm.env
set +a
docker exec recruit-vm3-mysql mysqladmin ping -uroot -p"${MYSQL_ROOT_PASSWORD}"
docker exec recruit-vm3-redis redis-cli ping
curl -f http://127.0.0.1:9000/minio/health/ready
```

### 3. VM3 启动 AI 服务

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d --build ai-service
curl -f http://127.0.0.1:8106/actuator/health
curl -sS http://127.0.0.1:8106/api/ai/status
```

### 4. VM2 启动业务服务

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml up -d --build
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml ps
```

验证：

```bash
for port in 8101 8102 8103 8104 8105 8107; do
  curl -f "http://127.0.0.1:${port}/actuator/health"
done

curl -f http://127.0.0.1:8101/v3/api-docs
curl -f http://127.0.0.1:8103/swagger-ui.html
```

### 5. VM1 启动 Gateway 和前端

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d --build gateway-service frontend
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml ps
```

验证：

```bash
curl -f http://127.0.0.1:8080/actuator/health
curl -I http://127.0.0.1/
curl -sS http://127.0.0.1:8080/api/ai/status
```

浏览器访问：

```text
http://<VM1_IP>/
```

## 验证命令

在任意能访问三台 VM 的机器上执行：

```bash
curl -sS -X POST "http://<VM1_IP>:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"student","password":"123456"}'

curl -sS "http://<VM1_IP>:8080/api/students/profile"
curl -sS "http://<VM1_IP>:8080/api/jobs"
curl -sS "http://<VM1_IP>:8080/api/ai/status"
curl -sS "http://<VM1_IP>/api/ai/status"
```

OpenAPI 直连路径：

```text
http://<VM2_IP>:8101/swagger-ui.html
http://<VM2_IP>:8102/swagger-ui.html
http://<VM2_IP>:8103/swagger-ui.html
http://<VM2_IP>:8104/swagger-ui.html
http://<VM2_IP>:8105/swagger-ui.html
http://<VM2_IP>:8107/swagger-ui.html
http://<VM3_IP>:8106/swagger-ui.html
```

验证 AI 筛选持久化：

```bash
curl -sS -X POST "http://<VM1_IP>:8080/api/ai/candidates/screen" \
  -H "Content-Type: application/json" \
  -d '{"deliveryId":"D900","companyId":"C001","studentId":"S001","resumeId":"R001","jobId":"J001","targetRole":"Java 后端实习生","skills":["Java","Spring Boot","MySQL"],"projects":["校园招聘平台"],"jobRequirements":["Java","MySQL"],"resumeSummary":"Java Web 项目经验","jobDescription":"后端接口开发"}'

curl -sS "http://<VM1_IP>:8080/api/ai/candidates/screenings?companyId=C001"
```

重启 VM3 的 `ai-service` 后再次查询，记录仍应存在：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml restart ai-service
curl -sS "http://<VM1_IP>:8080/api/ai/candidates/screenings?companyId=C001"
```

演示账号：

- `student / 123456`
- `company / 123456`
- `admin / 123456`

## 常见问题

**Gateway 健康，但业务接口 502 或连接失败**

检查 VM1 Gateway 容器中的 URI 是否指向 VM2、VM3：

```bash
docker exec recruit-vm1-gateway-service printenv | grep SERVICE_URI
```

同时确认 VM2/VM3 防火墙开放 `8101`-`8107`、`8106`。

**前端能打开，但 `/api/*` 失败**

VM1 前端 Nginx 使用 `GATEWAY_UPSTREAM` 生成配置，默认应为 `http://gateway-service:8080`。检查：

```bash
docker exec recruit-vm1-frontend printenv | grep GATEWAY_UPSTREAM
docker logs recruit-vm1-frontend --tail 50
```

**Nacos 控制台没有服务实例**

确认 VM2、VM3 后端服务都设置了 `NACOS_ENABLED=true` 和 `NACOS_SERVER_ADDR=<VM1_IP>:8848`，并确认 VM1 的 `8848`、`9848` 对 VM2/VM3 可达：

```bash
docker exec recruit-vm2-auth-service printenv | grep NACOS
docker exec recruit-vm3-ai-service printenv | grep NACOS
curl -f http://<VM1_IP>:8848/nacos/
```

**`ai-service` 返回 `mocked=true`**

这是预期降级行为。未配置 `DASHSCOPE_API_KEY` 或模型调用失败时，AI 接口仍返回可演示结果。配置真实 Key 后重启 VM3：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d ai-service
```

**AI 初筛历史没有持久化**

检查 VM3 环境变量、MySQL 和 Redis：

```bash
set -a
. ./deploy/three-vm.env
set +a
docker exec recruit-vm3-ai-service printenv | grep AI_SCREENING
docker exec recruit-vm3-ai-service printenv | grep SPRING_DATASOURCE_URL
docker exec recruit-vm3-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "USE ai_campus_recruit; SHOW TABLES;"
docker exec recruit-vm3-redis redis-cli ping
```

**Docker 拉取镜像慢或失败**

确认 Docker daemon 已按 `docs/deploy/docker-mirror.md` 配置国内镜像源并重启 Docker。当前镜像源优先使用 `docker.m.daocloud.io`；离线环境可在能联网机器上 `docker pull` 后通过 `docker save` / `docker load` 迁移。

**端口被占用**

VM1 可通过 `FRONTEND_PORT`、`GATEWAY_PORT` 改前端和 Gateway 暴露端口。后端服务端口来自各服务 `application.yml`，改动会影响 Gateway 路由和文档，不建议在 v0.9 部署时临时调整。

**RocketMQ 和 MinIO 是否已经被业务强依赖**

v0.9 先把 RocketMQ、MinIO 作为分布式基础设施启动。当前 AI 初筛持久化主要依赖 MySQL 和 Redis；后续异步任务、文件对象存储接入时复用 VM3。
