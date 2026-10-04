# 前端设计重构与验收

日期: 2026-10-05。本文供前端维护者及 Docker 部署验收使用，记录本轮界面调整和复验方法。

## 设计与页面关系

使用已安装的 `frontend-design` skill，采用工业实用风格。核心视觉标识是左侧带编号的功能导航轨道。
保留绿色品牌色，以灰白工作区、蓝色状态和细线分区组织信息，移除宣传文字、英文眉标及学生端重复大标题。

- 公共框架: 220px 导航和 58px 顶栏。小屏使用抽屉导航，搜索入口使用图标。
- 学生端: 简历直接进入填写资料、岗位与模板、编辑与下载三个步骤，其余四个模块沿用原路由。
- 岗位匹配: 岗位列表、条件筛选、岗位详情和匹配操作保持在同一工作区。
- 学习及面试: 数据摘要使用紧凑行，证据、成果和逐题反馈保持可展开。
- 知识问答: 摘要栏使用中文来源状态，内部来源和分析版本放入来源详情展开项。
- 企业端: 岗位列表与发布页面通过侧栏相连，优先展示实际岗位内容。
- 管理端: 优先展示账号及文档列表，创建、上传、重置密码和索引维护按需展开。
- 登录页: 品牌和登录表单，按钮及 Enter 使用同一提交入口。

业务 API、权限校验和路由保持兼容。本轮没有新增服务或部署依赖。

## 已修复问题

1. 小屏标题和统计卡片占用过多空间: 删除重复标题，将大卡片改为紧凑摘要。
2. 320px 窗口出现横向溢出: 页面最小宽度改为允许收缩，避免滚动条缩窄可用空间后内容溢出。
3. 移动导航初始焦点未进入抽屉: 在可见状态绘制后聚焦当前导航项，关闭后恢复触发按钮焦点。
4. 弹层打开时背景仍可操作: 使用 `inert`、焦点约束及滚动锁定，支持 Escape 和遮罩关闭。
5. 旧浏览器测试依赖已移除标题: 改为检查业务组件和折叠入口，保留历史诊断与资料恢复验证。
6. Node 20 运行浏览器脚本缺少 WebSocket: 自动使用所需运行参数重新启动脚本。

简历 PDF 保留整页适配、宽度适配、独立滚动、缩放及展开预览。
放大后四个边缘均可到达，操作不会重新请求已加载的 PDF。

## 验收结果与范围

- 前端 134 项单元测试及生产构建通过。
- Docker 前端生产镜像已更新，15 个 Compose 容器均为 healthy。
- 有界面浏览器完整 E2E 通过，覆盖学生、企业、管理端以及 1440、1024、390、320px 窗口。
- 本地演示模式 E2E 通过，与 CI 使用的前端验收模式一致。
- 页面布局检查全部通过，未出现浏览器 JavaScript 异常。
- 简历专项 79 项检查通过，覆盖 8 套模板的真实 PDF 画布、生成、编辑、下载、刷新恢复、导入确认和双页预览。
- 专项通过 Word/PDF 浏览器下载、非空画布像素检查、手机预览及 Escape 焦点恢复。

完整 E2E 使用 Docker 服务及已存在的验收记录，覆盖历史诊断、匹配、学习成果和面试反馈。
知识页执行仅检索操作。本轮界面验收不代表重新校准 AI 评分或检索质量指标。
文件内容和导出版式的更详细结果见 [简历工作区验收](resume-workspace-verification.md)。

本机验收材料由 Git 忽略:

- `output/playwright/frontend-design-live/layout-report.json`: 页面布局和浏览器异常记录。
- `output/playwright/frontend-design-live/`: 桌面、手机和管理表单截图。
- `output/playwright/frontend-design-resume/report.json`: 简历专项逐项结果。
- `output/playwright/frontend-design-resume/`: 模板、预览和下载材料。

## 复验与部署

在项目根目录执行，Docker 服务需已启动。浏览器脚本默认寻找本机 Edge 或 Chrome，
也可通过 `E2E_BROWSER` 指定浏览器路径。

```powershell
npm.cmd --prefix frontend run test:unit
npm.cmd --prefix frontend run build

docker compose --progress plain build frontend
docker compose up -d --no-deps frontend
docker compose ps

$env:E2E_BASE_URL='http://127.0.0.1'
$env:E2E_HEADED='1'
$env:E2E_ARTIFACTS_DIR='D:\project\bishe\output\playwright\frontend-design-live'
npm.cmd --prefix frontend run test:e2e

$env:E2E_RESUME_BASE_URL='http://localhost'
$env:E2E_RESUME_API_URL='http://localhost:18080'
$env:E2E_ALL_TEMPLATES='1'
$env:E2E_RESUME_ARTIFACTS_DIR='D:\project\bishe\output\playwright\frontend-design-resume'
npm.cmd --prefix frontend run test:e2e:resume
```

成功运行会记录 `PASSED` 和完成时间，并清除同目录旧失败材料；失败时保留页面截图及文字。
运行地址: <http://localhost>。依赖下载继续沿用现有中国镜像。

## 兼容性与限制

实际验收使用 Windows Chromium 浏览器。布局使用 Grid、Flex、动态视口单位和 `inert`，
适用于当前版本的 Edge、Chrome、Firefox 和 Safari，后三者未逐一完成真实浏览器验收。
PDF 由本地 PDF.js 画布呈现，不依赖浏览器 PDF 插件。
系统开启减少动态效果时禁用导航过渡动画。

生产构建仍存在既有 vendor 文件大于 500KB 的提示；构建成功，后续性能优化可另行处理。
