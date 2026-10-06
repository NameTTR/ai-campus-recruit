const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

const root = path.resolve(__dirname, '..')
const topicFile = path.join(root, 'backend/ai-service/src/main/resources/knowledge-topics/campus-topics-zh.json')
const topics = JSON.parse(fs.readFileSync(topicFile, 'utf8'))
const topicById = new Map(topics.map(topic => [topic.id, topic]))
const args = new Map()
for (let i = 2; i < process.argv.length; i++) {
  if (!process.argv[i].startsWith('--')) throw new Error('Expected a named option')
  args.set(process.argv[i].slice(2), process.argv[i + 1] && !process.argv[i + 1].startsWith('--') ? process.argv[++i] : true)
}
const baseUrl = String(args.get('base') || process.env.KNOWLEDGE_EVAL_BASE_URL || process.env.MVP_BASE_URL || 'http://localhost').replace(/\/+$/, '')
const outputFile = path.resolve(root, String(args.get('output') || 'evaluation/reports/knowledge-workspace.json'))
const offline = args.has('offline')
const timeoutMs = Number(args.get('timeout-ms') || 150000)

// Labels are fixed before querying; the runner never uses test rows to select a threshold.
const cases = [
  ['KW-JAVA-01', 'calibration', 'JAVA', 'answerable', 'Java', '学生名单需要顺序展示并按学号查询，集合怎样选择？', ['KT-JAVA-01'], 'ROLE_SKILL'],
  ['KW-JAVA-02', 'calibration', 'JAVA', 'answerable', '异常处理', '读取文件失败时怎样保留异常原因，同时避免吞掉错误？', ['KT-JAVA-02'], 'ROLE_SKILL'],
  ['KW-JAVA-03', 'calibration', 'JAVA', 'answerable', '并发', '可见性修饰符为什么不能让计数加一成为原子操作？', ['KT-JAVA-03'], 'SYNONYM'],
  ['KW-JAVA-04', 'calibration', 'JAVA', 'answerable', 'Spring Boot', '控制器、业务层与数据访问层应该分别负责什么？', ['KT-JAVA-04'], 'SYNONYM'],
  ['KW-JAVA-05', 'calibration', 'JAVA', 'unanswerable', null, '请给出2027届每一家校园招聘公司的保密录取名单。', [], 'NO_ANSWER'],
  ['KW-JAVA-06', 'test', 'JAVA', 'answerable', 'SQL', '学生没有成果记录时，左连接统计为何不能直接数所有行？', ['KT-JAVA-06'], 'SYNONYM'],
  ['KW-JAVA-07', 'test', 'JAVA', 'answerable', 'MySQL', '计划切换只更新一半时怎样回滚，联合索引又解决什么问题？', ['KT-JAVA-07'], 'SYNONYM'],
  ['KW-JAVA-08', 'test', 'JAVA', 'answerable', 'Redis', '旁路缓存更新后如何失效，TTL能否证明强一致性？', ['KT-JAVA-08'], 'SYNONYM'],
  ['KW-JAVA-09', 'test', 'JAVA', 'answerable', '排障', '服务端口正常但接口失败，怎样沿请求路径定位依赖超时？', ['KT-JAVA-10'], 'ROLE_SKILL'],
  ['KW-JAVA-10', 'test', 'JAVA', 'unanswerable', null, '星际光量子引擎ZQ739的精确制造配方是什么？', [], 'NO_ANSWER'],
  ['KW-FRONT-01', 'calibration', 'FRONTEND', 'answerable', 'HTML', '搜索结果导航和收藏操作，链接与按钮应该怎样区分？', ['KT-FRONT-01'], 'ROLE_SKILL'],
  ['KW-FRONT-02', 'calibration', 'FRONTEND', 'answerable', 'CSS', '手机320像素时长文本把网格撑宽，该检查什么布局约束？', ['KT-FRONT-02'], 'ROLE_SKILL'],
  ['KW-FRONT-03', 'calibration', 'FRONTEND', 'answerable', 'JavaScript', '浅拷贝以后修改内部对象，为什么原对象也发生变化？', ['KT-FRONT-03'], 'SYNONYM'],
  ['KW-FRONT-04', 'calibration', 'FRONTEND', 'answerable', '异步请求', '旧网络响应迟到时怎样避免覆盖新搜索，fetch何时需要检查状态码？', ['KT-FRONT-04'], 'SYNONYM'],
  ['KW-FRONT-05', 'calibration', 'FRONTEND', 'unanswerable', null, '请提供所有用户尚未公开的私人账号口令。', [], 'NO_ANSWER'],
  ['KW-FRONT-06', 'test', 'FRONTEND', 'answerable', 'TypeScript', '加载、成功、失败三种状态怎样用联合类型区分，断言能替代校验吗？', ['KT-FRONT-05'], 'SYNONYM'],
  ['KW-FRONT-07', 'test', 'FRONTEND', 'answerable', 'Vue', '子组件可以直接改父组件传下来的props吗，怎样表达用户操作？', ['KT-FRONT-06'], 'ROLE_SKILL'],
  ['KW-FRONT-08', 'test', 'FRONTEND', 'answerable', 'React', 'React副作用里怎样清理请求，避免旧结果写入新状态？', ['KT-FRONT-07'], 'ROLE_SKILL'],
  ['KW-FRONT-09', 'test', 'FRONTEND', 'answerable', '测试与无障碍', '不用鼠标怎样验证Tab阅读顺序、错误提示和完整页面流程？', ['KT-FRONT-10'], 'SYNONYM'],
  ['KW-FRONT-10', 'test', 'FRONTEND', 'unanswerable', null, '古生物火星三叶虫XQ128的完整DNA序列是什么？', [], 'NO_ANSWER'],
  ['KW-OPS-01', 'calibration', 'OPERATIONS', 'answerable', '用户分析', '观察到用户不报名，怎样区分已知事实与对原因的猜测？', ['KT-OPS-01'], 'ROLE_SKILL'],
  ['KW-OPS-02', 'calibration', 'OPERATIONS', 'answerable', '内容策略', '内容选题怎样对应用户问题，并用反馈验证是否有帮助？', ['KT-OPS-02'], 'ROLE_SKILL'],
  ['KW-OPS-03', 'calibration', 'OPERATIONS', 'answerable', '文案', '活动文案怎样让读者看懂下一步，避免无法验证的夸大承诺？', ['KT-OPS-03'], 'ROLE_SKILL'],
  ['KW-OPS-04', 'calibration', 'OPERATIONS', 'answerable', '活动策划', '活动方案怎样安排目标、资源、负责人、风险和验收？', ['KT-OPS-04'], 'SYNONYM'],
  ['KW-OPS-05', 'calibration', 'OPERATIONS', 'unanswerable', null, '请公布未发布活动的全部个人医疗档案。', [], 'NO_ANSWER'],
  ['KW-OPS-06', 'test', 'OPERATIONS', 'answerable', '数据分析', '点击率的分子分母与观察窗口不一致时，数据为何不可直接比较？', ['KT-OPS-06'], 'SYNONYM'],
  ['KW-OPS-07', 'test', 'OPERATIONS', 'answerable', '漏斗分析', '访问、报名、到场的转化链路怎样定位掉队，不能直接断言什么原因？', ['KT-OPS-07'], 'SYNONYM'],
  ['KW-OPS-08', 'test', 'OPERATIONS', 'answerable', 'Excel', '表格分析前怎样去重清洗，并保存可追溯公式与原始数据？', ['KT-OPS-08'], 'ROLE_SKILL'],
  ['KW-OPS-09', 'test', 'OPERATIONS', 'answerable', 'A/B测试', '对照实验怎样先定假设、分组与判断规则，为什么不能看到上涨就宣布成功？', ['KT-OPS-09'], 'SYNONYM'],
  ['KW-OPS-10', 'test', 'OPERATIONS', 'unanswerable', null, '未公开的QY894外星演唱会精确上座率是多少？', [], 'NO_ANSWER']
].map(([id, split, roleDirection, kind, skill, query, expectedTopicIds, variant]) => ({ id, split, roleDirection, kind, skill, query, expectedTopicIds, variant,
  requestedRoleDirection: variant === 'SYNONYM' ? { JAVA: 'Java 后端', FRONTEND: '前端', OPERATIONS: '运营' }[roleDirection] : roleDirection,
  requestedSkill: variant === 'SYNONYM' ? null : skill,
  expectedDocumentIds: expectedTopicIds.map(topicId => topicById.get(topicId)?.documentId),
  labelRationale: kind === 'answerable' ? '作者依据发布前的专题材料拟定；未根据被测返回倒推标签' : '该请求需要专题未提供的秘密资料或不存在的精确数据，检索不应提供依据',
  reviewStatus: 'OWNER_REVIEW_PENDING' }))

function positionFailures(location, document) {
  if (!location || !document) return ['MISSING_SOURCE']
  const failures = []
  if (!location.chunkId || !Number.isInteger(location.chunkIndex) || location.chunkIndex < 1) failures.push('INVALID_CHUNK_ID_OR_INDEX')
  if (!Number.isInteger(location.startOffset) || !Number.isInteger(location.endOffset) || location.startOffset < 0
    || location.endOffset <= location.startOffset || location.endOffset > document.content.length) failures.push('INVALID_OFFSETS')
  else if (document.content.slice(location.startOffset, location.endOffset) !== location.snippet) failures.push('NOT_ORIGINAL_SUBSTRING')
  if (!(document.roles || []).some(role => ['ALL', 'STUDENT'].includes(role))) failures.push('NOT_READABLE_BY_STUDENT')
  if (document.status !== 'PUBLISHED') failures.push('NOT_PUBLISHED')
  return failures
}

function validateLocal() {
  const issues = []
  const ids = new Set()
  const documentIds = new Set()
  const roles = Object.fromEntries(['JAVA', 'FRONTEND', 'OPERATIONS'].map(role => [role, topics.filter(topic => topic.role === role).length]))
  if (topics.length !== 30 || Object.values(roles).some(count => count !== 10)) issues.push('Expected 30 topics and 10 per role')
  for (const topic of topics) {
    if (ids.has(topic.id) || documentIds.has(topic.documentId)) issues.push(`${topic.id}: duplicate identity`)
    ids.add(topic.id); documentIds.add(topic.documentId)
    for (const field of ['id', 'skill', 'title', 'summary', 'content', 'example', 'practicePrompt', 'source', 'sourceUrl', 'applicableVersion', 'checkedAt']) {
      if (!topic[field] || typeof topic[field] !== 'string') issues.push(`${topic.id}: missing ${field}`)
    }
    try { if (new URL(topic.sourceUrl).protocol !== 'https:') issues.push(`${topic.id}: source URL must use HTTPS`) } catch { issues.push(`${topic.id}: invalid source URL`) }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(topic.checkedAt)) issues.push(`${topic.id}: invalid source date`)
    if (!(topic.estimatedMinutes > 0) || topic.status !== 'PUBLISHED') issues.push(`${topic.id}: invalid duration or publication status`)
    for (const predecessor of topic.prerequisites || []) if (!topicById.has(predecessor)) issues.push(`${topic.id}: unknown prerequisite`)
  }
  const visited = new Set()
  function dependencyCheck(id, ancestry = new Set()) {
    if (ancestry.has(id)) { issues.push(`${id}: prerequisite cycle`); return }
    if (visited.has(id)) return
    const next = new Set(ancestry).add(id)
    for (const dependency of topicById.get(id)?.prerequisites || []) dependencyCheck(dependency, next)
    visited.add(id)
  }
  topics.forEach(topic => dependencyCheck(topic.id))
  if (cases.length !== 30 || new Set(cases.map(row => row.id)).size !== 30) issues.push('Expected 30 unique fixed query cases')
  for (const row of cases) {
    if (!['calibration', 'test'].includes(row.split)) issues.push(`${row.id}: bad split`)
    if (row.expectedDocumentIds.some(id => !id)) issues.push(`${row.id}: unknown topic label`)
  }
  const positiveDocument = { content: 'abc', roles: ['STUDENT'], status: 'PUBLISHED' }
  if (positionFailures({ chunkId: 'c', chunkIndex: 1, startOffset: 0, endOffset: 3, snippet: 'abc' }, positiveDocument).length) issues.push('Citation positive validator failed')
  if (!positionFailures({ chunkId: 'c', chunkIndex: 1, startOffset: 0, endOffset: 2, snippet: 'abc' }, positiveDocument).length) issues.push('Citation validator allowed wrong offset')
  if (!positionFailures({ chunkId: 'c', chunkIndex: 1, startOffset: 0, endOffset: 3, snippet: 'abc' }, { ...positiveDocument, roles: ['ADMIN'] }).includes('NOT_READABLE_BY_STUDENT')) issues.push('Citation validator allowed restricted material')
  if (!positionFailures({ chunkId: 'c', chunkIndex: 1, startOffset: 0, endOffset: 3, snippet: 'abc' }, { ...positiveDocument, status: 'DRAFT' }).includes('NOT_PUBLISHED')) issues.push('Citation validator allowed unpublished material')
  return { passed: !issues.length, topicCount: topics.length, roleCounts: roles, queryCount: cases.length, calibrationCount: cases.filter(row => row.split === 'calibration').length,
    testCount: cases.filter(row => row.split === 'test').length, answerableCount: cases.filter(row => row.kind === 'answerable').length,
    noAnswerCount: cases.filter(row => row.kind === 'unanswerable').length, issues }
}

async function evaluate() {
  const report = { datasetVersion: 'knowledge-workspace-zh-v1', evaluatedAt: new Date().toISOString(), reviewStatus: 'OWNER_REVIEW_PENDING', reviewedBy: null,
    origin: 'SYNTHETIC_ANONYMOUS', generationRequested: false, local: validateLocal(), status: 'NOT_RUN',
    topicsSha256: crypto.createHash('sha256').update(fs.readFileSync(topicFile)).digest('hex'),
    fixedQueriesSha256: crypto.createHash('sha256').update(JSON.stringify(cases)).digest('hex'),
    remote: { catalog: [], rows: [], citationChecks: 0, citationFailures: 0, operationalErrors: [], roleLeaks: 0, cleanupFailures: 0 },
    calibration: { scope: 'calibration rows only', thresholdApplied: false, status: 'NO_THRESHOLD_CHANGE' },
    limitations: ['Expected relevance and absence labels await project-owner review.', 'Retrieval-only excludes answer-generation quality; embedding/rerank calls may still occur.',
      'Declared checkedAt dates are metadata, not proof that external source URLs were opened or every sentence reviewed.', 'The separate existing 60-query permission and RAG benchmark is not replaced by these 30 workspace queries.'] }
  if (!report.local.passed) { report.status = 'LOCAL_VALIDATION_FAILED'; return report }
  if (offline) { report.status = 'OFFLINE_ONLY'; return report }
  const documents = new Map()
  let student
  const timeout = Number.isFinite(timeoutMs) && timeoutMs >= 1000 ? timeoutMs : 150000
  async function api(route, method = 'GET', data, token = student?.token) {
    const response = await fetch(baseUrl + route, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(data ? { 'Content-Type': 'application/json' } : {}) },
      body: data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(timeout) })
    const body = await response.json()
    if (!response.ok || body.code !== 0 || body.data == null) throw new Error('API_ERROR')
    return body.data
  }
  const safeError = error => error.name === 'TimeoutError' || error.name === 'AbortError' ? 'API_TIMEOUT' : 'API_OR_SOURCE_VALIDATION_FAILED'
  async function sourceDocument(id) {
    if (!documents.has(id)) documents.set(id, await api('/api/ai/knowledge/library/' + encodeURIComponent(id)))
    return documents.get(id)
  }
  try {
    if (process.env.KNOWLEDGE_EVAL_TOKEN) student = { token: process.env.KNOWLEDGE_EVAL_TOKEN }
    else student = await api('/api/auth/register', 'POST', { username: 'knowledge_eval_' + Date.now().toString(36) + '_' + crypto.randomBytes(3).toString('hex'),
      password: crypto.randomBytes(24).toString('hex'), displayName: '匿名中文知识评估', role: 'STUDENT' }, null)
    const remoteTopics = await api('/api/ai/knowledge/topics')
    for (const topic of topics) {
      const row = { id: topic.id, documentId: topic.documentId, sourceChecks: [], locationCount: 0, citationErrors: [] }
      try {
        const [actual, document] = await Promise.all([api('/api/ai/knowledge/topics/' + encodeURIComponent(topic.id)), sourceDocument(topic.documentId)])
        for (const field of ['id', 'documentId', 'source', 'sourceUrl', 'applicableVersion', 'checkedAt', 'role', 'skill', 'version']) {
          if (actual[field] !== topic[field]) row.sourceChecks.push(`MISMATCH_${field.toUpperCase()}`)
        }
        if (!remoteTopics.some(item => item.id === topic.id)) row.sourceChecks.push('TOPIC_NOT_LISTED')
        if (document.source !== topic.source) row.sourceChecks.push('SOURCE_CREDIT_MISMATCH')
        if (!document.content.includes(topic.sourceUrl)) row.sourceChecks.push('SOURCE_URL_MISSING_IN_DOCUMENT')
        if (!document.content.includes(topic.summary) || !document.content.includes(topic.content)) row.sourceChecks.push('ORIGINAL_TOPIC_TEXT_MISMATCH')
        row.locationCount = document.locations.length
        if (!row.locationCount) row.citationErrors.push('NO_SOURCE_LOCATIONS')
        for (const location of document.locations) {
          report.remote.citationChecks++
          const failures = positionFailures(location, document)
          if (failures.length) report.remote.citationFailures++
          row.citationErrors.push(...failures)
        }
        row.passed = !row.sourceChecks.length && !row.citationErrors.length
      } catch (error) { row.error = safeError(error); report.remote.operationalErrors.push({ stage: 'catalog', id: topic.id, error: row.error }); row.passed = false }
      report.remote.catalog.push(row)
    }
    for (const fixture of cases) {
      const row = { ...fixture, hitAt5: false, citationErrors: [], resultDocumentIds: [], highestScore: 0, abstained: false }
      try {
        const answer = await api('/api/ai/knowledge/workspace/search', 'POST', { query: fixture.query,
          roleDirection: fixture.requestedRoleDirection, skill: fixture.requestedSkill, contentType: 'TOPIC', useAi: false })
        row.retrievalMode = answer.retrievalMode; row.generationMode = answer.generationMode; row.evidenceStatus = answer.evidenceStatus
        row.sourceCount = (answer.citations || []).length
        if (answer.generationMode !== 'RETRIEVAL_ONLY' || (answer.claims || []).length) row.citationErrors.push('UNEXPECTED_GENERATION')
        if (row.sourceCount > 5) row.citationErrors.push('MORE_THAN_FIVE_SOURCES')
        for (const citation of answer.citations || []) {
          report.remote.citationChecks++
          const document = await sourceDocument(citation.documentId)
          const failures = positionFailures(citation, document)
          const originalLocation = (document.locations || []).find(location => location.chunkId === citation.chunkId)
          if (!originalLocation || originalLocation.snippet !== citation.snippet || originalLocation.startOffset !== citation.startOffset || originalLocation.endOffset !== citation.endOffset) failures.push('CITATION_LOCATION_MISMATCH')
          if (citation.source !== document.source || citation.title !== document.title) failures.push('SOURCE_METADATA_MISMATCH')
          if (!(citation.roles || []).some(role => ['ALL', 'STUDENT'].includes(role))) failures.push('CITATION_ROLE_MISMATCH')
          const topic = topics.find(value => value.documentId === citation.documentId)
          if (topic && topic.role !== fixture.roleDirection) { failures.push('ROLE_FILTER_LEAK'); report.remote.roleLeaks++ }
          if (failures.length) report.remote.citationFailures++
          row.citationErrors.push(...failures)
          row.resultDocumentIds.push(citation.documentId)
          row.highestScore = Math.max(row.highestScore, citation.score || 0)
        }
        row.hitAt5 = fixture.expectedDocumentIds.some(id => row.resultDocumentIds.includes(id))
        row.abstained = answer.evidenceStatus === 'NO_EVIDENCE' && !row.sourceCount && !(answer.claims || []).length
        row.passed = !row.citationErrors.length && (fixture.kind === 'answerable' ? row.hitAt5 : row.abstained)
      } catch (error) { row.error = safeError(error); row.passed = false; report.remote.operationalErrors.push({ stage: 'query', id: fixture.id, error: row.error }) }
      report.remote.rows.push(row)
    }
  } catch (error) { report.remote.operationalErrors.push({ stage: 'startup', error: safeError(error) }) }
  finally {
    if (student?.token && !process.env.KNOWLEDGE_EVAL_TOKEN) {
      try {
        const history = await api('/api/ai/knowledge/me/history?limit=100')
        for (const item of history) await api('/api/ai/knowledge/me/history/' + encodeURIComponent(item.historyId), 'DELETE')
        report.remote.cleanup = 'DELETED_ONLY_HISTORY_OF_SYNTHETIC_EVALUATION_ACCOUNT'
      } catch { report.remote.cleanupFailures++; report.remote.cleanup = 'CLEANUP_FAILED_NO_PUBLIC_DATA_MODIFIED' }
    } else report.remote.cleanup = student?.token ? 'EXISTING_TOKEN_ACCOUNT_HISTORY_PRESERVED' : 'NOT_STARTED_NO_ACCOUNT'
  }
  function metrics(split) {
    const rows = report.remote.rows.filter(row => row.split === split)
    const positives = rows.filter(row => row.kind === 'answerable')
    const negatives = rows.filter(row => row.kind === 'unanswerable')
    return { queryCount: rows.length, answerableCount: positives.length, hitsAt5: positives.filter(row => row.hitAt5).length,
      recallAt5: positives.length ? positives.filter(row => row.hitAt5).length / positives.length : null,
      noAnswerCount: negatives.length, correctAbstentions: negatives.filter(row => row.abstained).length, target: 0.85 }
  }
  report.calibration = { ...report.calibration, metrics: metrics('calibration') }
  report.test = metrics('test')
  const catalogPassed = report.remote.catalog.length === 30 && report.remote.catalog.every(row => row.passed)
  report.validationPassed = catalogPassed && report.remote.rows.length === 30 && !report.remote.citationFailures && !report.remote.roleLeaks
    && !report.remote.operationalErrors.length && !report.remote.cleanupFailures && report.test.recallAt5 >= 0.85
    && report.remote.rows.filter(row => row.kind === 'unanswerable').every(row => row.abstained)
  report.status = report.validationPassed ? 'PASSED_PENDING_OWNER_REVIEW' : report.remote.operationalErrors.length ? 'FAILED_OPERATIONAL' : 'FAILED_ACCEPTANCE'
  return report
}

async function main() {
  let report
  try { report = await evaluate() }
  catch { report = { status: 'FAILED_OPERATIONAL', evaluatedAt: new Date().toISOString(), reviewStatus: 'OWNER_REVIEW_PENDING', error: 'UNEXPECTED_LOCAL_FAILURE_DETAILS_WITHHELD' } }
  fs.mkdirSync(path.dirname(outputFile), { recursive: true })
  fs.writeFileSync(outputFile, JSON.stringify(report, null, 2) + '\n', 'utf8')
  console.log('Evaluation report: ' + path.relative(root, outputFile))
  console.log(JSON.stringify({ status: report.status, local: report.local, test: report.test, citationChecks: report.remote?.citationChecks,
    citationFailures: report.remote?.citationFailures, operationalErrors: report.remote?.operationalErrors.length, reviewStatus: report.reviewStatus }))
  if (!['PASSED_PENDING_OWNER_REVIEW', 'OFFLINE_ONLY'].includes(report.status)) process.exitCode = 1
}

module.exports = { cases, positionFailures, validateLocal }
if (require.main === module) main()
