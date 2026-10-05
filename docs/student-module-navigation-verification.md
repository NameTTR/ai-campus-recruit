# 学生模块分层导航验收

日期：2026-10-05 至 2026-10-06。环境：Windows、真实 Docker 服务、Chromium。

## 页面关系

保留工作台的五个主入口及配色。岗位模块沿用已完成的分层导航，本轮把其余四个模块拆为独立子页面，每页只展示当前操作。

| 模块 | 二级导航 | 下一级页面 |
| --- | --- | --- |
| 简历 | 我的简历、主资料、上传记录 | 岗位与模板、内容编辑、诊断建议、修订历史、上传原件及历史诊断 |
| 学习路径 | 今日、完整计划、复盘调整、历史 | 创建计划、单项任务详情及成果提交 |
| 模拟面试 | 开始、答题、历史 | 独立面试报告、逐题评价和原回答 |
| 知识库 | 查询、资料原文、最近查询 | AI 回答、单条引用原文 |

简历预览不再与资料表单和诊断同时展示，移除重复模板摘要及页面外框。完整学习计划每页 12 项，可按周筛选；首页只保留有限数量的今日待办、逾期任务与待成果任务。成果表单移入任务详情。面试历史使用普通列表，参考要点和评价按需展开；已完成面试不显示保存、结束或重复评价按钮，原回答区域缩为 6 行并保持可滚动。知识查询只显示简短检索结果，长回答与原文分别进入对应页面。

## 路由与上下文

- 简历：`/student/resume`、`/profile`、`/templates`、`/edit`、`/diagnosis`、`/versions`、`/history`、`/original/{resumeId}`、`/original/{resumeId}/diagnosis`。
- 学习：`/student/plan`、`/tasks`、`/tasks/{taskId}`、`/create`、`/review`、`/history`。
- 面试：`/student/interview`、`/practice`、`/report`、`/history`。
- 知识：`/student/knowledge`、`/answer`、`/sources`、`/history`。

子路径均属于表中模块根路径。草稿、计划、面试会话及知识查询上下文分别使用 `draftId`、`planId`、`sessionId`、`q/ai/source`；无效 ID 明确提示，不能静默切换到其他记录。旧 `/student/history` 和 `/student/interview?tab=history` 保留兼容跳转。

上传成功后进入该原件页面。岗位匹配中的“生成学习计划”直接进入创建页并携带 `matchId`。请求期间已切换页面或修改输入时，迟到响应不能改变当前页面或覆盖新输入。

未保存的主资料、简历内容、面试回答、任务备注与成果说明按用户和记录隔离，在当前浏览器标签页暂存；暂存不会替代服务端保存。历史计划保持只读。PDF 普通预览保留滚动链，展开模式可以关闭并恢复正文滚动。

知识结果暂存最多 10 分钟，子页切换及刷新恢复前读取 `GET /api/ai/knowledge/revision`。版本与结果中的 `permissionVersion` 不一致时清除旧结果并提示重新检索。版本读取不调用向量、重排或回答模型；删除资料、变更角色或内容后旧引用不能恢复。该新增只读接口已同步 OpenAPI 与 `docs/api.md`。

## 验证命令

```powershell
cd D:\project\bishe\frontend
npm.cmd run test:unit
npm.cmd run build
$env:E2E_BASE_URL = 'http://localhost'
npm.cmd run test:e2e
npm.cmd run test:e2e:resume

cd D:\project\bishe\backend
mvn.cmd -s settings.xml.example -pl ai-service -am '-Dtest=AiControllerTest,KnowledgeIdentityIntegrationTest,KnowledgeBaseServiceTest,KnowledgeSemanticRetrievalTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -s settings.xml.example -pl gateway-service -am '-Dtest=JwtGatewayAuthFilterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

cd D:\project\bishe
docker compose build frontend ai-service
docker compose up -d --no-deps frontend ai-service
docker compose ps
git diff --check
```

继续使用 DaoCloud Docker 镜像、阿里云 Maven 镜像和 npmmirror，没有新增基础设施。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 前端单元测试 | 5 个文件、141 项通过 |
| 生产构建 | 本机与 Docker 类型检查、构建通过；保留既有文件体积提示 |
| 知识接口与服务测试 | 71 项通过，包含版本稳定、新增、权限变化和删除 |
| 网关权限测试 | 24 项通过，学生可读版本但不可写知识库 |
| 四模块完整浏览器回归 | 通过，包含原企业、管理员和岗位流程 |
| 页面滚动 | 53 组桌面和 320px、390px 手机子页检查通过，无横向溢出；长内容可到底部并返回顶部 |
| 导航边界 | 页面互斥、返回、刷新、空数据、无效草稿/原件/计划/任务/会话、历史只读通过 |
| 输入与失败恢复 | 未保存资料和草稿、面试回答、成果跨页与刷新恢复；空成果、503 保存失败不丢内容 |
| 知识查询边界 | A 请求迟到不覆盖历史 B 或已改输入；引用版本不同清除旧结果，恢复与切页不新增回答生成请求 |
| 简历专项 | 83 项通过，21 张截图，T01–T08 真实导出、Word/PDF 下载、双页及缩放检查通过 |
| 最后面试页面补测 | 1440px、390px、320px 均通过：完成会话只读，无保存/结束/重复评价按钮，报告往返正常；修复旧样式强制撑高后，6 行回答区实测约 154px，无横向溢出，0 浏览器异常 |
| 容器 | 全部 15 个容器运行且健康，更新保留现有数据卷 |

运行报告及截图位于忽略目录，不提交学生资料或导出文件：

- `output/playwright/module-navigation-smoke/student-scroll-results.json`：首次完整回归的 53 组滚动记录。
- `output/playwright/module-navigation-final-20261006/`：最终浏览器回归和知识缓存边界检查。
- `output/playwright/module-navigation-release-20261006/smoke-report.json`：主会话完整回归，`PASSED`、53 组滚动、0 未捕获浏览器异常。
- `output/playwright/modules-interview-final-{1440,390,320}.png`：最后历史面试精简后的单独复测。
- `output/playwright/resume-workspace/2026-10-05T15-53-58-746Z/report.json`：真实简历专项。
- `output/playwright/modules-resume-final-1440.png`：最终简历首屏。

空数据、503 和迟到响应场景使用浏览器临时拦截，其余业务流程连接真实 Docker 服务。测试账户、截图和导出文件只用于本机验收。
