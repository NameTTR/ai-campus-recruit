# 简历工作区修复与验收

日期: 2026-10-04。本文记录简历页面预览、下载排版和界面简化修复，供开发及 Docker 部署验收使用。

## 修复内容

- 页面改为填写资料、岗位与模板、编辑与下载三个步骤，资料按分区填写。
- 生成后自动显示最终 PDF，使用受鉴权的同源文件接口及 PDF.js 画布。
- 编辑后禁用旧文件下载，保存新修订并更新预览后再下载。
- Word 与 PDF 读取同一导出快照，支持中文文件名及文件摘要校验。
- T05 浮动背景改为表格背景，表格按 A4 可用宽度排版，空区块不进入文件。
- T06 保留受控双栏，T07 使用自然分页，作品链接采用可读的深色。
- 渲染版本变化时重新导出，旧草稿正文、历史修订及原文件保留。

## 验收结果

- 后端完整 `clean verify` 成功: 390 项测试，无失败及错误，1 项可选缩略图生成测试跳过。
- 最终渲染器专项测试包含实际 PDF 转换，另运行过全部 10 项并生成正式缩略图。
- 前端 134 项单元测试及生产构建成功。
- 真实有界面浏览器 79 项检查通过，产生 21 张截图，未出现 JavaScript 或 CSP 错误。
- 浏览器完整执行资料确认、真实岗位选择、生成、编辑、下载、刷新恢复及导入确认。
- 390px 和 360px 手机页面无横向溢出，PDF 预览默认适应整页；放大后的左右、上下边缘均可通过预览内部滚动到达。
- 预览区使用固定的响应式高度和独立滚动，工具栏始终可见；支持适应整页、适应宽度、展开查看、Escape 收起，缩放不会重复请求 PDF。
- 双页简历可切换到第二页并看到末尾内容；窗口尺寸变化、展开查看和手机窄屏会保留当前页。
- DOCX 导入候选在学生保存主资料前不会写入已确认资料。
- Java、前端、运营的真实 DashScope 内容组织、规则岗位比较及 Docker 重启恢复通过。

Docker 导出覆盖 8 套模板的短内容、长内容、有照片及无照片共 32 组。
单页模板短内容正常导出，超出页数时返回 `NEEDS_EDIT`；T07/T08 长内容正常分为两页。
21 份成功 PDF 含重启恢复文件，均通过中文提取、页边界、文字交叠、照片与文字相交检查。
T05 白字始终位于深色背景内。

最终 `resume-render-v4` 另通过真实浏览器下载全部 8 套 Word/PDF，检查中文可提取、Word 可编辑、
无裁切或交叠、T06/T07 作品链接使用修正后的深色。验收材料保存在本机:

- `output/playwright/resume-fix-export-geometry.json`
- `output/playwright/resume-fix-export-contact.png`
- `output/playwright/resume-fix-live-downloads.json`
- `output/playwright/resume-workspace/2026-10-04T11-47-54-439Z/report.json`
- `output/playwright/resume-workspace/2026-10-04T12-29-08-645Z/report.json`

## 复验命令

```powershell
# 后端单元及集成验证
mvn.cmd -B -f backend/pom.xml -s backend/settings.xml.example clean verify

# 前端单元测试及生产构建
npm.cmd --prefix frontend run test:unit
npm.cmd --prefix frontend run build

# Docker 导出及重启恢复
python scripts/resume-export-acceptance.py --restart
python scripts/resume-export-acceptance.py --verify-existing

# 真实有界面浏览器及全部模板
$env:E2E_HEADED='1'
$env:E2E_ALL_TEMPLATES='1'
npm.cmd --prefix frontend run test:e2e:resume
```

## 部署说明

```powershell
docker compose build --progress plain resume-service frontend
docker compose up -d --no-deps resume-service frontend
```

简历服务启动时增量迁移导出表，保留旧修订和文件。渲染器版本改变时，同一草稿修订产生新的导出。
前端和简历服务需要同时更新，因为 PDF.js 通过新增文件接口读取资料。
Nginx 将 `.mjs` worker 文件作为 JavaScript 返回，并允许本地 Blob 读取；
预览不依赖外部文件嵌入或浏览器 PDF 插件。

Maven、npm、基础镜像、LibreOffice 和字体安装继续使用既有中国镜像。
运行页面: `http://localhost/student/resume`。

`output/playwright/` 和 `output/pdf/` 内的图片、PDF 和浏览器材料属于验收输出，由 Git 忽略。
`frontend/public/resume-templates/` 内的八张正式模板缩略图作为应用资源提交。
