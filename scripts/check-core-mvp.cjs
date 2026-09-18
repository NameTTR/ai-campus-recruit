// Local integration verification. Creates isolated sample accounts/content; never writes tokens to disk.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const base = process.env.MVP_BASE_URL || 'http://127.0.0.1:8080'
const password = process.env.MVP_SMOKE_PASSWORD || 'Verification123!'
const reportPath = path.resolve(__dirname, '../logs/core-mvp-verification.json')

async function api(route, token, method = 'GET', body, reject = false) {
  const form = body instanceof FormData
  const response = await fetch(base + route, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(!form && body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? form ? body : JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(150000)
  })
  const result = await response.json()
  if (reject) {
    assert.ok(!response.ok || result.code !== 0, `Expected rejection: ${method} ${route}`)
    return
  }
  assert.ok(response.ok && result.code === 0, `${method} ${route}: ${response.status} ${result.message}`)
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

async function main() {
  if (process.argv.includes('--verify-diagnosis')) {
    const data = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
    const student = await api('/api/auth/login', null, 'POST', { username: data.studentUsername, password })
    const job = await api(`/api/jobs/${data.jobId}`, student.token)
    if (!process.argv.includes('--read-only')) {
      await api(`/api/resumes/${data.resumeId}/analyze`, student.token, 'POST', { targetJob: job.title })
    }
    const diagnoses = await api(`/api/resumes/${data.resumeId}/diagnoses`, student.token)
    assert.ok(diagnoses.length >= 2)
    const latest = [...diagnoses].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))[0]
    assert.notEqual(latest.source, 'RULE_FALLBACK', 'Expected a real AI diagnosis with the configured provider')
    data.diagnosisSource = latest.source
    fs.writeFileSync(reportPath, JSON.stringify(data, null, 2))
    console.log(`AI_DIAGNOSIS_PASS: source=${latest.source}, history=${diagnoses.length}`)
    return
  }
  if (process.argv.includes('--verify-restart')) {
    const data = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
    const student = await api('/api/auth/login', null, 'POST', { username: data.studentUsername, password })
    const t = student.token
    assert.equal((await api(`/api/resumes/${data.resumeId}`, t)).resumeId, data.resumeId)
    assert.ok((await api(`/api/resumes/${data.resumeId}/diagnoses`, t)).length > 0)
    const plan = await api(`/api/ai/learning/plans/${data.planId}`, t)
    assert.ok(plan.tasks.some(task => task.status === 'COMPLETED' && task.feedback === 'MVP-已完成并保存'))
    const session = await api(`/api/ai/interview/sessions/${data.sessionId}`, t)
    assert.equal(session.status, 'COMPLETED')
    assert.ok(session.report)
    const profile = await api('/api/students/profile', t)
    assert.equal(profile.major, '软件工程（持久化验证）')
    assert.ok((await api('/api/matches', t)).some(match => match.matchId === data.matchId && match.score === 67))
    assert.equal((await api(`/api/jobs/${data.jobId}`, t)).status, 'OPEN')
    const admin = await api('/api/auth/login', null, 'POST', { username: process.env.MVP_ADMIN_USER || 'admin', password: process.env.MVP_ADMIN_PASSWORD || '123456' })
    const documents = await api('/api/ai/knowledge/documents?limit=200', admin.token)
    assert.deepEqual(documents.find(document => document.documentId === data.documentId)?.roles, ['ADMIN'])
    const ingestions = await api('/api/ai/knowledge/ingestions?limit=200', admin.token)
    assert.equal(ingestions.find(job => job.jobId === data.ingestionId)?.status, 'READY')
    console.log('RESTART_VERIFICATION_PASS: account, profile, resume diagnosis, jobs, matches, learning progress, interview report, knowledge permissions and ingestion')
    return
  }
  const stamp = Date.now().toString(36)
  const register = (suffix, role) => api('/api/auth/register', null, 'POST', { username: `mvp_${suffix}_${stamp}`, password, displayName: `MVP验证${suffix}`, role })
  const student = await register('student', 'STUDENT'), other = await register('other', 'STUDENT'), company = await register('company', 'COMPANY')
  const t = student.token
  await api('/api/students/profile', t, 'PUT', { displayName: 'MVP学生', school: '验证大学', major: '软件工程（持久化验证）', skills: ['Java', 'MySQL'], targetPosition: 'Java 实习工程师' })
  const job = await api('/api/jobs', company.token, 'POST', { title: 'MVP Java 实习工程师', city: '杭州', salaryRange: '180-260/天', requiredSkills: ['Java', 'MySQL', 'Redis'], description: '参与后端接口开发，使用 Java、MySQL、Redis，能说明测试方式。' })
  const upload = new FormData()
  upload.append('file', new Blob([docx('MVP学生个人简历\n教育背景：验证大学，软件工程，本科。\n技能：Java、MySQL、软件测试。\n项目：课程管理系统。使用 Java 编写 6 个接口，设计 MySQL 表并为查询添加索引。\n成果：完成 18 项接口测试，记录查询耗时从 120 毫秒降到 40 毫秒。\n经历：协作 3 人完成课程项目，负责接口设计和数据库验证。')]), 'mvp-resume.docx')
  let resume = await api('/api/resumes/upload', t, 'POST', upload)
  resume = await api(`/api/resumes/${resume.resumeId}/profile`, t, 'PATCH', { education: '软件工程本科', skills: ['Java', 'MySQL'], projects: ['课程管理系统：6个接口和18项测试'] })
  await api(`/api/resumes/${resume.resumeId}`, other.token, 'GET', undefined, true)
  await api(`/api/resumes/${resume.resumeId}/analyze`, t, 'POST', { targetJob: job.title })
  const diagnoses = await api(`/api/resumes/${resume.resumeId}/diagnoses`, t)
  assert.ok(diagnoses.length > 0)
  assert.deepEqual(diagnoses[0].skillsSnapshot, ['Java', 'MySQL'])
  const match = await api('/api/matches/resume-job', t, 'POST', { resumeId: resume.resumeId, jobId: job.jobId })
  assert.equal(match.score, 67)
  assert.ok(match.missingSkills.includes('Redis'))
  console.log(`RESUME_MATCH_PASS: diagnosis=${diagnoses[0].source}, skillCoverage=${match.score}`)
  for (const example of [
    { title: '前端开发实习生', skills: ['JavaScript', 'Vue'], required: ['JavaScript', 'Vue', 'TypeScript'], project: '校园活动页面：组件开发、表单校验和交互测试' },
    { title: '新媒体运营实习生', skills: ['内容策划', '数据分析'], required: ['内容策划', '数据分析', '用户运营'], project: '校园公众号：策划 4 次专题活动，统计阅读与报名转化' }
  ]) {
    const extraJob = await api('/api/jobs', company.token, 'POST', { title: `MVP ${example.title}`, city: '杭州', salaryRange: '150-200/天', requiredSkills: example.required, description: example.project })
    const extraFile = new FormData()
    extraFile.append('file', new Blob([docx(`教育：验证大学本科\n技能：${example.skills.join('、')}\n项目：${example.project}`)]), 'mvp-profession.docx')
    const extraResume = await api('/api/resumes/upload', t, 'POST', extraFile)
    assert.ok(!extraResume.skills.includes('Java'), `Unexpected Java default for ${example.title}`)
    await api(`/api/resumes/${extraResume.resumeId}/profile`, t, 'PATCH', { education: '验证大学本科', skills: example.skills, projects: [example.project] })
    const extraMatch = await api('/api/matches/resume-job', t, 'POST', { resumeId: extraResume.resumeId, jobId: extraJob.jobId })
    assert.equal(extraMatch.score, 67)
    assert.deepEqual(extraMatch.missingSkills, [example.required[2]])
  }
  console.log('PROFESSION_PASS: frontend and nontechnical roles use their own skill evidence')
  await api('/api/matches/resume-job', other.token, 'POST', { resumeId: resume.resumeId, jobId: job.jobId }, true)
  await api(`/api/jobs/${job.jobId}/status`, company.token, 'POST', { status: 'CLOSED' })
  assert.ok(!(await api('/api/jobs', t)).some(item => item.jobId === job.jobId))
  await api(`/api/jobs/${job.jobId}/status`, company.token, 'POST', { status: 'OPEN' })
  const context = { resumeId: resume.resumeId, jobId: job.jobId, matchId: match.matchId, targetRole: job.title }
  const plan = await api('/api/ai/learning/plans', t, 'POST', { ...context, durationWeeks: 2, weeklyHours: 4 })
  for (const week of new Set(plan.tasks.map(task => task.week))) {
    assert.ok(plan.tasks.filter(task => task.week === week).reduce((sum, task) => sum + task.estimatedHours, 0) <= 4)
  }
  await api(`/api/ai/learning/plans/${plan.planId}/tasks/${plan.tasks[0].taskId}`, t, 'PUT', { status: 'COMPLETED', feedback: 'MVP-已完成并保存' })
  await api(`/api/ai/learning/plans/${plan.planId}`, other.token, 'GET', undefined, true)
  console.log(`LEARNING_PLAN_PASS: mocked=${plan.mocked}, tasks=${plan.tasks.length}`)
  let session = await api('/api/ai/interview/sessions', t, 'POST', { ...context, questionCount: 1 })
  await api(`/api/ai/interview/sessions/${session.sessionId}`, other.token, 'GET', undefined, true)
  for (let turn = 0; turn < 3; turn++) {
    const question = [...session.questions].sort((a, b) => a.order - b.order).find(q => !session.answers.some(a => a.questionId === q.questionId))
    if (!question) break
    assert.equal(question.referencePoints?.length || 0, 0)
    const answer = '在课程管理系统中，我先明确接口输入和错误响应，用 Java 实现功能，使用 MySQL 索引优化查询，并通过 18 项测试验证结果。查询耗时从 120 毫秒降低到 40 毫秒，选择索引时也考虑了写入成本。'
    session = await api(`/api/ai/interview/sessions/${session.sessionId}/questions/${question.questionId}/answer`, t, 'PUT', { questionId: question.questionId, answer })
    await api(`/api/ai/interview/sessions/${session.sessionId}/questions/${question.questionId}/answer`, t, 'PUT', { questionId: question.questionId, answer: '不同的重复回答' }, true)
  }
  const report = await api(`/api/ai/interview/sessions/${session.sessionId}/finish`, t, 'POST', {})
  assert.ok(report.questionFeedback.length >= 1)
  console.log(`INTERVIEW_PASS: mocked=${report.mocked}, answers=${session.answers.length}`)
  const revised = await api(`/api/ai/learning/plans/${plan.planId}/replan`, t, 'POST', { reason: '根据模拟面试暴露的短板加强实践', interviewSessionId: session.sessionId })
  const kept = revised.tasks.find(task => task.taskId === plan.tasks[0].taskId)
  assert.equal(kept?.status, 'COMPLETED')
  assert.equal(kept.feedback, 'MVP-已完成并保存')
  assert.equal((await api(`/api/ai/learning/plans/${revised.planId}/versions`, t)).length, 2)
  const admin = await api('/api/auth/login', null, 'POST', { username: process.env.MVP_ADMIN_USER || 'admin', password: process.env.MVP_ADMIN_PASSWORD || '123456' })
  const title = `MVP知识验证${stamp}`
  const document = await api('/api/ai/knowledge/documents', admin.token, 'POST', { title, content: `${title}：Redis 缓存应设置合理过期时间，更新数据库后删除缓存；验证缓存命中率并记录延迟变化。`, category: 'mvp-validation', source: 'MVP验证资料', roles: ['STUDENT'], tags: [title, 'Redis'] })
  let search = await api('/api/ai/knowledge/search', t, 'POST', { query: title, limit: 5 })
  assert.ok(search.results.length > 0)
  const answer = await api('/api/ai/knowledge/answer', t, 'POST', { query: title, limit: 5, useAi: true })
  assert.ok(answer.citations.length > 0)
  assert.ok(answer.citations.some(citation => citation.documentId === document.documentId))
  await api(`/api/ai/knowledge/documents/${document.documentId}/roles`, admin.token, 'PATCH', { roles: ['ADMIN'] })
  search = await api('/api/ai/knowledge/search', t, 'POST', { query: title, role: 'ADMIN', limit: 5 })
  assert.ok(!search.results.some(item => item.title === title || item.documentId === document.documentId))
  const knowledgeFile = new FormData()
  knowledgeFile.append('file', new Blob([`# 文件导入验证 ${stamp}\n\n校园招聘准备需要记录作品、实践反馈和可验证的学习成果。`], { type: 'text/markdown' }), 'mvp-knowledge.md')
  knowledgeFile.append('roles', 'STUDENT')
  knowledgeFile.append('title', `MVP文件导入${stamp}`)
  const ingestion = await api('/api/ai/knowledge/files', admin.token, 'POST', knowledgeFile)
  let ready
  for (let attempt = 0; attempt < 30; attempt++) {
    ready = (await api('/api/ai/knowledge/ingestions?limit=200', admin.token)).find(item => item.jobId === ingestion.jobId)
    if (ready?.status === 'READY' || ready?.status === 'FAILED') break
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  assert.equal(ready?.status, 'READY', ready?.message)
  console.log(`RAG_PASS: provider=${answer.provider}, mocked=${answer.mocked}, citations, role changes and file ingestion`)
  fs.mkdirSync(path.dirname(reportPath), { recursive: true })
  fs.writeFileSync(reportPath, JSON.stringify({ verifiedAt: new Date().toISOString(), studentUsername: `mvp_student_${stamp}`, resumeId: resume.resumeId, jobId: job.jobId, matchId: match.matchId, planId: revised.planId, sessionId: session.sessionId, documentId: document.documentId, ingestionId: ingestion.jobId, diagnosisSource: diagnoses[0].source, learningMocked: plan.mocked, interviewMocked: report.mocked, ragMocked: answer.mocked, ragProvider: answer.provider }, null, 2))
  console.log('CORE_MVP_PASS: verification metadata saved without tokens')
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
