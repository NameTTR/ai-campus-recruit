# 岗位资料与匿名教学数据填充

本批数据分为三类，不能混称为真实招聘或真实学生：

- 公开招聘资料：实际读取企业官方详情，保留职位、地点、任职年限与原始来源；薪资未公开时不编造。页面名称标注“公开岗位参考”，实际招聘状态以来源网站为准。
- 官方知识资料：依据实际读取的官方文档自编中文短篇讲解，记录来源网址、读取时间与核对范围。引用可定位不等于全部事实已经自动审核。
- 匿名教学样例：60 名合成学生、48 个合成实习职位。学生经历包括无项目、课程实践、社团和小型团队实习协作，明确为合成数据。不会冒用真实个人信息、手机号或作品链接，也不会把学习完成变为技能证据。

## 数据与脚本

| 文件 | 用途 |
| --- | --- |
| `scripts/data/verified-public-sources.json` | 公开岗位、官方知识摘要及真实网络读取结果 |
| `scripts/data/student-scenarios.cjs` | 确定性生成匿名学生资料，Java、前端、运营各 20 名 |
| `scripts/seed-realistic-data.cjs` | 通过已有业务 API 导入，不直接写数据库 |
| `scripts/seed-realistic-data.test.cjs` | 资料一致性、规模参数、DOCX 和错误处理测试 |
| `scripts/verify-seeded-data.cjs` | 校验实际业务记录、日程预算、资料引用、个人隔离与匹配幂等性 |
| `reports/data/public-source-verification.md` | 来源核对与网站访问限制 |
| `reports/data/seed-data-acceptance.md` | 本批数量、修复、Docker 重启及页面验证结果 |

默认导入 60 名学生、48 个合成实习职位及来源目录中的公开岗位与知识资料。每名学生保存主资料、文本型 DOCX 原件、三个岗位匹配及一个收藏笔记。前 12 名另有可编辑草稿、学习计划、辅导或模拟面试、已保存回答、阶段报告和查询历史，并与公开校招或实习岗位匹配。前 12 名在本地合成岗位下建立教学投递，供企业端查看，现有消息流程会执行初筛；不会向真实招聘网站投递。AI 生成和评价均由当前服务执行，脚本不写入伪造模型评分。

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

可调整 `--students`（1～60）、`--activities`（0～学生数）、`--base-url`、`--timeout-ms`。默认接口为 `http://localhost:18080`，单请求最长等待 180 秒。`--dry-run` 不登录、不联网、不修改数据。正式运行必须存在已核对来源目录。

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
