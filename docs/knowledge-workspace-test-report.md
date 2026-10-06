# 知识工作台验收记录

本文件面向项目负责人和维护人员, 区分代码检查、Docker 接口、真实浏览器、固定查询评估及人工审核。
最终代码和 Docker 验证已完成; 专题正文与固定集标签仍需项目负责人进行人工复核。

## 固定评估范围

`scripts/evaluate-knowledge-workspace.cjs` 使用 30 个内置中文专题及 30 条固定中文查询。
Java、前端、运营各 10 个专题和 10 条查询。校准集与测试集各 15 条;
每个集合有 12 条有答案查询、3 条无答案查询, 包含技能名称和相关表述。
预期标签由作者在查询前按材料拟定, 状态保留 `OWNER_REVIEW_PENDING`, reviewedBy 为空。
明确技能查询使用方向和技能筛选; 同义表达查询只使用岗位方向及其中文别名, 不靠目标技能筛选指定专题。
所有查询限定已发布专题, 因此这里的 Recall@5 是专题工作台筛选场景指标, 不等于全知识库无筛选召回率。

逐篇核对 30 篇专题的出处、适用版本、核对日期和前置关系;
服务返回的全部片段和检索引用核对 chunk ID、序号、全文偏移、原句、发布状态及学生可读权限。
测试集 Recall@5 目标为 85%, 校准数据只记录观察结果, 不自动修改阈值。
所有无答案查询应无引用、无事实性 claim, 并显示 NO_EVIDENCE。

默认 `useAi:false`, 不请求回答生成, 查询仍可能调用向量和重排接口。
既有 [60 条固定 RAG 评估](../evaluation/README.md) 包含独立权限场景, 由主会话运行;
本次 30 条查询不能代替它, 也不宣称覆盖回答生成质量。

## 执行方式

```powershell
cd D:\project\bishe
node --check scripts/evaluate-knowledge-workspace.cjs
node scripts/evaluate-knowledge-workspace.cjs --offline --output evaluation/reports/knowledge-workspace-offline.json
node scripts/evaluate-knowledge-workspace.cjs --base http://localhost --output evaluation/reports/knowledge-workspace.json
```

接口评估默认创建合成学生账户, 不改公共资料, 结束时删除该账户本轮查询历史。
也可通过 `KNOWLEDGE_EVAL_TOKEN` 使用现有学生令牌, 此时保留账户查询历史。
输出不记录密码、JWT、请求头或服务错误原文。
API 不可用、响应无效或位置核对失败时照常保存失败报告并返回非零状态;
`--offline` 仅检查本地数据结构, 报告状态为 OFFLINE_ONLY, 不等于 Docker 验收通过。

## 当前记录

| 检查 | 已知状态 | 结果文件 |
| --- | --- | --- |
| 新评估脚本语法 | 已通过 `node --check scripts/evaluate-knowledge-workspace.cjs` | 不产生报告 |
| 30 专题本地字段与依赖 | 已通过: 30 专题、30 查询、15/15 分组, 无字段或依赖问题; 仅本地检查 | `evaluation/reports/knowledge-workspace-offline.json` |
| 服务不可用时的失败报告 | 已验证: 不可用本地地址返回 FAILED_OPERATIONAL、非零退出码, 无虚构通过指标 | `evaluation/reports/knowledge-workspace-unavailable.json` |
| 30 查询真实检索及原文引用 | 最终通过: 30 专题、30 查询、264 引用/位置检查零失败; 测试集有答案 12/12, 无答案 3/3 | `evaluation/reports/knowledge-workspace-final-v2.json` |
| 后端完整测试 | 最终 `mvn -s settings.xml.example clean verify` BUILD SUCCESS; 505 tests, 0 failures, 0 errors, 1 skipped | `logs/knowledge-backend-final-verify-v3.log` |
| 前端测试及生产构建 | Vitest 162 passed; `npm run build` passed | `logs/knowledge-frontend-final-unit.log` / `logs/knowledge-frontend-final-build.log` |
| 既有 60 查询评估 | 最终 Recall@5 26/30 = 86.7%; 无答案 6/6, 权限泄漏 0, 30 条有答案均重排 | `evaluation/reports/knowledge-fixed60-final-v3.json` |
| 桌面和 320px/390px 浏览器 | 最终 118 checks, 31 screenshots, 0 browser errors | `output/playwright/knowledge-workspace/2026-10-06T15-23-32-749Z/report.json` |
| 权限改变、发布与下架 | API 工作台 64 checks passed; 引用、Range、下架和双账号隔离通过 | `logs/knowledge-workspace-verification.json` |
| 服务重启恢复 | 4 checks passed; 笔记、练习、历史、计划均可恢复 | `logs/knowledge-workspace-restart.json` |
| 专题与标签人工复核 | 未完成, OWNER_REVIEW_PENDING | 负责人记录复核人及时间 |

实际结果应填写本次运行时间、镜像部署状态、通过数量、模式、引用失败数和报告路径。
故障替身、规则评价和真实模型分别记录。中国 Docker、Maven、npm 源沿用现有配置。

## 30 查询 Docker 实测

2026-10-06 23:23 (Asia/Shanghai), 新 ai-service 与 frontend 镜像启动 healthy 后,
对 `http://localhost` 执行最终在线命令。报告状态为 `PASSED_PENDING_OWNER_REVIEW`。
专题和固定查询标签仍为 `OWNER_REVIEW_PENDING`; 通过结果没有改写标签。

| 指标 | 实测结果 |
| --- | --- |
| 专题目录、原文与来源元数据 | 30/30 通过 |
| 测试集有答案 Recall@5 | 12/12 = 100%, 达到 85% 目标 |
| 测试集无答案拒答 | 3/3 |
| 校准集有答案 Recall@5 | 12/12 = 100% |
| 校准集无答案拒答 | 3/3 |
| 查询总体 | 30/30 通过 |
| 片段及返回引用位置检查 | 264 次, 0 失败 |
| 跨岗位方向专题泄漏 | 0 |
| 接口运行错误 / 历史清理失败 | 0 / 0 |
| 实际检索模式 | HYBRID_RRF_RERANK: 27 次; HYBRID_RRF: 3 次 |
| 回答生成模式 | RETRIEVAL_ONLY: 30 次; 未请求生成, 无事实性 claims |

此前串行评估曾记录 `KW-OPS-05` 无答案误召回以及固定集 Recall 80%;
修复了语义候选来源重复、敏感请求证据门槛和缓存版本后重新构建镜像、索引并复测。
历史失败报告仍保留在 `evaluation/reports/knowledge-workspace.json` 与
`evaluation/reports/knowledge-fixed60-final-stable.json`, 用于审计修复过程。

固定 60 条评估的最终报告为 `knowledge-fixed60-final-v3.json`:
测试集有答案 Recall@5 为 26/30 = 86.7%, 无答案 6/6, 权限泄漏 0;
全部 30 条有答案查询经过重排。该固定集的合成标签仍等待项目负责人复核。

## 真实模型与索引

三类岗位的最终真实调用均通过: `logs/knowledge-workspace-real-model.json`。
Java、前端、运营均为非 mocked 结果, 检索包含 RRF 与重排, 引用版本和原文偏移全部核对。
索引重建报告为 `logs/knowledge-index-rebuild.json`: 170 documents, 290 chunks,
`text-embedding-v4`, 1024 dimensions。资料目录中包含验收期间保留的历史合成资料;
公共检索仍按发布状态和权限过滤。

敏感请求保护由 `AI_KNOWLEDGE_SENSITIVE_QUERY_TERMS` 配置, 默认覆盖隐私、个人资料、医疗、凭据及未公开内容。
普通没有字面重合的语义问题继续允许向量/重排候选, 只有敏感类别且候选无对应概念时拒答。

## 内容与来源边界

来源登记详见 [中文知识专题来源清单](knowledge-sources.md)。中文正文为平台自编, 外部页面为参考资料。
字段检查不能证明参考 URL 可访问或每一句都已经过作者/负责人审核。
检索引用有效只说明原句可以定位, 不能替代内容事实审核。

当前只读内容审查未发现可直接确认为事实错误的核心结论;
部分参考 URL 为入口页, 与全部具体概念不是逐句对应, 已在来源清单列出复核重点。
该观察不构成 30 篇内容全部审核通过。
