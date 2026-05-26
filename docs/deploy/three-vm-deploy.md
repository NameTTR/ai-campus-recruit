# 三虚拟机部署

## VM1

- 前端 Nginx
- `gateway-service`
- Nacos
- Sentinel Dashboard

## VM2

- `auth-service`
- `user-service`
- `resume-service`
- `job-service`
- `match-service`
- `delivery-service`

## VM3

- MySQL
- Redis
- RocketMQ
- MinIO
- `ai-service`
- Prometheus + Grafana

## Ubuntu 国内源

Ubuntu 18.04 可使用清华源或阿里云源。替换前备份：

```bash
sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak
sudo apt update
```

## 部署步骤

1. 三台机器安装 Docker 和 Docker Compose。
2. 配置 Docker 国内镜像源。
3. VM3 启动数据库、中间件和 AI 服务。
4. VM2 启动业务服务，并配置 Nacos 地址指向 VM1。
5. VM1 启动 Nacos、Gateway 和前端。
6. 浏览器访问 `http://VM1_IP`。

