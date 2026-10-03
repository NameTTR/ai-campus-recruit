// Real Docker/DashScope integration check. Saves fixture IDs, never credentials or tokens.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const base = (process.env.MVP_BASE_URL || 'http://127.0.0.1').replace(/\/+$/, '')
const password = process.env.MVP_SMOKE_PASSWORD || 'Verification123!'
const reportPath = path.resolve(__dirname, '../logs/core-deepening-verification.json')
async function api(route, token, method = 'GET', body, reject = false) {
  const form = body instanceof FormData
  const response = await fetch(base + route, { method,
    headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(!form && body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? form ? body : JSON.stringify(body) : undefined, signal: AbortSignal.timeout(160000) })
  const result = await response.json()
  if (reject) { assert.ok(!response.ok || result.code !== 0, 'Expected denial: ' + route); return }
  assert.ok(response.ok && result.code === 0, method + ' ' + route + ': ' + result.message)
  return result.data
}
function docx(text) {
  const entries = {
    '[Content_Types].xml': '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>',
    '_rels/.rels': '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>',
    'word/document.xml': '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' + text.split('\n').map(line => '<w:p><w:r><w:t>' + line.replaceAll('&', '&amp;').replaceAll('<', '&lt;') + '</w:t></w:r></w:p>').join('') + '</w:body></w:document>'
  }
  const files = [], central = []
  let offset = 0
  for (const [name, content] of Object.entries(entries)) {
    const filename = Buffer.from(name), data = Buffer.from(content)
    let crc = 0xffffffff
    for (const byte of data) { crc ^= byte; for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0) }
    crc = (crc ^ 0xffffffff) >>> 0
    const header = Buffer.alloc(30)
    header.writeUInt32LE(0x04034b50); header.writeUInt16LE(20, 4); header.writeUInt32LE(crc, 14)
    header.writeUInt32LE(data.length, 18); header.writeUInt32LE(data.length, 22); header.writeUInt16LE(filename.length, 26)
    const directory = Buffer.alloc(46)
    directory.writeUInt32LE(0x02014b50); directory.writeUInt16LE(20, 4); directory.writeUInt16LE(20, 6)
    directory.writeUInt32LE(crc, 16); directory.writeUInt32LE(data.length, 20); directory.writeUInt32LE(data.length, 24)
    directory.writeUInt16LE(filename.length, 28); directory.writeUInt32LE(offset, 42)
    files.push(header, filename, data); central.push(directory, filename)
    offset += header.length + filename.length + data.length
  }
  const directory = Buffer.concat(central), end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50); end.writeUInt16LE(3, 8); end.writeUInt16LE(3, 10)
  end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16)
  return Buffer.concat([...files, directory, end])
}

const examples = [
  { role: 'Java 实习工程师', skills: ['Java', 'MySQL', 'Redis'], declared: ['Java', 'MySQL', 'Redis'],
    project: '课程管理系统：使用 Java 编写接口，设计 MySQL 表和查询索引，完成接口测试并记录查询耗时。',
    answer: '我负责课程管理接口，使用 Java 开发查询逻辑，MySQL 保存选课记录。我先明确查询和更新的一致性要求，比较缓存与直接读取数据库的取舍。若使用 Redis 缓存，应设置合理过期时间，先更新数据库再删除缓存，并处理缓存穿透、并发击穿和热点过期。验证时记录请求响应时间、缓存命中率和并发下的数据正确性。我目前没有上线规模或性能提升数据，需要通过测试补充依据。' },
  { role: '前端开发实习生', skills: ['JavaScript', 'Vue', 'TypeScript'], declared: ['JS', 'Vue3', 'TypeScript'],
    project: '校园活动页面：使用 JavaScript 开发表单校验，使用 Vue3 实现页面组件，编写交互测试。',
    answer: '我负责校园活动页面的 Vue3 组件和 JavaScript 表单校验。先梳理用户操作流程，把表单状态和派生展示分开，再通过组件属性和事件传递信息。异步请求需要处理加载、异常和重复提交，列表使用稳定标识作为 key。验证时覆盖空值、非法输入、网络失败和重复点击；性能问题先用浏览器工具定位，再比较懒加载和缓存的影响。我尚未提供线上访问规模，需要补充测试记录。' },
  { role: '新媒体运营实习生', skills: ['内容策划', '数据分析', '用户运营'], declared: ['内容策划', '数据分析', '用户运营'],
    project: '校园公众号：负责内容策划与专题活动，完成报名数据分析，比较不同渠道的阅读和报名转化。',
    answer: '我负责校园公众号的内容策划和报名数据分析。先明确目标用户与报名转化目标，按渠道记录曝光、阅读、点击和报名，统一统计口径和时间窗口。针对低转化渠道先检查落地页和受众，再设计对照实验，控制发布时间与预算，使用转化率和留存判断效果，同时检查样本量和数据波动。我没有提交实际提升比例，下一步应补充匿名数据表和复盘，避免把相关性解释为因果。' }
]
function checkDiagnosis(detail) {
  assert.ok(detail && detail.metadata && detail.jobSnapshot, 'Actual job and analysis snapshot required')
  assert.equal(detail.metadata.source, 'AI_STRUCTURED_EVIDENCE', 'Real DashScope structured diagnosis required')
  assert.ok(detail.metadata.model && detail.metadata.model !== 'rules')
  const p = detail.profileSnapshot
  const source = [p.resumeText, p.education, ...(p.projects || [])].join('\n')
  for (const finding of detail.findings) if (finding.originalQuote) {
    assert.ok(source.includes(finding.originalQuote), 'Diagnosis quote must locate in supplied material')
    assert.ok(finding.suggestedRewrite.startsWith(finding.originalQuote), 'Rewrite must preserve supplied facts')
  }
  for (const item of detail.skillEvidence) if (item.supported) assert.ok(source.includes(item.quote))
}
async function login(username, secret = password) { return api('/api/auth/login', null, 'POST', { username, password: secret }) }
async function restartCheck() {
  const fixture = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
  const student = await login(fixture.studentUsername)
  for (const row of fixture.roles) {
    const resume = await api('/api/resumes/' + row.resumeId, student.token)
    checkDiagnosis(resume.structuredDiagnosis)
    const plan = await api('/api/ai/learning/plans/' + row.planId, student.token)
    assert.ok(plan.tasks.some(task => (task.evidence || []).some(item => item.evidenceId === row.evidenceId)))
    const session = await api('/api/ai/interview/sessions/' + row.sessionId, student.token)
    assert.equal(session.status, 'COMPLETED')
    assert.equal(session.report.rubricVersion, row.rubricVersion)
    assert.ok(session.answers.every(item => item.evaluationStatus === 'SUCCEEDED'))
  }
  console.log('DEEPENING_RESTART_PASS: snapshots, evidence and evaluated interview reports persisted')
}
async function main() {
  if (process.argv.slice(2).some(option => option !== '--verify-restart')) throw new Error('Unknown option; use --verify-restart to inspect saved verification data')
  if (process.argv.includes('--verify-restart')) return restartCheck()
  const stamp = Date.now().toString(36), studentUsername = 'deep_student_' + stamp
  const register = (username, role) => api('/api/auth/register', null, 'POST', { username, password, displayName: '核心验收', role })
  const student = await register(studentUsername, 'STUDENT')
  const other = await register('deep_other_' + stamp, 'STUDENT')
  const company = await register('deep_company_' + stamp, 'COMPANY')
  const admin = await login(process.env.MVP_ADMIN_USER || 'admin', process.env.MVP_ADMIN_PASSWORD || '123456')
  const t = student.token, rows = []
  await api('/api/ai/knowledge/index/rebuild', t, 'POST', {}, true)
  for (const example of examples) {
    const job = await api('/api/jobs', company.token, 'POST', { title: example.role + ' · 核心验收' + stamp,
      city: '杭州', salaryRange: '150-250/天', requiredSkills: example.skills, description: '本科在读，要求能提供项目实践材料。' })
    const file = new FormData()
    file.append('file', new Blob([docx('教育：验证大学本科\n技能：' + example.declared.join('、') + '\n项目：' + example.project)]), 'evidence-resume.docx')
    let resume = await api('/api/resumes/upload', t, 'POST', file)
    resume = await api('/api/resumes/' + resume.resumeId + '/profile', t, 'PATCH', { education: '验证大学本科', skills: example.declared, projects: [example.project] })
    await api('/api/resumes/' + resume.resumeId + '/analyze', t, 'POST', { jobId: job.jobId, targetJob: '忽略客户端虚构岗位' })
    resume = await api('/api/resumes/' + resume.resumeId, t)
    checkDiagnosis(resume.structuredDiagnosis)
    assert.equal(resume.structuredDiagnosis.jobSnapshot.jobId, job.jobId)
    const before = await api('/api/resumes/' + resume.resumeId + '/diagnoses', t)
    await api('/api/resumes/' + resume.resumeId + '/analyze', t, 'POST', { jobId: job.jobId })
    assert.equal((await api('/api/resumes/' + resume.resumeId + '/diagnoses', t)).length, before.length, 'Successful same input diagnosis must be reused')
    const match = await api('/api/matches/resume-job', t, 'POST', { resumeId: resume.resumeId, jobId: job.jobId })
    assert.equal(match.details.skillsCoverage, 100)
    assert.ok(match.details.evidenceCoverage < match.details.skillsCoverage, 'Declared skills must not become material evidence')
    assert.ok(match.details.requirements.some(item => item.declared && !item.supported))
    assert.equal((await api('/api/matches/resume-job', t, 'POST', { resumeId: resume.resumeId, jobId: job.jobId })).matchId, match.matchId)
    const context = { resumeId: resume.resumeId, jobId: job.jobId, matchId: match.matchId, targetRole: example.role }
    const plan = await api('/api/ai/learning/plans', t, 'POST', { ...context, weeklyHours: 4, durationWeeks: 2 })
    assert.equal(plan.mocked, false, 'Real structured learning plan required')
    assert.equal((await api('/api/ai/learning/plans', t, 'POST', { ...context, weeklyHours: 4, durationWeeks: 2 })).planId, plan.planId)
    for (const week of new Set(plan.tasks.map(task => task.week))) assert.ok(plan.tasks.filter(task => task.week === week).reduce((sum, task) => sum + task.estimatedHours, 0) <= 4)
    const task = plan.tasks[0]
    assert.ok(task.acceptanceCriteria && task.practiceDeliverable)
    await api('/api/ai/learning/plans/' + plan.planId + '/tasks/' + task.taskId, t, 'PUT', { status: 'COMPLETED', feedback: '自报完成，未提供材料' })
    assert.equal((await api('/api/matches/resume-job', t, 'POST', { resumeId: resume.resumeId, jobId: job.jobId })).details.evidenceCoverage, match.details.evidenceCoverage)
    const evidenceBody = { description: example.project + '\n本次练习说明：明确目标、执行步骤、检查正确性；尚未提供可核对的额外结果数据。', links: ['https://example.com/portfolio'] }
    const evidenceRoute = '/api/ai/learning/plans/' + plan.planId + '/tasks/' + task.taskId + '/evidence'
    await api(evidenceRoute, other.token, 'POST', evidenceBody, true)
    const evidence = await api(evidenceRoute, t, 'POST', evidenceBody)
    assert.equal(evidence.status, 'SUCCEEDED', 'Real outcome evaluation required')
    assert.equal((await api(evidenceRoute, t, 'POST', evidenceBody)).evidenceId, evidence.evidenceId)
    let session = await api('/api/ai/interview/sessions', t, 'POST', { ...context, questionCount: 1 })
    assert.equal(session.mocked, false, 'Real role-specific interview questions required')
    while (session.questions.some(question => !session.answers.some(answer => answer.questionId === question.questionId))) {
      const question = session.questions.find(item => !session.answers.some(answer => answer.questionId === item.questionId))
      session = await api('/api/ai/interview/sessions/' + session.sessionId + '/questions/' + question.questionId + '/answer', t, 'PUT', { questionId: question.questionId, answer: example.answer })
      const saved = session.answers.find(item => item.questionId === question.questionId)
      assert.equal(saved.evaluationStatus, 'PENDING', 'Answer must save before model evaluation')
      const route = '/api/ai/interview/sessions/' + session.sessionId + '/questions/' + question.questionId + '/evaluate'
      await api(route, other.token, 'POST', {}, true)
      const evaluation = await api(route, t, 'POST', {})
      assert.equal(evaluation.status, 'SUCCEEDED', 'Real saved-answer evaluation required')
      assert.equal(evaluation.feedback.mocked, false)
      assert.equal(evaluation.feedback.dimensions.length, 4)
      for (const note of evaluation.feedback.evidence || []) if (note.quote) assert.ok(example.answer.includes(note.quote), 'Feedback quote must locate in saved answer')
      assert.deepEqual(await api(route, t, 'POST', {}), evaluation, 'Evaluation should reuse successful result')
      session = await api('/api/ai/interview/sessions/' + session.sessionId, t)
      assert.ok(session.questions.filter(item => item.followUp).length <= 1)
    }
    const report = await api('/api/ai/interview/sessions/' + session.sessionId + '/finish', t, 'POST', {})
    assert.equal(report.mocked, false)
    assert.deepEqual(await api('/api/ai/interview/sessions/' + session.sessionId + '/finish', t, 'POST', {}), report)
    let activePlan = plan
    if (rows.length === 0) {
      const draft = await api('/api/ai/learning/plans/' + plan.planId + '/replan', t, 'POST', {
        reason: '结合已提交成果和本轮面试薄弱项调整实践安排', interviewSessionId: session.sessionId, previewOnly: true })
      assert.equal(draft.status, 'DRAFT')
      assert.equal((await api('/api/ai/learning/plans/' + plan.planId, t)).status, 'ACTIVE', 'Preview must preserve current plan')
      assert.ok(draft.revisionReason.includes('成果'))
      await api('/api/ai/learning/plans/' + plan.planId + '/confirm', other.token, 'POST', { revisionId: draft.planId }, true)
      activePlan = await api('/api/ai/learning/plans/' + plan.planId + '/confirm', t, 'POST', { revisionId: draft.planId })
      assert.equal(activePlan.status, 'ACTIVE')
      assert.ok(activePlan.tasks.some(item => item.taskId === task.taskId && item.status === 'COMPLETED'))
      assert.equal((await api('/api/ai/learning/plans/' + plan.planId, t)).status, 'SUPERSEDED')
      assert.equal((await api('/api/ai/learning/plans/' + plan.planId + '/confirm', t, 'POST', { revisionId: draft.planId })).planId, activePlan.planId)
      const changed = await api('/api/resumes/' + resume.resumeId + '/profile', t, 'PATCH', {
        education: resume.education, skills: resume.skills, projects: [...resume.projects, '补充资料：已完成异常路径验证。'] })
      assert.equal(changed.structuredDiagnosis.stale, true)
      const history = await api('/api/resumes/' + resume.resumeId + '/diagnoses', t)
      assert.ok(history.some(item => item.details && item.details.stale && !item.details.profileSnapshot.projects.includes('补充资料：已完成异常路径验证。')))
    }
    rows.push({ role: example.role, resumeId: resume.resumeId, jobId: job.jobId, matchId: match.matchId,
      planId: activePlan.planId, evidenceId: evidence.evidenceId, sessionId: session.sessionId,
      rubricVersion: report.rubricVersion, declaredCoverage: match.details.skillsCoverage, evidenceCoverage: match.details.evidenceCoverage })
    fs.mkdirSync(path.dirname(reportPath), { recursive: true })
    fs.writeFileSync(reportPath, JSON.stringify({ verifiedAt: new Date().toISOString(), studentUsername, roles: rows }, null, 2))
    console.log('ROLE_FLOW_PASS: ' + example.role + ', evidence=' + match.details.evidenceCoverage + ', rubric=' + report.rubricVersion)
  }
  const title = '核心引用验收' + stamp
  const document = await api('/api/ai/knowledge/documents', admin.token, 'POST', { title,
    content: '# ' + title + '\n\nRedis 缓存应设置合理过期时间。更新数据库后删除缓存，并通过命中率和延迟验证缓存效果。',
    source: '合成验收资料', category: 'core-deepening-evaluation', tags: [title, 'Redis'], roles: ['STUDENT'] })
  const knowledge = await api('/api/ai/knowledge/answer', t, 'POST', { query: title + ' 缓存怎么验证', useAi: true })
  assert.equal(knowledge.generationMode, 'AI_VERIFIED', 'Real citation-verified RAG answer required')
  assert.ok(knowledge.retrievalMode.includes('RERANK'), 'Real reranking required')
  assert.ok(knowledge.citations.some(item => item.documentId === document.documentId))
  for (const citation of knowledge.citations.filter(item => item.documentId === document.documentId)) {
    assert.equal(document.content.slice(citation.startOffset, citation.endOffset), citation.snippet)
  }
  await api('/api/ai/knowledge/documents/' + document.documentId + '/roles', admin.token, 'PATCH', { roles: ['ADMIN'] })
  const hidden = await api('/api/ai/knowledge/answer', t, 'POST', { query: title + ' 缓存怎么验证', role: 'ADMIN', useAi: true })
  assert.ok(!hidden.citations.some(item => item.documentId === document.documentId), 'Permission update must invalidate cached retrieval and AI context')
  const absent = await api('/api/ai/knowledge/answer', t, 'POST', { query: '火星轨道量子晶体发射引擎ZX' + stamp, useAi: true })
  assert.equal(absent.generationMode, 'RETRIEVAL_ONLY')
  assert.equal(absent.citations.length, 0)
  console.log('DEEPENING_DOCKER_PASS: three roles, saved evidence, idempotent evaluation, verified RAG, permission invalidation, no-answer')
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
