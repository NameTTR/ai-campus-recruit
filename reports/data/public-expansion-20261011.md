# 公开资料三倍新增批次验收

验收日期：2026-10-11。运行地址：`http://localhost`。本批在原有 42 条公开岗位、34 篇官方资料基础上，分别新增原数量的三倍；不增加真实学生个人信息，不把合成样例改称真实数据。

## 最终数据

| 资料 | 原有 | 新增 | 当前批次合计 |
| --- | ---: | ---: | ---: |
| 真实公开岗位参考 | 42 | 126 | 168 |
| 官方资料中文自编讲解 | 34 | 102 | 136 |
| 匿名合成学生 | 180 | 0 | 180 |
| 合成教学岗位 | 144 | 0 | 144 |

新增岗位为 Java 51、前端 17、运营 58。官方可读取、未重复的前端岗位不足 42 条，没有将其他岗位改名凑数。新增岗位含校招 6、实习 12、社会招聘参考 108；全部公开岗位合计校招 13、实习 23、社招参考 132。社招岗位保留工作经验要求，不能视为应届或实习岗位；招聘是否仍开放以官方当前页面为准。未公开薪资不编造。

新增知识资料为 Java、前端、运营各 34 篇，旧资料合并后分别为 46、46、44 篇。每篇包含独立中文讲解、易错点、短练习与验收标准，保留适用版本、官方来源和读取时间。练习是教学建议，不是实际学生成果。

完整新增来源清单见 [CSV 清单](public-expansion-sources-20261011.csv)，保留 228 条资料的标题、方向、来源地址、核对时间、适用范围及响应 SHA256。可复跑的导入快照为 [public-sources-expansion-20261011.json](../../scripts/data/public-sources-expansion-20261011.json)。原始批次未覆盖。

## 来源核对与修正

岗位来自腾讯官方招聘和校园招聘详情接口，职责、要求、地点、经验与原始岗位 ID 保留原文。JavaScript 不误记为 Java，技能引用保留任职要求字符位置。旧岗位只补充原始来源接口地址。

知识资料实际读取 Oracle、Spring、Redis、MDN、Vue、React、TypeScript、Microsoft、NIST 官方正文；102 篇均完整 GET 成功，保存响应字节数、SHA256、正文摘录及 UTF-16 字符位置。HTTP 成功和引用可定位不等于完成全部事实人工审核。7 个失效候选地址仅记入失败发现清单，不发布。

来源复核中将“配对比较”的参考地址校正到 NIST 配对分析章节。初次导入的旧地址副本已下架，保留历史，不再向学生返回正文或进入检索。当前批次有效知识资料为 136 篇；本地导入状态另保留 1 条下架审计记录，故状态文件资料计数为 137。重复导入复用 136 篇有效资料，不继续创建副本。

页面修复了公开岗位详情误显示“招聘中”的问题：现在显示“公开岗位参考”；合成企业岗位显示“教学样例”。资料更新或下架导致历史查询失效时，搜索页明确提示“资料已更新或权限已变化，请重新搜索”，不再误显示为普通无结果。

## 自动验证

执行命令：

```powershell
$env:REQUIRE_SOURCE_SNAPSHOTS='1'
node --test scripts/build-public-source-catalog.test.cjs scripts/collect-public-jobs-expansion.test.cjs scripts/collect-knowledge-expansion.test.cjs scripts/seed-realistic-data.test.cjs
node scripts/build-public-source-catalog.cjs
node scripts/seed-realistic-data.cjs --public-only --public-catalog scripts/data/public-sources-expansion-20261011.json --dry-run
node scripts/seed-realistic-data.cjs --public-only --public-catalog scripts/data/public-sources-expansion-20261011.json
node scripts/seed-realistic-data.cjs --public-only --public-catalog scripts/data/public-sources-expansion-20261011.json
node scripts/verify-public-expansion.cjs --public-catalog scripts/data/public-sources-expansion-20261011.json --expect-idempotent --search-per-role 1000
node scripts/verify-seeded-data.cjs
```

结果：

- 38 项采集、来源完整性、去重、导入及幂等测试全部通过，无跳过。全部 126 个岗位原响应及 102 篇知识原响应在本机核对了字节数和 SHA256。
- 168 个岗位、136 篇学生可读资料的唯一性、正文、来源及权限通过；137 个分块位置均可逐字定位。
- 136 次标题查询全部在前 5 条检索结果中找到对应资料；最终重启后返回的 673 条引用全部有效，检索模式为 `HYBRID_RRF_RERANK`。本项是已知标题回归，不等同于独立匿名查询集的 Recall@5 指标。
- 再次导入：`jobsReused=168`、`knowledgeReused=136`，没有新增或更新，没有操作学生、简历、匹配、学习计划、面试，也没有请求生成回答。
- 原业务回归：180 份资料与简历、24 份草稿、564 条匹配、24 个计划/232 个任务、24 个面试/33 份回答、180 条收藏保持可用；所有权隔离及重复匹配通过，`failures=[]`。
- 前端 170 项单元测试及生产构建通过。此批没有修改后端服务代码或公开 API，未重新运行 Maven 全量验证。

原响应与完整日志位于忽略目录 `logs/public-expansion/`，不把官方网页全文和账号凭据提交到 Git。普通检出没有原响应时，快照核验测试明确跳过；设置 `REQUIRE_SOURCE_SNAPSHOTS=1` 后任何缺失都报错，不能把跳过视为来源验收成功。

## 浏览器与重启

使用真实可见浏览器验证：

- 168 个公开岗位的搜索、14 页末页、无结果状态及 Java/前端/运营详情原文。
- 新增 Redis 事务、Vue Router、内容发布前检查三篇资料的搜索、引用高亮、全文、刷新恢复和返回结果，共 30 项浏览器检查，无异常。
- 320、390、1440 像素页面无横向溢出，长页面能滚到底。
- 来源变更后的旧查询明确提示重新搜索，普通空态隐藏；学生点击搜索后恢复当前有效资料。

关键截图：

- [岗位列表](screenshots/public-expansion/jobs-168-1440.png)
- [知识搜索](screenshots/public-expansion/knowledge-search-2-1440.png)
- [Java 手机全文](screenshots/public-expansion/knowledge-reader-0-320.png)
- [前端手机全文](screenshots/public-expansion/knowledge-reader-1-320.png)
- [运营手机全文](screenshots/public-expansion/knowledge-reader-2-320.png)
- [旧查询失效提示](screenshots/public-expansion/stale-history-1440.png)

前端镜像已构建部署；`ai-service`、`job-service` 重启后重新检查全部公开资料、所有引用及原业务记录，全部通过。下架旧来源不能读取，也未出现在学生搜索中。15 个容器全部健康；MySQL、MinIO、Redis 数据卷和中国镜像配置保留。

完整运行报告位于 `logs/public-expansion-verification.json`、`logs/public-expansion/browser-knowledge.json` 和 `logs/public-expansion/business-after-restart.json`。本报告记录本机实际验收结果；后续网站内容更新需重新采集、复核来源与适用版本。
