# 知识库学习工作台

本模块将岗位要求、知识阅读、短练习和学习计划连接起来。学生浏览器入口为
`http://localhost/student/knowledge`, 管理员从原知识管理入口进入。

## 使用流程

1. 在“查知识”输入问题, 或按 Java、前端、运营方向打开专题。
2. 默认只检索资料。开启“通俗讲解”后使用可定位的引用, 没有依据的内容不补写。
3. 打开引用查看全文。PDF 显示原文件页码; Word 只标注提取文本位置。
4. 收藏专题、保存私人笔记、标记自报掌握或完成三道短练习。
5. 先预览实践任务的日程影响, 确认后启用学习计划新版本, 或开始辅导面试。

阅读状态和短练习成绩不修改简历技能或正式证据。实践成果仍需经过学习路径中的成果评价和学生确认。

## API

JSON 响应使用 `ApiResponse<T>`, 时间保存为 ISO 8601, 复习和日程按 `Asia/Shanghai` 计算。
新接口从 Gateway 读取经过验证的身份; 学生不能修改公共资料, 企业不能操作学生个人记录。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/ai/knowledge/topics` | 专题列表, 支持 roleDirection、skill、difficulty、contentType |
| `GET /api/ai/knowledge/topics/{id}` | 专题内容、出处、适用版本、例子、练习 |
| `GET /api/ai/knowledge/recommendations` | 依据本人岗位、匹配、计划和面试的优先推荐 |
| `POST /api/ai/knowledge/workspace/search` | 筛选后检索/讲解并记录本人历史 |
| `GET /api/ai/knowledge/library/{id}` | 可读全文、文档版本、片段偏移和 PDF 页码 |
| `GET /api/ai/knowledge/library/{id}/original` | 认证二进制原件, 支持 Range |
| `GET /api/ai/knowledge/me/items` | 本人收藏、笔记和学习状态 |
| `PUT /api/ai/knowledge/me/items` | 按 expectedRevision 保存个人记录 |
| `DELETE /api/ai/knowledge/me/items/{id}` | 删除本人记录 |
| `POST /api/ai/knowledge/me/items/{id}/review` | 保存自报复习结果, passed=true/false |
| `GET /api/ai/knowledge/me/history` | 本人查询历史 |
| `GET/DELETE /api/ai/knowledge/me/history/{id}` | 阅读或删除历史记录 |
| `POST /api/ai/knowledge/practices` | 请求体 topicId, 创建三题练习 |
| `GET /api/ai/knowledge/practices/{id}` | 本人练习 |
| `GET /api/ai/knowledge/practices/{id}/attempts` | 不可变回答尝试历史 |
| `POST /api/ai/knowledge/practices/{id}/answers` | 保存 questionId、answer, 不调用模型 |
| `POST /api/ai/knowledge/practices/{id}/attempts/{attemptId}/evaluate` | 评价或重试已保存回答 |
| `POST /api/ai/knowledge/actions/preview` | 生成行动预览, 学习计划状态 DRAFT |
| `POST /api/ai/knowledge/actions/confirm` | 根据 previewId 确认, 成功重复请求复用 |
| `GET/POST /api/ai/knowledge/publications` | 管理员列表/创建草稿 |
| `PUT /api/ai/knowledge/publications/{id}` | 管理员编辑, 必须带 expectedRevision |
| `POST /api/ai/knowledge/publications/{id}/publish` | 发布 |
| `POST /api/ai/knowledge/publications/{id}/unpublish` | 下架, 同步使缓存失效 |
| `POST /api/ai/knowledge/publications/{id}/reparse` | 由保留原件重新解析页码, 生成草稿 |

旧 `search`、`answer`、`documents` 和索引接口继续兼容。旧创建接口保持原语义,
新发布入口及新文件导入使用草稿流程。

### 查询

```json
{
  "query": "Redis 缓存失效怎么设计",
  "roleDirection": "JAVA",
  "skill": "Redis",
  "useAi": true
}
```

可选 resumeId、jobId、matchId、planId、interviewSessionId 均由服务端校验所有权和上下文。
查询长度为 1-2000 字。筛选在检索前执行, 返回前再次核对权限。
关键词和向量各取候选, 使用已有 RRF(k=60) 和重排。失败时保留可用资料并标明来源。
最终五条优先保留不同资料的最相关片段，再用原排名补足名额；同一专题筛选仍可返回多个片段。
向量候选使用原始相似度排序和筛选，显示分数保留原来的百分比字段。
纯标题短片段与紧随的正文合并，偏移仍指向原文，不修改原文内容。

### 个人笔记

```json
{"topicId":"专题 ID","kind":"NOTE","note":"自己的理解","expectedRevision":1}
```

kind 为 BOOKMARK、NOTE、STUDY。学习状态为 TO_LEARN、LEARNING、SELF_MASTERED、TO_REVIEW。
版本冲突返回错误; 客户端保留未保存输入。默认复习间隔为 1、3、7、14 天, 可关闭或修改。
同一自然日的重复复习请求复用已记录结果, 不跳过间隔。未来安排保留自报学习状态,
只有到期记录才在读取时显示为待复习, 不因此增加技能证据。

### 行动预览

```json
{"type":"LEARNING_PLAN","topicId":"专题 ID","planId":"本人当前计划 ID","durationWeeks":8}
```

类型为 LEARNING_PLAN 或 INTERVIEW。计划预览只添加有来源的实践任务, 不重生成整个计划。
无计划时默认 2 小时/周、1 周、每天 60 分钟、周一至周五。已有计划沿用原预算;
排不进去时要求显式调整时间或周期。确认前检查原计划时间戳和知识版本, 防止旧预览覆盖新修改。
预览包含具体练习、验收标准、引用资料版本和预计日期。知识任务确认保留每项原有时长,
并重新检查每天及每周预算。旧知识来源撤权或更新后, 需要重新选择当前可读资料。

## 持久化与权限

增量表 `ai_knowledge_workspace` 保存各类版本化快照, 主键为记录类型、所有者、记录 ID。
现有知识正文和向量表保持兼容。DOCUMENT_METADATA 保存发布状态、版本、文件对象和页码;
DOCUMENT_VERSION 保存编辑前正文; 个人记录、查询、练习尝试和行动均按学生隔离。

资料变化后, 历史列表和详情重新核对权限及版本, 不返回受限的旧答案正文。
练习访问和评价也核对其来源版本。评价失败保留回答并允许重试, 成功的同一回答评价复用结果。
引用新增可选 documentVersion、pageNumber; 老引用没有这些字段时继续按文本位置显示。
学习计划保留学生提交的成果, 受限知识原文与引用在读取时隐藏, 不再传给评价模型。
学习引用新增可选 `documentVersion`，保留原有四字段读取兼容性。
普通 AI 计划、知识实践、学习成果派生的项目面试和知识面试后的重规划均继承资料版本保护，
每次读取、成果评价和模型上下文构建前校验当前学生权限、资料版本及引用原句。
旧引用没有版本时仍检查权限和原句一致性。受限资料不会因确认过成果而变为可读；
学生自己的成果说明、链接、确认状态及完成进度仍保留。

## Docker 与验证

继续使用现有中国 Docker、Maven 和 npm 源, 不增加服务。
`AI_KNOWLEDGE_PERSISTENCE_ENABLED=true` 启用 MySQL 快照;
`AI_KNOWLEDGE_OBJECT_STORAGE_ENABLED=true` 启用 MinIO 原件保存;
`AI_KNOWLEDGE_MINIO_BUCKET=knowledge` 指定桶。原件通过认证 API 读取。

`AI_KNOWLEDGE_CONCEPT_GATE_ENABLED=true` 默认启用敏感意图保护。关键词和向量独立召回各 20 条,
RRF 融合前 20 条并至多重排一次后, 只有查询包含隐私、个人档案、医疗、凭据、未公开等敏感词，
且候选标题、正文或技能标签都没有对应敏感概念时，才返回 `NO_EVIDENCE`；普通语义同义问题不会因缺少字面重合而被拒绝。
来源说明不作为概念证据, 同源结果仍按原排名补齐。敏感词类别可由 `AI_KNOWLEDGE_SENSITIVE_QUERY_TERMS` 调整。
设置 gate 为 `false` 恢复不做敏感意图保护的纯向量及重排召回。该配置不改变拒答阈值或增加模型调用。

```powershell
cd D:\project\bishe\backend
mvn -s settings.xml.example clean verify
cd D:\project\bishe\frontend
npm run test:unit
npm run build
cd D:\project\bishe
node scripts/check-knowledge-workspace.cjs
$env:E2E_KNOWLEDGE_API_URL='http://localhost'
node frontend/scripts/e2e-knowledge-workspace.cjs
node scripts/check-knowledge-workspace.cjs --verify-real-model
docker compose restart ai-service
node scripts/check-knowledge-workspace.cjs --verify-restart
```

API 验收保存到 `logs/knowledge-workspace-verification.json`, 不记录密码和令牌。
真实模型专项报告为 `logs/knowledge-workspace-real-model.json`，规则或模型降级不算真实调用通过。
重启专项报告为 `logs/knowledge-workspace-restart.json`。
真实浏览器报告和截图保存到 `output/playwright/knowledge-workspace/`。
固定 60 条评估会临时创建、发布和删除资料，因此必须与专题评估、浏览器发布测试串行运行，
避免资料权限版本改变使另一组查询失效。索引重建也应在这些修改结束后单独运行。
评估样例继续保留 OWNER_REVIEW_PENDING, 未复核标签不标记为正式模型质量基准。
