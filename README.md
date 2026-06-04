# AI Campus Recruit

基于 Spring Cloud Alibaba 与阿里云百炼的 AI 简历诊断与校园招聘匹配平台。

## Modules

- `backend`: Spring Boot 3 / Spring Cloud Alibaba 多模块后端
- `frontend`: Vue 3 三端前端，包含学生端、企业端、学校端
- `docs`: 需求、架构、API、部署和 AI 开发规范
- `docker-compose.yml`: 本地和虚拟机部署编排

## Quick Start

```powershell
cd D:\Study\homework\fenbushixitong\exfinal1
copy .env.example .env
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

访问：

- 前端：`http://localhost:5173`
- 网关：`http://localhost:8080`

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
```

未配置 Key 时，`ai-service` 会返回可演示的 mock 结果。
