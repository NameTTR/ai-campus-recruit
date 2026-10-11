# 岗位资料与匿名教学数据填充

本批数据分为三类，不能混称为真实招聘或真实学生：

- 公开招聘资料：实际读取企业官方详情，保留职位、地点、任职年限与原始来源；薪资未公开时不编造。页面名称标注“公开岗位参考”，实际招聘状态以来源网站为准。
- 官方知识资料：依据实际读取的官方文档自编中文短篇讲解，记录来源网址、读取时间与核对范围。引用可定位不等于全部事实已经自动审核。
- 匿名教学样例：180 名合成学生、144 个合成实习职位。学生经历包括无项目、课程实践、社团和小型团队实习协作，明确为合成数据。不会冒用真实个人信息、手机号或作品链接，也不会把学习完成变为技能证据。

## 数据与脚本

| 文件 | 用途 |
| --- | --- |
| `scripts/data/verified-public-sources.json` | 公开岗位、官方知识摘要及真实网络读取结果 |
| `scripts/data/student-scenarios.cjs` | 确定性生成匿名学生资料，Java、前端、运营各 60 名 |
| `scripts/seed-realistic-data.cjs` | 通过已有业务 API 导入，不直接写数据库 |
| `scripts/seed-realistic-data.test.cjs` | 资料一致性、规模参数、DOCX 和错误处理测试 |
| `scripts/verify-seeded-data.cjs` | 校验实际业务记录、日程预算、资料引用、个人隔离与匹配幂等性 |
| `reports/data/public-source-verification.md` | 来源核对与网站访问限制 |
| `reports/data/seed-data-acceptance.md` | 本批数量、修复、Docker 重启及页面验证结果 |

默认导入 180 名学生、144 个合成实习职位及来源目录中的公开岗位与知识资料。每名学生保存主资料、文本型 DOCX 原件、三个岗位匹配及一个收藏笔记。前 24 名另有可编辑草稿、学习计划、辅导或模拟面试、已保存回答、阶段报告和查询历史，并与公开校招或实习岗位匹配。前 24 名在本地合成岗位下建立教学投递，供企业端查看，现有消息流程会执行初筛；不会向真实招聘网站投递。AI 生成和评价均由当前服务执行，脚本不写入伪造模型评分。

企业、学生账号使用 `seed_company_XX`、`seed_student_XX` 前缀，公开岗位使用 `seed_public_jobs` 资料账号，后者不代表企业官方账号。数据仅用于本地演示和教学。

## 运行

启动现有 Docker 服务后，在仓库根目录执行：

```powershell
node --test scripts/seed-realistic-data.test.cjs
node scripts/seed-realistic-data.cjs --dry-run
node scripts/seed-realistic-data.cjs --students 3 --activities 3
node scripts/seed-realistic-data.cjs
```

管理员密码读取现有 `.env` 的 `BOOTSTRAP_ADMIN_PASSWORD`；也可以设置 `SEED_ADMIN_USER`、`SEED_ADMIN_PASSWORD`。脚本没有内置管理员密码。新样例账号密码读取 `SEED_PASSWORD`，未提供时随机生成，保存在本地忽略目录 `logs/seed-realistic-access.json`，不会打印或提交。

若已存在早期试导入账号，首次恢复需要在本地设置 `SEED_LEGACY_PASSWORD`；只用于已存在的保留前缀账号。不要将凭据写入报告或 Git。

可调整 `--students`（1～180）、`--activities`（0～学生数）、`--base-url`、`--timeout-ms`。默认接口为 `http://localhost:18080`，单请求最长等待 180 秒。`--dry-run` 不登录、不联网、不修改数据。正式运行必须存在已核对来源目录。

## 幂等与数据保留

导入进度保存在 `logs/seed-realistic-state.json`，成功步骤立即保存，可在失败后再次运行。脚本通过用户所有权及稳定标记复用岗位、文件、草稿、学习和面试记录；失败的列表读取会中止该步骤，不会被当成空列表后重复创建。错误集中记录，任一失败会返回非零退出码。

本轮仅修订带有 `seed-realistic-20261007` 标签的早期错误来源资料，使用现有发布版本接口保留历史。不删除已有用户、岗位、知识或业务记录。手动补充过的学生主资料会保留，避免覆盖真实内容。保持 `logs` 进度文件以便复跑；从其他电脑运行不等同于拥有同一套账号凭据。

重复导入与服务重启后可检查持久化：

```powershell
node scripts/seed-realistic-data.cjs --verify-only
node scripts/verify-seeded-data.cjs
```

结果保存在 `logs/seed-realistic-report.json`，只含数量、对象 ID 与失败原因，不包含密码、登录令牌或 AI 密钥。最终导入数量与页面检查记录见本批验收报告。

本轮不更换 Docker、Maven、npm 中国镜像，不新增基础设施，也不批量复制第三方课程正文。

## 公开资料扩容批次

2026-10-11 批次使用独立目录，不覆盖原始 `verified-public-sources.json`。新增 126 条公开岗位和 102 篇官方资料中文讲解，公开资料总量分别为 168 条和 136 篇。匿名学生和合成教学岗位不随此批次增加。

| 文件 | 用途 |
| --- | --- |
| `scripts/data/public-jobs-expansion-20261011.json` | 新增岗位原文、招聘类型、技能引用和来源 GET 校验 |
| `scripts/data/knowledge-expansion-20261011.json` | 新增独立知识专题、中文讲解和官方正文核对 |
| `scripts/build-public-source-catalog.cjs` | 合并批次并检查重复来源、采集完整性和数量 |
| `scripts/data/public-sources-expansion-20261011.json` | 旧批次与新批次合并后的导入快照，含输入文件哈希 |
| `scripts/verify-public-expansion.cjs` | 核对全部公开资料的唯一性、内容、学生权限与引用位置 |
| `reports/data/public-expansion-sources-20261011.csv` | 本批 228 条新增来源、核对时间、范围及 SHA256 清单 |
| `reports/data/public-expansion-20261011.md` | 实际导入、浏览器、检索及重启验收结果 |

先构建目录，再执行公开资料专用导入。`--public-only` 不创建学生、简历、匹配、学习计划或面试，不请求生成回答。重复运行必须复用同一来源的已保存记录。

```powershell
node scripts/build-public-source-catalog.cjs
node scripts/seed-realistic-data.cjs --public-only --public-catalog scripts/data/public-sources-expansion-20261011.json --dry-run
node scripts/seed-realistic-data.cjs --public-only --public-catalog scripts/data/public-sources-expansion-20261011.json
node scripts/seed-realistic-data.cjs --public-only --public-catalog scripts/data/public-sources-expansion-20261011.json
node scripts/verify-public-expansion.cjs --public-catalog scripts/data/public-sources-expansion-20261011.json --expect-idempotent --search-per-role 1000
```

`--public-catalog` 接受相对仓库路径或绝对文件路径；未提供时仍使用旧目录。缺失目录、非法 JSON、重复岗位来源和未经完整读取的资料会在业务写入前报错。验证报告保存于忽略目录 `logs/public-expansion-verification.json`。

新增岗位实际分类为 Java 51、前端 17、运营 58；官方未重复前端职位不足 42 条，未改写其他岗位凑分类数量。新增校招 6、实习 12、社会招聘参考 108。社会招聘保留原始经验要求，不可视为应届岗位。公开职位是否仍接受申请以官方最新页面为准。

知识讲解为自编中文材料，练习是教学建议。来源读取、引用定位和内容审核是不同检查，不将 HTTP 200 或引用存在解释为全部事实已完成审核。各专题保留适用版本和核对时间。

完整原响应属于本地审计资料，位于忽略目录 `logs/public-expansion/`。普通检出没有快照时，采集测试会明确跳过原响应核验；本机验收设置 `REQUIRE_SOURCE_SNAPSHOTS=1` 后执行以下测试，任意快照缺失、哈希变化或原句定位错误均失败：

```powershell
$env:REQUIRE_SOURCE_SNAPSHOTS='1'
node --test scripts/build-public-source-catalog.test.cjs scripts/collect-public-jobs-expansion.test.cjs scripts/collect-knowledge-expansion.test.cjs scripts/seed-realistic-data.test.cjs
```

需要重新采集时，运行 `node scripts/collect-public-jobs-expansion.cjs` 和 `node scripts/collect-knowledge-expansion.cjs`，复核新目录及失败记录后再合并导入；网站内容可能变化，不能保证重新采集仍返回相同岗位及响应哈希。此次有效资料为 136 篇，另保留 1 条已下架的来源校正历史；详见验收报告。
