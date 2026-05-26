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

## Demo Accounts

- 学生：`student / 123456`
- 企业：`company / 123456`
- 学校管理员：`admin / 123456`

## AI Configuration

真实调用阿里云百炼时配置：

```env
DASHSCOPE_API_KEY=your_api_key
DASHSCOPE_MODEL=qwen-plus
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

未配置 Key 时，`ai-service` 会返回可演示的 mock 结果。

