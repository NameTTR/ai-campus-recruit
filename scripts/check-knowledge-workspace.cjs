// Runs against the deployed stack. Saved verification data never contains tokens.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const base = (process.env.KNOWLEDGE_BASE_URL || 'http://localhost').replace(/\/+$/, '')
const password = process.env.MVP_SMOKE_PASSWORD || 'Verification123!'
const reportPath = path.resolve(__dirname, '../logs/knowledge-workspace-verification.json')
const checks = []
async function api(route, token, method = 'GET', body, reject = false) {
  const form = body instanceof FormData
  const response = await fetch(base + route, { method,
    headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(!form && body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? form ? body : JSON.stringify(body) : undefined, signal: AbortSignal.timeout(160000) })
  const payload = await response.json()
  assert.equal(typeof payload.code, 'number', route + ' must return ApiResponse')
  if (reject) { assert.ok(!response.ok || payload.code !== 0, 'Expected denial: ' + route); return payload }
  assert.ok(response.ok && payload.code === 0, method + ' ' + route + ': ' + payload.message)
  return payload.data
}
function check(name, condition, detail) { assert.ok(condition, name); checks.push({ name, passed: true, ...(detail || {}) }) }
function pdf(pages) {
  const objects = []
  objects[1] = '<< /Type /Catalog /Pages 2 0 R >>'
  objects[2] = '<< /Type /Pages /Kids [' + pages.map((_, i) => (3 + i * 2) + ' 0 R').join(' ') + '] /Count ' + pages.length + ' >>'
  pages.forEach((text, i) => {
    const id = 3 + i * 2
    objects[id] = '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 ' + (3 + pages.length * 2) + ' 0 R >> >> /Contents ' + (id + 1) + ' 0 R >>'
    const stream = 'BT /F1 10 Tf 20 760 Td (' + text.replace(/[()\\]/g, '\\$&') + ') Tj ET'
    objects[id + 1] = '<< /Length ' + Buffer.byteLength(stream) + ' >>\nstream\n' + stream + '\nendstream'
  })
  objects[3 + pages.length * 2] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'
  let content = '%PDF-1.4\n', offsets = [0]
  for (let i = 1; i < objects.length; i++) { offsets[i] = Buffer.byteLength(content); content += i + ' 0 obj\n' + objects[i] + '\nendobj\n' }
  const xref = Buffer.byteLength(content)
  content += 'xref\n0 ' + objects.length + '\n0000000000 65535 f \n' + offsets.slice(1).map(v => String(v).padStart(10, '0') + ' 00000 n \n').join('')
  return Buffer.from(content + 'trailer\n<< /Size ' + objects.length + ' /Root 1 0 R >>\nstartxref\n' + xref + '\n%%EOF')
}
async function ready(job, token) {
  for (let i = 0; i < 60; i++) {
    const all = await api('/api/ai/knowledge/ingestions?limit=200', token)
    const current = all.find(row => row.jobId === job.jobId)
    if (current && ['READY', 'FAILED', 'DUPLICATE'].includes(current.status)) return current
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error('Ingestion did not finish: ' + job.jobId)
}
async function restart() {
  const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
  const auth = await api('/api/auth/login', null, 'POST', { username: report.username, password })
  const items = await api('/api/ai/knowledge/me/items', auth.token)
  check('items survive restart', items.some(item => item.itemId === report.noteId && item.note.includes('account-owned')))
  const attempts = await api('/api/ai/knowledge/practices/' + report.practiceId + '/attempts', auth.token)
  check('practice answers and evaluation survive restart', attempts.some(row => row.attemptId === report.attemptId && row.status === report.attemptStatus))
  const history = await api('/api/ai/knowledge/me/history', auth.token)
  check('query history survives restart', history.length > 0)
  const plan = await api('/api/ai/learning/plans/' + report.planId, auth.token)
  check('confirmed learning action survives restart', plan.tasks.some(task => task.taskId === report.taskId))
  fs.writeFileSync(reportPath.replace('-verification.json', '-restart.json'), JSON.stringify({
    status: 'PASSED', generatedAt: new Date().toISOString(), checks
  }, null, 2))
  console.log('KNOWLEDGE_RESTART_PASS: ' + checks.length + ' checks')
}
async function realModel() {
  const saved = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
  const auth = await api('/api/auth/login', null, 'POST', { username: saved.username, password })
  const topics = await api('/api/ai/knowledge/topics', auth.token)
  const rows = []
  for (const direction of ['JAVA', 'FRONTEND', 'OPERATIONS']) {
    const topic = topics.find(row => row.role === direction)
    const answer = await api('/api/ai/knowledge/workspace/search', auth.token, 'POST', {
      query: topic.title, roleDirection: direction, useAi: true
    })
    const sources = new Map()
    for (const citation of answer.citations || []) {
      const source = sources.get(citation.documentId) || await api('/api/ai/knowledge/library/' + citation.documentId, auth.token)
      sources.set(citation.documentId, source)
    }
    const citationsValid = (answer.citations || []).length > 0 && answer.citations.every(citation => {
      const source = sources.get(citation.documentId)
      return source.version === citation.documentVersion && source.content.slice(citation.startOffset, citation.endOffset) === citation.snippet
    })
    const claimsValid = (answer.claims || []).length > 0 && answer.claims.every(claim => claim.citationIds.length > 0
      && claim.citationIds.every(id => answer.citations.some(c => c.chunkId === id && c.snippet.includes(claim.supportQuote))))
    rows.push({ direction, retrievalMode: answer.retrievalMode, generationMode: answer.generationMode,
      mocked: answer.mocked, citationsValid, claimsValid,
      passed: !answer.mocked && answer.retrievalMode.includes('RERANK') && citationsValid && claimsValid })
  }
  const passed = rows.every(row => row.passed)
  const output = reportPath.replace('-verification.json', '-real-model.json')
  fs.writeFileSync(output, JSON.stringify({ status: passed ? 'PASSED' : 'FAILED', generatedAt: new Date().toISOString(), rows }, null, 2))
  assert.ok(passed, 'Real model acceptance failed; fallback is recorded separately in ' + output)
  console.log('KNOWLEDGE_REAL_MODEL_PASS: ' + rows.length + ' roles')
}
async function main() {
  if (process.argv.includes('--verify-restart')) return restart()
  if (process.argv.includes('--verify-real-model')) return realModel()
  const stamp = Date.now().toString(36), username = 'knowledge_verify_' + stamp
  const register = name => api('/api/auth/register', null, 'POST', { username: name, password, displayName: '知识库验收', role: 'STUDENT' })
  const student = await register(username), other = await register('knowledge_other_' + stamp)
  const admin = await api('/api/auth/login', null, 'POST', { username: process.env.MVP_ADMIN_USER || 'admin', password: process.env.MVP_ADMIN_PASSWORD || '123456' })
  const token = student.token
  const topics = await api('/api/ai/knowledge/topics', token)
  for (const direction of ['JAVA', 'FRONTEND', 'OPERATIONS']) check(direction + ' has ten Chinese topics', topics.filter(row => row.role === direction).length >= 10)
  const topic = topics.find(row => row.role === 'JAVA' && row.skill === 'Redis') || topics.find(row => row.role === 'JAVA')
  const library = await api('/api/ai/knowledge/library/' + topic.documentId, token)
  check('full text citation positions locate original', library.locations.length > 0 && library.locations.every(row => library.content.slice(row.startOffset, row.endOffset) === row.snippet))
  check('markdown chunks contain usable source text', library.locations.every(row => row.snippet.replace(/[#\s]/g, '').length > 0))
  await api('/api/ai/knowledge/publications', token, 'POST', { title: 'forbidden', content: 'content' }, true)
  for (const direction of ['JAVA', 'FRONTEND', 'OPERATIONS']) {
    const selected = topics.find(row => row.role === direction)
    const answer = await api('/api/ai/knowledge/workspace/search', token, 'POST', { query: selected.title, roleDirection: direction, useAi: true })
    check(direction + ' real retrieval has cited evidence', answer.citations.length > 0 && answer.retrievalMode.includes('RERANK'), { retrievalMode: answer.retrievalMode, generationMode: answer.generationMode, mocked: answer.mocked })
    for (const citation of answer.citations) {
      const source = await api('/api/ai/knowledge/library/' + citation.documentId, token)
      check('citation matches source ' + citation.chunkId, source.content.includes(citation.snippet))
      check('citation records current document version ' + citation.chunkId, citation.documentVersion === source.version)
    }
  }
  await api('/api/ai/knowledge/workspace/search', token, 'POST', { query: ' ', useAi: true }, true)
  await api('/api/ai/knowledge/workspace/search', token, 'POST', { query: 'x'.repeat(2001) }, true)
  await api('/api/ai/knowledge/workspace/search', token, 'POST', { query: 'Redis', matchId: 'forged-owner-match' }, true)
  const noAnswer = await api('/api/ai/knowledge/workspace/search', token, 'POST', { query: '星际引擎未知技术ZX' + stamp, useAi: true })
  check('unanswerable query returns no fabricated evidence', noAnswer.citations.length === 0)
  const note = await api('/api/ai/knowledge/me/items', token, 'PUT', { topicId: topic.id, kind: 'NOTE', status: 'LEARNING', note: 'account-owned original note', expectedRevision: 0 })
  const revised = await api('/api/ai/knowledge/me/items', token, 'PUT', { topicId: topic.id, kind: 'NOTE', note: 'account-owned revised note', expectedRevision: note.revision })
  await api('/api/ai/knowledge/me/items', token, 'PUT', { topicId: topic.id, kind: 'NOTE', note: 'stale edit', expectedRevision: note.revision }, true)
  check('private note is isolated', (await api('/api/ai/knowledge/me/items', other.token)).every(row => row.itemId !== revised.itemId))
  await api('/api/ai/knowledge/me/items/' + revised.itemId, other.token, 'DELETE', undefined, true)
  const study = await api('/api/ai/knowledge/me/items', token, 'PUT', { topicId: topic.id, kind: 'STUDY', status: 'SELF_MASTERED', intervalDays: [1, 3, 7, 14], reviewEnabled: true, expectedRevision: 0 })
  let reviewed = await api('/api/ai/knowledge/me/items/' + study.itemId + '/review', token, 'POST', { passed: true })
  const gapDays = row => Math.round((new Date(row.nextReviewAt) - new Date(row.lastReviewedAt)) / 86400000)
  check('first successful review schedules three days', gapDays(reviewed) === 3)
  check('future review is not shown as currently overdue', reviewed.status === 'SELF_MASTERED')
  const firstReview = reviewed
  reviewed = await api('/api/ai/knowledge/me/items/' + study.itemId + '/review', token, 'POST', { passed: true })
  check('same-day review reuses existing schedule', reviewed.revision === firstReview.revision && reviewed.nextReviewAt === firstReview.nextReviewAt)
  reviewed = await api('/api/ai/knowledge/me/items/' + study.itemId + '/review', token, 'POST', { passed: false })
  check('same-day repeat does not change recorded outcome', reviewed.nextReviewAt === firstReview.nextReviewAt)
  const practice = await api('/api/ai/knowledge/practices', token, 'POST', { topicId: topic.id })
  check('short practice has two understanding and one application question', practice.questions.length === 3 && practice.questions.filter(q => q.type === 'UNDERSTANDING').length === 2)
  const request = { questionId: practice.questions[0].questionId, answer: topic.summary + ' 我会在课程练习中记录输入、处理过程和实际结果，并核对资料。' }
  const saved = await api('/api/ai/knowledge/practices/' + practice.practiceId + '/answers', token, 'POST', request)
  check('answer is saved before evaluation', saved.status === 'RECORDED')
  const duplicate = await api('/api/ai/knowledge/practices/' + practice.practiceId + '/answers', token, 'POST', request)
  check('duplicate answer reuses attempt', duplicate.attemptId === saved.attemptId)
  const concurrent = await Promise.all([api('/api/ai/knowledge/practices/' + practice.practiceId + '/answers', token, 'POST', request),
    api('/api/ai/knowledge/practices/' + practice.practiceId + '/answers', token, 'POST', request)])
  check('concurrent duplicate saves reuse one attempt', concurrent.every(row => row.attemptId === saved.attemptId))
  await api('/api/ai/knowledge/practices/' + practice.practiceId, other.token, 'GET', undefined, true)
  const evaluated = await api('/api/ai/knowledge/practices/' + practice.practiceId + '/attempts/' + saved.attemptId + '/evaluate', token, 'POST')
  check('real evaluation preserves answer', evaluated.answer === request.answer && evaluated.status === 'SUCCEEDED', { evaluation: evaluated.evaluationSnapshot })
  const duplicateEval = await api('/api/ai/knowledge/practices/' + practice.practiceId + '/attempts/' + saved.attemptId + '/evaluate', token, 'POST')
  check('successful evaluation is reused', duplicateEval.evaluatedAt === evaluated.evaluatedAt)
  const preview = await api('/api/ai/knowledge/actions/preview', token, 'POST', { type: 'LEARNING_PLAN', topicId: topic.id, weeklyHours: 2, durationWeeks: 1, dailyMinutesCap: 60 })
  const draft = await api('/api/ai/learning/plans/' + preview.payload.revisionId, token)
  check('action preview creates a draft only', draft.status === 'DRAFT')
  await api('/api/ai/knowledge/actions/confirm', other.token, 'POST', { previewId: preview.previewId }, true)
  const confirmed = await api('/api/ai/knowledge/actions/confirm', token, 'POST', { previewId: preview.previewId })
  let plan = await api('/api/ai/learning/plans/' + confirmed.payload.revisionId, token)
  check('action confirmation creates budget-valid active plan', plan.status === 'ACTIVE' && plan.tasks.reduce((sum, row) => sum + row.estimatedMinutes, 0) <= 120)
  const reConfirmed = await api('/api/ai/knowledge/actions/confirm', token, 'POST', { previewId: preview.previewId })
  check('action confirmation is idempotent', reConfirmed.payload.revisionId === confirmed.payload.revisionId)
  const originalTasks = plan.tasks.map(row => ({ taskId: row.taskId, minutes: row.estimatedMinutes }))
  const nextPreview = await api('/api/ai/knowledge/actions/preview', token, 'POST', { type: 'LEARNING_PLAN', topicId: topic.id, planId: plan.planId, durationWeeks: 2 })
  check('revision preview preserves current active plan', (await api('/api/ai/learning/plans/' + plan.planId, token)).status === 'ACTIVE')
  const nextConfirm = await api('/api/ai/knowledge/actions/confirm', token, 'POST', { previewId: nextPreview.previewId })
  plan = await api('/api/ai/learning/plans/' + nextConfirm.payload.revisionId, token)
  check('real plan revision retains existing tasks and minutes', originalTasks.every(row => plan.tasks.some(task => task.taskId === row.taskId && task.estimatedMinutes === row.minutes)))
  const dayMinutes = new Map(), weekMinutes = new Map()
  for (const task of plan.tasks) { dayMinutes.set(task.taskDate, (dayMinutes.get(task.taskDate) || 0) + task.estimatedMinutes); weekMinutes.set(task.week, (weekMinutes.get(task.week) || 0) + task.estimatedMinutes) }
  check('real plan revision meets each daily and weekly budget', [...dayMinutes.values()].every(value => value <= plan.dailyMinutesCap) && [...weekMinutes.values()].every(value => value <= plan.weeklyHours * 60))
  const pub = await api('/api/ai/knowledge/publications', admin.token, 'POST', { title: '权限测试' + stamp, content: '# 权限测试\n\nONLYAUTHORIZED' + stamp + ' 表示仅限授权用户阅读的测试段落。', source: '合成权限验收', category: 'verification', tags: [stamp], roles: ['STUDENT', 'ADMIN'] })
  check('new publication starts as draft', pub.status === 'DRAFT')
  await api('/api/ai/knowledge/library/' + pub.documentId, token, 'GET', undefined, true)
  await api('/api/ai/knowledge/publications/' + pub.documentId + '/publish', admin.token, 'POST')
  await api('/api/ai/knowledge/workspace/search', token, 'POST', { query: 'ONLYAUTHORIZED' + stamp, useAi: false })
  const recent = (await api('/api/ai/knowledge/me/history', token)).find(row => row.query.includes(stamp))
  assert.ok(recent)
  await api('/api/ai/knowledge/publications/' + pub.documentId + '/unpublish', admin.token, 'POST')
  const stale = await api('/api/ai/knowledge/me/history/' + recent.historyId, token)
  check('unpublished source is masked in historic answer', !stale.answerSnapshot || !JSON.stringify(stale.answerSnapshot).includes('ONLYAUTHORIZED' + stamp))
  const form = new FormData()
  const bytes = pdf(['PAGEONE-' + stamp + ' first source.', 'PAGETWO-' + stamp + ' second source.'])
  form.append('file', new Blob([bytes], { type: 'application/pdf' }), 'two-pages-' + stamp + '.pdf')
  form.append('title', 'PDF页码验收' + stamp); form.append('roles', 'STUDENT,ADMIN')
  const job = await ready(await api('/api/ai/knowledge/files', admin.token, 'POST', form), admin.token)
  check('PDF ingestion succeeds', job.status === 'READY')
  await api('/api/ai/knowledge/publications/' + job.documentId + '/publish', admin.token, 'POST')
  const pdfDocument = await api('/api/ai/knowledge/library/' + job.documentId, token)
  check('PDF maps both original pages', pdfDocument.pages.length === 2 && pdfDocument.content.slice(pdfDocument.pages[1].startOffset, pdfDocument.pages[1].endOffset).includes('PAGETWO'))
  const original = await fetch(base + '/api/ai/knowledge/library/' + job.documentId + '/original', { headers: { Authorization: 'Bearer ' + token, Range: 'bytes=0-9' } })
  check('authorized original supports byte range', original.status === 206 && (await original.arrayBuffer()).byteLength === 10)
  const outOfRange = await fetch(base + '/api/ai/knowledge/library/' + job.documentId + '/original', { headers: { Authorization: 'Bearer ' + token, Range: 'bytes=999999-' } })
  check('invalid byte range is rejected', outOfRange.status === 416)
  const anonymousFile = await fetch(base + '/api/ai/knowledge/library/' + job.documentId + '/original')
  check('original requires authenticated access', anonymousFile.status === 401)
  await api('/api/ai/knowledge/publications/' + job.documentId + '/unpublish', admin.token, 'POST')
  const hiddenFile = await fetch(base + '/api/ai/knowledge/library/' + job.documentId + '/original', { headers: { Authorization: 'Bearer ' + token } })
  check('unpublished original cannot be downloaded', hiddenFile.status !== 200)
  const report = { status: 'PASSED', generatedAt: new Date().toISOString(), username, noteId: revised.itemId, practiceId: practice.practiceId,
    attemptId: evaluated.attemptId, attemptStatus: evaluated.status, planId: plan.planId, taskId: plan.tasks[0].taskId, checks }
  fs.mkdirSync(path.dirname(reportPath), { recursive: true })
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2))
  console.log('KNOWLEDGE_WORKSPACE_PASS: ' + checks.length + ' checks; report ' + reportPath)
}
main().catch(error => {
  fs.mkdirSync(path.dirname(reportPath), { recursive: true })
  fs.writeFileSync(reportPath.replace('.json', '-failure.json'), JSON.stringify({ status: 'FAILED', generatedAt: new Date().toISOString(), checks, error: error.message }, null, 2))
  console.error(error.message); process.exitCode = 1
})
