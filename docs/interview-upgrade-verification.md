# 模拟面试实用化验收

## 实现边界

保留文本面试和现有四级子页：开始、答题、报告、历史。辅导练习即时反馈和重答，模拟面试结束后统一复盘。题目来源包括岗位、本人项目或已确认学习成果、本人能力差距。计时由服务端记录，暂停和刷新不丢状态。行动建议先预览，确认后才创建专项练习或学习调整草稿；简历候选仍由学生确认，不自动提升证据。

所有新增接口详见 [API 文档](api.md#模拟面试来源练习和复盘)。会话的新增结构保存于原持久化快照，旧记录继续按辅导练习和首次回答展示。中国 Maven、npm、Docker 镜像继续沿用，无新增必需基础设施。

## 可重复浏览器验收

脚本使用 Playwright CLI 打开真实 Chromium，默认显示浏览器窗口；连接真实 Docker 前端及网关，创建合成学生账户和资料，避免改写日常使用的账户。模型失败、超时等故障可在专项场景中临时拦截，其余流程必须连接真实服务。脚本执行后写入结果和截图，不提交账户资料或截图。

```powershell
cd D:\project\bishe\frontend
npm.cmd run test:unit
npm.cmd run build
$env:E2E_INTERVIEW_BASE_URL = 'http://localhost'
$env:E2E_INTERVIEW_API_URL = 'http://localhost:18080'
npm.cmd run test:e2e:interview
```

可设置 `E2E_HEADLESS=1` 关闭显示窗口；可用 `E2E_INTERVIEW_ARTIFACTS_DIR` 指定验收文件夹。默认结果路径为 `output/playwright/interview-workspace/{运行时间}/report.json`。结果中的 `checks` 逐项记录实际断言，`screenshots` 记录截图位置；失败时保存已完成的检查和失败截图，进程返回非零状态。

故障专项与页面续跑复用验收报告中的合成账户，避免重复模型调用。续跑保留原报告内容，在独立目录生成结果，并用 `parentReport`、`inheritedCheckCount` 区分前一轮已通过的业务检查与本轮页面检查。它不能替代真实模型业务验收。

```powershell
cd D:\project\bishe\frontend
$env:E2E_INTERVIEW_REPORT = 'D:\project\bishe\output\playwright\interview-workspace\release-20261006-verified\report.json'
npm.cmd run test:e2e:interview:resilience
$env:E2E_INTERVIEW_PARENT_REPORT = $env:E2E_INTERVIEW_REPORT
npm.cmd run test:e2e:interview:continue
cd ..
node scripts/verify-interview-recovery.cjs output/playwright/interview-workspace/release-20261006-ui-verified/report.json
```

覆盖：无简历初次进入、Java/前端/运营、岗位/项目/差距的页面选择和创建、伪造来源、辅导四题与差距八题、模拟四题、首次保存与重复提交、重答与明确采纳、成功评价复用、阶段报告继续作答、模拟提示隔离、暂停与刷新恢复、0/5/20/60 分钟及越界值、最终报告引用、每主问题最多一次追问、模式隔离比较、行动预览和确认幂等、简历候选页面导入且不自动保存、真实学习成果评价和确认后作为项目来源、学习调整草稿和原计划保持有效、越权读取、320px/390px/1440px 和无效会话。

## 验证记录

| 检查 | 当前记录 |
| --- | --- |
| 浏览器脚本语法 | `node --check frontend/scripts/e2e-interview-workspace.cjs` 已通过 |
| 后端定向与完整测试 | `mvn.cmd -s settings.xml.example clean verify` 成功：430 项，0 失败、0 错误、1 跳过；面试核心定向测试 20 项通过 |
| 前端单元测试和生产构建 | `npm.cmd run test:unit`：152 项通过；`npm.cmd run build` 成功 |
| 真实 Docker 浏览器业务验收 | 131 项业务及边界断言通过，Java/前端/运营各完成辅导和模拟流程，六份报告均为真实模型评价；页面续跑 52 项通过，0 浏览器异常 |
| 模型失败、评价重试、迟到响应 | 最新镜像故障专项 16 项、6 张截图通过，0 浏览器异常；1 次受控评价重试、0 次真实模型请求 |
| 超时提醒 | 后端验证暂停、恢复及超时后允许保存；真实请求验证关闭及 5/20/60 分钟、越界拒绝。浏览器提醒使用明确标注的时钟快照，不等待完整 60 分钟 |
| 服务重启持久化 | 实际重启 `ai-service`、`resume-service` 后 41 项核对通过：计时、回答尝试、报告、行动、ACTIVE/DRAFT 计划与确认成果均恢复 |
| 页面视觉和手机滚动 | 最新 Docker 检查 320px、390px、1440px 的开始/答题/报告/历史；无横向溢出，末尾控件可达。逐题评价默认精简，完整维度、引用和版本可展开 |

验收过程中发现并修复：匹配来源与默认岗位不一致、阶段报告继续作答停留在已保存题、Redis 薄弱项偏离来源、0.5 小时任务被截断为零、失败评价带兜底反馈时隐藏重试按钮。最终复核还补齐行动预览与报告版本绑定、旧预览失效检查及确认保存中断后复用已创建练习。伪造学习任务来源返回 `ApiResponse` 业务错误。

第一次完整浏览器运行在 131 项已通过检查后，因滚动脚本遗漏 `overflow-y: visible` 的文档滚动元素而停止；保留原失败报告。修复脚本后在最新镜像上执行独立页面续跑，不重复六类模型流程。续跑报告中的 183 项由 131 项前序检查和 52 项本轮检查组成，不表示重新执行了全部业务检查。

本机报告与截图路径：

- 真实模型及业务断言：`output/playwright/interview-workspace/release-20261006-verified/report.json`，原失败记录保留。
- 最新页面续跑：`output/playwright/interview-workspace/release-20261006-ui-verified/report.json`，27 张截图。
- 重启核对：`output/playwright/interview-workspace/release-20261006-ui-verified/recovery-report.json`。
- 受控故障专项：`output/playwright/interview-resilience-20261006/report.json`。
- 全工作台冒烟：`output/playwright/interview-release-smoke-20261006/smoke-report.json`，53 项滚动检查、0 浏览器异常；部分故障和历史场景采用标注的页面夹具。

唯一跳过项为 `ResumeRenderServiceTest.generatesSyntheticPreviewsFromActualRendererWhenRequested`，该可选模板预览测试需要显式启用，与面试流程无关。模型不可用或出题偏离授权范围时，界面仍明确标记规则来源；受控故障结果不计入真实模型通过数量。中国 Maven、npm、Docker 镜像配置沿用，容器保持运行，可从 `http://localhost` 访问。账户、令牌及截图位于忽略目录，不提交到仓库。
