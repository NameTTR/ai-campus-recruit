# 岗位匹配分层导航验收

日期：2026-10-05。环境：Windows、真实 Docker 服务、Chromium。

## 页面关系

| 路径 | 展示内容 |
| --- | --- |
| `/student/jobs` | 岗位搜索、城市和技能筛选、分页列表 |
| `/student/jobs/{jobId}` | 单个岗位要求、简历选择和匹配操作 |
| `/student/jobs/{jobId}/match` | 单个岗位的匹配分析和后续学习、面试入口 |
| `/student/jobs/compare` | 同一份简历比较 2–3 个岗位 |
| `/student/jobs/history` | 历史匹配记录，点击进入对应分析 |

主导航仍为“岗位匹配”；二级导航提供岗位列表、岗位比较和匹配记录。岗位详情与分析提供返回列表和岗位层级的导航。原 `/student/jobs` 入口保持有效，原 `/student/jobs?matchId=...` 会转到对应分析页，业务接口不变。

默认列表不展示统计卡片、匹配结果、比较表单和历史记录。列表采用每页 12 个岗位的分页，页面本身滚动。分析依据与比较详情按需展开。

## 验证命令

```powershell
cd D:\project\bishe\frontend
npm.cmd run test:unit
npm.cmd run build

cd D:\project\bishe
docker compose build frontend
docker compose up -d --no-deps frontend

cd D:\project\bishe\frontend
$env:E2E_BASE_URL = 'http://localhost'
npm.cmd run test:e2e
```

## 验收范围

- 列表、详情、分析、比较和历史仅展示当前层级。
- 搜索无结果、筛选、分页、返回和刷新保留当前浏览上下文。
- 从真实岗位完成匹配，并从历史进入相应的分析记录。
- 使用相同简历比较岗位；少于两个或多于三个岗位不能提交。
- 更换简历或岗位后清除旧比较结果；迟到响应不能写入新上下文。
- 无简历、无岗位、岗位已不可用和请求失败均有明确状态与可操作入口。
- 桌面、390px 和 320px 下无横向溢出，正文可真实滚动到底部。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 前端单元测试 | 5 个文件、141 项通过 |
| 类型检查与生产构建 | 本机与 Docker 均通过 |
| 完整 Docker E2E | 通过，包含原 15 组五模块滚轮与触摸滚动回归 |
| 岗位导航边界 | 页面互斥、分页、搜索、返回、刷新、历史分数、无效 ID、空岗位、空简历、503 重试通过 |
| 比较数量边界 | 0、1 个禁用；2、3 个可提交；第 4 个不可选择 |
| 真实岗位比较 | Java、前端、运营岗位；2 个与 3 个结果均正常 |
| 比较上下文 | 更换简历或岗位清除旧结果；选择变化、离开页面后的迟到响应被忽略 |
| 历史与旧链接 | 旧 `matchId` 链接可打开；迟到匹配响应不会覆盖刚打开的历史分析 |
| 五个子页面布局 | 1440×900、390×900、320×900 共 15 组，无横向溢出，长内容展开后能滚到底部 |
| 后续流程 | 从历史分析进入学习、面试时保留简历、岗位和匹配上下文 |
| 浏览器异常 | 岗位专项检查无未捕获异常 |
| 部署状态 | 最终前端镜像已更新，容器健康 |

本轮保持现有布局与配色。默认页面只包含当前层级；证据、条件、建议和结果快照按需展开。历史时间使用短格式，无引用时隐藏空来源行。

截图与本机验证文件保存在忽略目录中：

- `output/playwright/jobs-navigation-before.png`：修改前页面。
- `output/playwright/jobs-navigation-{list,detail,analysis,compare,history}-{1440,390,320}.png`：实际岗位子页截图。
- `output/playwright/jobs-navigation-check.js`：Playwright CLI 岗位专项检查，可在已登录会话运行。
- `frontend/.e2e-artifacts/03a-jobs-list-navigation.png`：列表与分层导航。
- `frontend/.e2e-artifacts/03b-jobs-empty.png`、`03c-jobs-load-recovered.png`、`03d-jobs-no-resume.png`：边界场景。
- `frontend/.e2e-artifacts/student-scroll-results.json`：原 15 组真实滚动结果。

专项检查命令：

```powershell
npx.cmd --yes --package @playwright/cli playwright-cli -s=jobs-nav run-code --filename=output/playwright/jobs-navigation-check.js
git diff --check
```

专项脚本需要已登录学生的 CLI 浏览器会话。测试产生的匹配记录使用规则计算，不调用模型；空数据及故障场景通过浏览器临时响应模拟。
