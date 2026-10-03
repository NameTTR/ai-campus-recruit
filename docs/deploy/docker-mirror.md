# Docker 国内源与启动故障处理

本文供本项目的开发者和部署维护者配置中国可访问的依赖源，并处理已复现的 Windows Docker Desktop 启动故障。
所有运行目录处理都先备份；不使用恢复出厂设置，不删除镜像、容器或数据卷。

## 中国源配置

项目 Compose 与 Dockerfile 已显式使用 `docker.m.daocloud.io` 的镜像路径。
后端 Maven 使用 [阿里云设置示例](../../backend/settings.xml.example)，前端构建使用 `https://registry.npmmirror.com`。

Windows Docker Desktop 可在 Settings > Docker Engine 中合并以下配置，并通过 Apply & restart 生效。
已有配置中的其他键需要保留:

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ]
}
```

Ubuntu 可将同一配置合并到 `/etc/docker/daemon.json`，再重启 Docker:

```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
```

在仓库根目录构建和启动，Maven 下载插件及依赖继续使用阿里云设置:

```powershell
mvn -B -f backend/pom.xml -s backend/settings.xml.example package -DskipTests
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
```

镜像源配置解决下载路径问题；以下 Windows 故障发生在引擎启动阶段，需要单独处理。

## Windows Inference manager 启动失败

### 适用范围

本机于 2026-10-03 使用 Docker Desktop `4.54.0` 复现了以下错误:

```text
starting services: initializing Inference manager:
...Docker\run\dockerInference...
The file cannot be accessed by the system.
```

相同症状的公开参考见 [Docker Desktop Windows Inference manager 启动失败](https://github.com/docker/desktop-feedback/issues/531)。该链接供比对现象，本次判断和修复验证以本机日志、`docker version` 和容器健康结果为依据。
只有日志匹配该启动阶段和运行目录错误时，才采用下面的处理。
WSL 未安装、虚拟化未开启、下载失败或业务容器退出应按各自日志排查，不据此执行运行目录修复。

下面记录的是本机有效的可逆处理，不作为 Docker Desktop 所有启动崩溃的通用修复: 备份普通运行目录、备份设置、关闭可选 Docker AI，再让 Docker Desktop 重建运行目录。
修复后 Docker Server 为 `29.1.2`，本项目 15 个容器全部运行且健康。
现有镜像、容器和数据卷保留，没有对其他项目执行停止或清理操作。

### 备份与修复

1. 从 Docker Desktop 托盘菜单选择 Quit，确认应用已退出。不要在它仍运行时移动目录。
2. 检查以下位置仅为普通目录: `%LOCALAPPDATA%\Docker\run`、`%LOCALAPPDATA%\docker-secrets-engine`。若任一路径为符号链接或 reparse point，停止自动处理并检查目标，不能递归删除或移动其指向的位置。
3. 将 `settings-store.json` 复制为带时间戳的备份，并将已有普通运行目录重命名为同目录下的备份。
4. 在设置文件中仅将 `EnableDockerAI` 设为 `false`，保留其他配置。
5. 使用开始菜单或下面的命令重新启动 Docker Desktop，然后执行健康验证。

以下 PowerShell 示例在当前登录用户下执行。它先检查全部目标，再备份和修改。
脚本没有删除命令；生成的备份路径应保留在本机运维记录中:

```powershell
$taskDockerProcesses = Get-Process -Name 'Docker Desktop', 'com.docker.backend' -ErrorAction SilentlyContinue
if ($taskDockerProcesses) {
    throw '请先通过托盘菜单退出 Docker Desktop，确认后台进程也已退出。'
}

$taskRepairStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$taskLocalRoot = [System.IO.Path]::GetFullPath($env:LOCALAPPDATA)
$taskSettingsPath = Join-Path $env:APPDATA 'Docker\settings-store.json'
$taskSettingsBackup = Join-Path $env:APPDATA "Docker\settings-store.before-repair-$taskRepairStamp.json"
$taskRuntimePaths = @(
    (Join-Path $taskLocalRoot 'Docker\run'),
    (Join-Path $taskLocalRoot 'docker-secrets-engine')
)
$taskRuntimeBackups = @()

$taskSettings = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskSettingsPath | ConvertFrom-Json
foreach ($taskRuntimePath in $taskRuntimePaths) {
    if (-not (Test-Path -LiteralPath $taskRuntimePath)) {
        continue
    }
    $taskDirectory = Get-Item -Force -LiteralPath $taskRuntimePath
    $taskResolvedPath = [System.IO.Path]::GetFullPath($taskDirectory.FullName)
    $taskBackupPath = "$taskResolvedPath.before-repair-$taskRepairStamp"
    if (-not $taskResolvedPath.StartsWith($taskLocalRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "路径超出当前用户的 LocalAppData，停止处理: $taskResolvedPath"
    }
    if (-not $taskDirectory.PSIsContainer -or ($taskDirectory.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw "不是普通目录，停止自动处理: $taskResolvedPath"
    }
    if (Test-Path -LiteralPath $taskBackupPath) {
        throw "备份路径已存在，停止处理: $taskBackupPath"
    }
    $taskRuntimeBackups += [pscustomobject]@{
        Source = $taskResolvedPath
        Backup = $taskBackupPath
    }
}
if (Test-Path -LiteralPath $taskSettingsBackup) {
    throw "设置备份已存在，停止处理: $taskSettingsBackup"
}

Copy-Item -LiteralPath $taskSettingsPath -Destination $taskSettingsBackup -ErrorAction Stop
foreach ($taskRuntimeBackup in $taskRuntimeBackups) {
    Move-Item -LiteralPath $taskRuntimeBackup.Source -Destination $taskRuntimeBackup.Backup -ErrorAction Stop
}
$taskSettings | Add-Member -NotePropertyName EnableDockerAI -NotePropertyValue $false -Force
$taskSettingsJson = $taskSettings | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText($taskSettingsPath, $taskSettingsJson, [System.Text.UTF8Encoding]::new($false))
[pscustomobject]@{
    SettingsBackup = $taskSettingsBackup
    RuntimeBackups = $taskRuntimeBackups
}

$taskDockerApp = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
Start-Process -FilePath $taskDockerApp -WindowStyle Hidden
```

`EnableDockerAI=false` 关闭 Docker Desktop 的可选 Docker AI。
本项目通过环境变量配置的 DashScope 功能不依赖此开关，本轮真实学习计划、面试和 RAG 调用已验证。

### 验证与恢复

启动后确认 `docker version` 同时有 Client 和 Server，避免只看到了客户端版本。
然后检查 WSL、项目健康检查及数据读取:

```powershell
docker version
wsl --list --verbose
docker compose up -d --wait --wait-timeout 600
docker compose ps
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1/health'
Invoke-RestMethod 'http://127.0.0.1:18080/actuator/health'
```

本机前端端口为 `80`，Gateway 为 `18080`；其他环境以 `.env` 中的实际端口为准。
检查全部 15 个项目容器为 running/healthy，并通过已有账号读取持久化数据。
本轮还执行了核心流程及八个后端/Gateway 服务的重启恢复验收，详见 [优化验收记录](../../reports/optimization/2026-10-03-verification.md)。

本机备份清单保存在 `logs/validation/docker-repair-backups.json`，实际备份为:

- `C:\Users\G5080\AppData\Local\Docker\run.before-repair-20261003-114124`
- `C:\Users\G5080\AppData\Local\docker-secrets-engine.before-repair-20261003-114124`
- `C:\Users\G5080\AppData\Roaming\Docker\settings-store.before-repair-20261003-114124.json`

需要撤回修复时，先再次退出 Docker Desktop。确认当前运行目录和备份仍为上述用户目录内的普通目录，
将新生成的运行目录重命名为另一个带时间戳的保留目录，再将原备份移回原位置，并恢复设置备份。
最后重新启动并验证。恢复原 `EnableDockerAI` 设置可能再次触发原故障，应保留当前可用配置副本以便再次恢复。
整个过程不删除运行目录，也不触碰 Docker 镜像、数据卷或 WSL 数据磁盘。

## Nginx 上传与 AI 响应等待

前端 Nginx 配置见 [default.conf.template](../../frontend/nginx/default.conf.template)。
当前请求体上限为 `12m`，比简历后端的 10 MB 文件限制略大，为 multipart 表单边界预留空间。
知识库文件默认限制同样为 10 MB，其请求体默认上限为 12 MB。
后端仍负责校验文件大小和格式，代理上限不会扩大 API 允许的文件范围。

API 代理配置如下，读写超时用于真实 AI 请求，连接超时用于及时发现上游不可达:

```nginx
client_max_body_size 12m;

location /api/ {
    proxy_connect_timeout 5s;
    proxy_read_timeout 180s;
    proxy_send_timeout 180s;
}
```

修改模板后重新构建前端镜像，并检查容器实际生效的配置:

```powershell
docker compose up -d --build --wait --wait-timeout 600 frontend
docker compose exec -T frontend nginx -t
docker compose exec -T frontend nginx -T
```

本轮 2 MB 无效上传 fixture 已通过 Nginx 到达后端 API 校验，且没有生成简历记录。
这验证了上传请求不再被 Nginx 默认 1 MB 限制提前拦截；合法文件导入结果由核心流程单独验证。

## Core deepening evaluation image and migration notes

评估脚本使用 Node.js 内置 `fetch`、Java 17 和仓库已有构建产物，不需要新增 Docker 服务。执行前先用中国可访问 Maven 源完成后端构建，再通过 Gateway 运行 RAG API 评估。Compose 继续使用项目已有镜像源，不改变基础设施拓扑。

语义 RAG 升级只新增 MySQL 元数据表，并使用独立的 1024 维索引版本。不要把旧 collection 直接改维度；先执行索引重建，检查管理员状态，再让新版本承接查询。若排序或向量接口失败，服务保留关键词路径，便于在没有 Milvus 或临时没有模型配额时完成基础验证。

## 简历导出镜像

`resume-service` 使用独立的 `backend/resume-service/Dockerfile` 构建，继续采用 DaoCloud Java/Maven 镜像和阿里云 Ubuntu 软件源，并安装 LibreOffice Writer 与 Noto CJK 中文字体。生产环境请配置 `MINIO_PUBLIC_ENDPOINT`，使浏览器取得可访问的短期下载地址，而不是 Docker 网络内部的 MinIO 地址。


### 简历工作区迁移与运行

执行前保留 MySQL 与 MinIO 的现有 Docker volumes。此次迁移只创建
`resume_workspace_profile`、`resume_workspace_draft`、`resume_workspace_draft_revision`、
`resume_workspace_export` 和 `resume_workspace_photo`，由简历服务启动执行 `schema.sql`。
原 `resume_summary_record`、上传原件及诊断历史保持。

```powershell
docker compose build resume-service ai-service match-service frontend
docker compose up -d --wait --wait-timeout 600 resume-service ai-service match-service frontend
```

本机 Gateway 若配置为 18080，则 API 地址为 `http://localhost:18080`；
前端默认 `http://localhost`。不要使用其他项目占用的 8080。
简历服务内部端口 8103，健康检查 `/actuator/health`，OpenAPI `/v3/api-docs`。

简历运行镜像独立安装 LibreOffice 与 Noto CJK，不扩大其他服务镜像。
每次转换独立工作目录和 LibreOffice 运行配置，正文至少 10pt。
`RESUME_RENDER_TIMEOUT_SECONDS=60` 和 `RESUME_RENDER_CONCURRENCY=2` 可配置。
`MINIO_PUBLIC_ENDPOINT=http://localhost:9000` 用于本机浏览器下载；
远程部署应改为学生浏览器能访问的域名或主机，签名时必须使用同一外部 Host。
内网写入继续用 `MINIO_ENDPOINT=http://minio:9000`。

`AI_RESUME_DRAFT_AI_ENABLED=true` 默认开启内容组织，AI 密钥沿用环境变量。
关闭后仍可按事实整理、编辑、诊断规则、比较和导出。成功输入复用已有结果；
模型组织的建议必须校验原文和来源，未确认建议不进入导出。

重启简历服务时，持久化 `QUEUED/RUNNING` 导出任务重新排队，
成功任务从 MinIO 重新签发下载地址。修改模板文件时应同步更新模板版本；
已有草稿使用版本快照，版本失配应明确提示重新选择模板。

用户原始素材 `jianli/` 由 `.gitignore` 和 `.dockerignore` 排除。
全部素材清单位于 `docs/resume-template-catalog.json`，只发布八套清洁适配副本及预览。
不要将原始个人示例、照片、推广资料和约 1.3 GB 素材复制进服务镜像。
