# 三虚拟机部署手册

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
| VM3 | MySQL | `3306` | VM2、VM3 本机或受限内网 | `mysqladmin ping` |
| VM3 | Redis | `6379` | VM2、VM3 本机或受限内网 | `redis-cli ping` |
| VM3 | MinIO S3 API | `9000` | 内网 | `/minio/health/ready` |
| VM3 | MinIO Console | `9001` | 运维机 | `http://<VM3_IP>:9001` |
| VM3 | RocketMQ NameServer | `9876` | 内网 | 容器日志 |
| VM3 | RocketMQ Broker | `10909`、`10911` | 内网 | 容器日志 |

最小防火墙策略：

- VM1：对浏览器开放 `80`；对 VM2、VM3 开放 `8848`、`9848`；按需对运维机开放 `8080`。
- VM2：只对 VM1 开放 `8101`、`8102`、`8103`、`8104`、`8105`、`8107`。
- VM3：对 VM1、VM2 开放 `8106`；对 VM2 开放 MySQL `3306`、Redis `6379` 和 RocketMQ `9876`、`10909`、`10911`；按需对运维机开放 `9001`；MySQL、Redis、RocketMQ、MinIO API 优先限制在内网。

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
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=<strong_minio_password>
MINIO_BUCKET=resumes

DASHSCOPE_API_KEY=
DASHSCOPE_MODEL=qwen-plus
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

AI_SCREENING_PERSISTENCE_ENABLED=true
AI_SCREENING_CACHE_TTL=10m
RESUME_PERSISTENCE_ENABLED=true
RESUME_CACHE_TTL=10m
JOB_PERSISTENCE_ENABLED=true
JOB_CACHE_TTL=10m
MATCH_PERSISTENCE_ENABLED=true
MATCH_CACHE_TTL=10m
DELIVERY_PERSISTENCE_ENABLED=true
DELIVERY_CACHE_TTL=10m
DELIVERY_EVENTS_ROCKETMQ_ENABLED=true
DELIVERY_EVENTS_TOPIC=delivery-events

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
| `MINIO_ROOT_USER` | VM3 MinIO 管理账号，同时作为 VM2 `resume-service` 写入凭据 |
| `MINIO_ROOT_PASSWORD` | VM3 MinIO 管理密码，不要提交真实值 |
| `MINIO_BUCKET` | 简历对象存储 bucket，默认 `resumes` |
| `DASHSCOPE_API_KEY` | 阿里云百炼 API Key；为空时 AI 服务进入 mock 演示模式 |
| `DASHSCOPE_MODEL` | 默认 `qwen-plus` |
| `DASHSCOPE_BASE_URL` | 默认 `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `AI_SCREENING_PERSISTENCE_ENABLED` | AI 候选人初筛历史是否写入 MySQL，v0.9 建议为 `true` |
| `AI_SCREENING_CACHE_TTL` | AI 初筛历史 Redis 缓存 TTL |
| `RESUME_PERSISTENCE_ENABLED` | 简历摘要、诊断结果和抽取正文是否写入 VM3 MySQL，v1.9 建议为 `true` |
| `RESUME_CACHE_TTL` | 简历详情 Redis 缓存 TTL |
| `JOB_PERSISTENCE_ENABLED` | 岗位发布和岗位 AI 分析结果是否写入 VM3 MySQL，v1.7 建议为 `true` |
| `JOB_CACHE_TTL` | 岗位列表 Redis 缓存 TTL |
| `MATCH_PERSISTENCE_ENABLED` | 简历和岗位匹配结果是否写入 VM3 MySQL，v1.8 建议为 `true` |
| `MATCH_CACHE_TTL` | 学生匹配结果和岗位候选人匹配结果 Redis 缓存 TTL |
| `DELIVERY_PERSISTENCE_ENABLED` | 投递记录是否写入 VM3 MySQL，v1.6 建议为 `true` |
| `DELIVERY_CACHE_TTL` | 企业投递列表 Redis 缓存 TTL |
| `DELIVERY_EVENTS_ROCKETMQ_ENABLED` | 投递事件是否发布到 RocketMQ，三机部署默认 `true` |
| `DELIVERY_EVENTS_TOPIC` | 投递事件 topic，默认 `delivery-events` |
| `FRONTEND_PORT` | VM1 前端暴露端口 |
| `GATEWAY_PORT` | VM1 Gateway 暴露端口 |

服务内实际使用的关键环境变量：

- VM1 `gateway-service`：`AUTH_SERVICE_URI=http://${VM2_HOST}:8101`、`USER_SERVICE_URI=http://${VM2_HOST}:8102`、`RESUME_SERVICE_URI=http://${VM2_HOST}:8103`、`JOB_SERVICE_URI=http://${VM2_HOST}:8104`、`MATCH_SERVICE_URI=http://${VM2_HOST}:8105`、`DELIVERY_SERVICE_URI=http://${VM2_HOST}:8107`、`AI_SERVICE_URI=http://${VM3_HOST}:8106`。
- VM2 业务服务：`NACOS_ENABLED=true`、`NACOS_SERVER_ADDR=${VM1_HOST}:8848`；`resume-service` 额外使用 `AI_SERVICE_URI=http://${VM3_HOST}:8106`、`RESUME_OBJECT_STORAGE_ENABLED=true`、`MINIO_ENDPOINT=http://${VM3_HOST}:9000`、`MINIO_BUCKET=${MINIO_BUCKET}`、`RESUME_PERSISTENCE_ENABLED=true`、`SPRING_DATASOURCE_URL=jdbc:mysql://${VM3_HOST}:3306/${MYSQL_DATABASE}`、`SPRING_DATA_REDIS_HOST=${VM3_HOST}`；`job-service` 额外使用 `AI_SERVICE_URI=http://${VM3_HOST}:8106`、`JOB_PERSISTENCE_ENABLED=true`、`SPRING_DATASOURCE_URL=jdbc:mysql://${VM3_HOST}:3306/${MYSQL_DATABASE}`、`SPRING_DATA_REDIS_HOST=${VM3_HOST}`；`match-service` 额外使用 `MATCH_PERSISTENCE_ENABLED=true`、`SPRING_DATASOURCE_URL=jdbc:mysql://${VM3_HOST}:3306/${MYSQL_DATABASE}`、`SPRING_DATA_REDIS_HOST=${VM3_HOST}`；`delivery-service` 额外使用 `DELIVERY_PERSISTENCE_ENABLED=true`、`SPRING_DATASOURCE_URL=jdbc:mysql://${VM3_HOST}:3306/${MYSQL_DATABASE}`、`SPRING_DATA_REDIS_HOST=${VM3_HOST}`、`DELIVERY_EVENTS_ROCKETMQ_ENABLED=true`、`ROCKETMQ_NAME_SERVER=${VM3_HOST}:9876`、`DELIVERY_EVENTS_TOPIC=${DELIVERY_EVENTS_TOPIC}`。
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

### 一键健康检查

v1.0 新增跨三台 VM 的健康检查脚本。脚本只读取 `deploy/three-vm.env` 中的地址和端口，不输出 `DASHSCOPE_API_KEY`、数据库密码等敏感值。

Windows PowerShell 调用者：

```powershell
.\scripts\check-three-vm-health.ps1 -EnvFile .\deploy\three-vm.env -TimeoutSeconds 5
```

Linux bash 调用者：

```bash
bash scripts/check-three-vm-health.sh --env-file deploy/three-vm.env --timeout 5
```

检查范围：

- VM1：前端 `/`、前端 `/api/ai/status` 代理、Gateway `/actuator/health`、Gateway `/api/ai/status`、Nacos `/nacos/`、Nacos gRPC `9848`。
- VM2：`auth-service`、`user-service`、`resume-service`、`job-service`、`match-service`、`delivery-service` 的 `/actuator/health`。
- VM3：`ai-service` `/actuator/health` 和 `/api/ai/status`，MySQL `3306`、Redis `6379`、MinIO `/minio/health/ready` 和 Console `9001`、RocketMQ `9876`、`10911`、`10909`。

脚本返回非零 exit code 表示至少一个检查失败，适合部署后验收或后续接入 CI。

### 手工接口验证

在任意能访问三台 VM 的机器上执行：

```bash
curl -sS -X POST "http://<VM1_IP>:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"student","password":"123456"}'

curl -sS "http://<VM1_IP>:8080/api/students/profile"
curl -sS "http://<VM1_IP>:8080/api/jobs"
curl -sS "http://<VM1_IP>:8080/api/ai/status"
curl -sS "http://<VM1_IP>/api/ai/status"
curl -sS "http://<VM1_IP>:8080/api/deliveries/events"
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

验证投递记录持久化：

```bash
curl -sS -X POST "http://<VM1_IP>:8080/api/deliveries" \
  -H "Content-Type: application/json" \
  -d '{"studentId":"S001","resumeId":"R-PERSIST-001","jobId":"J001","resumeSourceFormat":"PDF","resumeParseStatus":"TEXT_EXTRACTED","resumeParsedTextLength":256}'

curl -sS "http://<VM1_IP>:8080/api/deliveries/company?companyId=C001"
```

重启 VM2 的 `delivery-service` 后再次查询，投递记录仍应存在：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml restart delivery-service
curl -sS "http://<VM1_IP>:8080/api/deliveries/company?companyId=C001"
```

验证简历摘要和抽取正文持久化：

```bash
curl -sS -X POST "http://<VM1_IP>:8080/api/resumes/upload" \
  -F "file=@./真实简历.docx"

curl -sS "http://<VM1_IP>:8080/api/resumes/<RESUME_ID>"
curl -sS -X POST "http://<VM1_IP>:8080/api/resumes/<RESUME_ID>/analyze"
```

重启 VM2 的 `resume-service` 后再次查询和诊断，简历摘要和已抽取正文仍应存在：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml restart resume-service
curl -sS "http://<VM1_IP>:8080/api/resumes/<RESUME_ID>"
curl -sS -X POST "http://<VM1_IP>:8080/api/resumes/<RESUME_ID>/analyze"
```

验证岗位记录持久化：

```bash
curl -sS -X POST "http://<VM1_IP>:8080/api/jobs" \
  -H "Content-Type: application/json" \
  -d '{"companyId":"C001","title":"分布式后端实习生","city":"杭州","salaryRange":"200-280/天","requiredSkills":["Java","Spring Cloud Alibaba","Redis"],"description":"参与校园招聘平台微服务接口和缓存治理"}'

curl -sS "http://<VM1_IP>:8080/api/jobs"
```

重启 VM2 的 `job-service` 后再次查询，岗位记录仍应存在：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml restart job-service
curl -sS "http://<VM1_IP>:8080/api/jobs"
```

验证匹配结果持久化：

```bash
curl -sS -X POST "http://<VM1_IP>:8080/api/matches/resume-job" \
  -H "Content-Type: application/json" \
  -d '{"studentId":"S001","resumeId":"R001","jobId":"J001"}'

curl -sS "http://<VM1_IP>:8080/api/matches/student/S001"
curl -sS "http://<VM1_IP>:8080/api/matches/job/J001"
```

重启 VM2 的 `match-service` 后再次查询，匹配结果仍应存在：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml restart match-service
curl -sS "http://<VM1_IP>:8080/api/matches/student/S001"
```

演示账号：

- `student / 123456`
- `company / 123456`
- `admin / 123456`

三机整体健康检查：

```powershell
.\scripts\check-three-vm-health.ps1 -EnvFile .\deploy\three-vm.env -TimeoutSeconds 5
.\scripts\check-api-smoke.ps1 -BaseUrl http://<VM1_IP>:8080
```

```bash
bash scripts/check-three-vm-health.sh --env-file deploy/three-vm.env --timeout 5
bash scripts/check-api-smoke.sh --base-url http://<VM1_IP>:8080
```

## 基础监控与日志

v1.0 先建立轻量运维基线，暂不强制安装 Prometheus、Grafana 或集中日志平台。

容器状态：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml ps
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml ps
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml ps
```

服务日志：

```bash
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml logs --tail 100 gateway-service frontend nacos
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml logs --tail 100 auth-service resume-service job-service delivery-service
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml logs --tail 100 ai-service mysql redis minio rocketmq-namesrv rocketmq-broker
```

建议排障顺序：

1. 先运行 `scripts/check-three-vm-health.ps1` 或 `scripts/check-three-vm-health.sh` 定位失败 VM 和端点。
2. 再在对应 VM 执行 `docker compose ps` 确认容器是否运行。
3. 最后使用 `docker compose logs --tail 100 <service>` 查看服务日志。
4. 如果 Gateway 或前端代理失败，优先检查 VM1 到 VM2/VM3 的网络、防火墙和服务端口。

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

**投递记录没有持久化或企业投递列表缓存异常**

检查 VM2 `delivery-service` 到 VM3 MySQL/Redis 的配置和网络：

```bash
set -a
. ./deploy/three-vm.env
set +a
docker exec recruit-vm2-delivery-service printenv | grep DELIVERY
docker exec recruit-vm2-delivery-service printenv | grep SPRING_DATASOURCE_URL
docker exec recruit-vm3-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "USE ai_campus_recruit; SHOW TABLES LIKE 'delivery_record';"
docker exec recruit-vm3-redis redis-cli KEYS 'delivery:records:*'
nc -zv <VM3_IP> 3306
nc -zv <VM3_IP> 6379
```

**简历摘要没有持久化或简历详情缓存异常**

检查 VM2 `resume-service` 到 VM3 MySQL/Redis 的配置和网络：

```bash
set -a
. ./deploy/three-vm.env
set +a
docker exec recruit-vm2-resume-service printenv | grep RESUME
docker exec recruit-vm2-resume-service printenv | grep SPRING_DATASOURCE_URL
docker exec recruit-vm3-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "USE ai_campus_recruit; SHOW TABLES LIKE 'resume_summary_record';"
docker exec recruit-vm3-redis redis-cli KEYS 'resume:summaries:*'
nc -zv <VM3_IP> 3306
nc -zv <VM3_IP> 6379
```

**岗位记录没有持久化或岗位列表缓存异常**

检查 VM2 `job-service` 到 VM3 MySQL/Redis 的配置和网络：

```bash
set -a
. ./deploy/three-vm.env
set +a
docker exec recruit-vm2-job-service printenv | grep JOB
docker exec recruit-vm2-job-service printenv | grep SPRING_DATASOURCE_URL
docker exec recruit-vm3-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "USE ai_campus_recruit; SHOW TABLES LIKE 'job_record';"
docker exec recruit-vm3-redis redis-cli KEYS 'job:records:*'
nc -zv <VM3_IP> 3306
nc -zv <VM3_IP> 6379
```

**匹配结果没有持久化或匹配查询缓存异常**

检查 VM2 `match-service` 到 VM3 MySQL/Redis 的配置和网络：

```bash
set -a
. ./deploy/three-vm.env
set +a
docker exec recruit-vm2-match-service printenv | grep MATCH
docker exec recruit-vm2-match-service printenv | grep SPRING_DATASOURCE_URL
docker exec recruit-vm3-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "USE ai_campus_recruit; SHOW TABLES LIKE 'match_result_record';"
docker exec recruit-vm3-redis redis-cli KEYS 'match:results:*'
nc -zv <VM3_IP> 3306
nc -zv <VM3_IP> 6379
```

**Docker 拉取镜像慢或失败**

确认 Docker daemon 已按 `docs/deploy/docker-mirror.md` 配置国内镜像源并重启 Docker。当前镜像源优先使用 `docker.m.daocloud.io`；离线环境可在能联网机器上 `docker pull` 后通过 `docker save` / `docker load` 迁移。

**端口被占用**

VM1 可通过 `FRONTEND_PORT`、`GATEWAY_PORT` 改前端和 Gateway 暴露端口。后端服务端口来自各服务 `application.yml`，改动会影响 Gateway 路由和文档，不建议在 v0.9 部署时临时调整。

**简历上传返回 `storageStatus=FAILED`**

`resume-service` 会降级继续返回简历摘要，但文件没有写入 MinIO。检查 VM2 到 VM3 MinIO 的网络和凭据：

```bash
docker exec recruit-vm2-resume-service printenv | grep MINIO
curl -f http://<VM3_IP>:9000/minio/health/ready
docker logs recruit-vm2-resume-service --tail 100
```

**投递事件没有发布到 RocketMQ**

`delivery-service` 会降级记录事件状态，主投递流程不失败。先查询最近事件：

```bash
curl -sS http://<VM1_IP>:8080/api/deliveries/events
```

如果 `publishStatus=FAILED`，检查 RocketMQ 地址和 VM3 端口：

```bash
docker exec recruit-vm2-delivery-service printenv | grep ROCKETMQ
docker exec recruit-vm2-delivery-service printenv | grep DELIVERY_EVENTS
nc -zv <VM3_IP> 9876
docker logs recruit-vm3-rocketmq-namesrv --tail 100
docker logs recruit-vm3-rocketmq-broker --tail 100
```

如果 broker 禁止自动创建 topic，需要在 RocketMQ 中预先创建 `delivery-events` topic。
