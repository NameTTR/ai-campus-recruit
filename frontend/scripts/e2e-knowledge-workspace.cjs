const { spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '../..')
const runId = new Date().toISOString().replace(/[:.]/g, '-')
const artifacts = path.resolve(process.env.E2E_KNOWLEDGE_ARTIFACTS_DIR || path.join(root, 'output/playwright/knowledge-workspace', runId))
const config = { baseUrl: (process.env.E2E_KNOWLEDGE_BASE_URL || 'http://localhost').replace(/\/+$/, ''), apiUrl: (process.env.E2E_KNOWLEDGE_API_URL || 'http://localhost:18080').replace(/\/+$/, ''), artifacts, runId, adminUsername: process.env.E2E_ADMIN_USERNAME || 'admin', adminPassword: process.env.E2E_ADMIN_PASSWORD || '123456' }
const session = `knowledge-${Date.now().toString(36)}`
const runner = path.join(artifacts, 'browser-run.js')
fs.mkdirSync(artifacts, { recursive: true })
function cli(args) {
  const result = spawnSync(process.platform === 'win32' ? 'npx.cmd' : 'npx', ['--yes', '--package', '@playwright/cli', 'playwright-cli', `-s=${session}`, ...args], { cwd: root, encoding: 'utf8', maxBuffer: 24 * 1024 * 1024, env: process.env, shell: process.platform === 'win32' })
  if (result.error || result.status !== 0) throw result.error || new Error(result.stderr || `Browser failed: ${result.status}`)
  return result.stdout
}
function pdfFixture(pages) {
  const objects = []
  objects[1] = '<< /Type /Catalog /Pages 2 0 R >>'
  objects[2] = `<< /Type /Pages /Kids [${pages.map((_, index) => `${3 + index * 2} 0 R`).join(' ')}] /Count ${pages.length} >>`
  pages.forEach((text, index) => {
    const id = 3 + index * 2
    objects[id] = `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 ${3 + pages.length * 2} 0 R >> >> /Contents ${id + 1} 0 R >>`
    const stream = `BT /F1 28 Tf 30 700 Td (${text.replace(/[()\\]/g, '\\$&')}) Tj ET`
    objects[id + 1] = `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`
  })
  objects[3 + pages.length * 2] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'
  let content = '%PDF-1.4\n'; const offsets = []
  for (let index = 1; index < objects.length; index++) { offsets.push(Buffer.byteLength(content)); content += `${index} 0 obj\n${objects[index]}\nendobj\n` }
  const xref = Buffer.byteLength(content)
  content += `xref\n0 ${objects.length}\n0000000000 65535 f \n${offsets.map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}`
  return Buffer.from(`${content}trailer\n<< /Size ${objects.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`)
}
config.pdfBase64 = pdfFixture([`PDF first page ${runId}.`, `PDF second page ${runId}.`]).toString('base64')

async function browserSuite(page, config) {
  const checks = []; const screenshots = []; const errors = []
  const suffix = Date.now().toString(36); const username = `knowledge_e2e_${suffix}`; const password = `KnowledgeE2e!${suffix}`
  let auth; let currentTopic; let currentPractice; let planId
  const check = (name, value, detail = {}) => { if (!value) throw new Error(`Acceptance failed: ${name}`); checks.push({ name, passed: true, ...detail }) }
    const api = async (route, method = 'GET', data, token = auth?.token, allowFailure = false) => {
    const response = await page.request.fetch(config.apiUrl + route, { method, data, timeout: 150000, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) } })
    const body = await response.json()
    if (allowFailure) return { status: response.status(), ...body }
    if (!response.ok() || body.code !== 0) throw new Error(`${route}: ${response.status()} ${body.message}`)
    return body.data
  }
  const shot = async name => { const file = `${config.artifacts}/${name}.png`; await page.screenshot({ path: file, fullPage: true }); screenshots.push(file) }
  const writeAndWait = async (control, fragment) => {
    const responsePromise = page.waitForResponse(response => response.url().includes(fragment) && ['POST', 'PUT', 'DELETE'].includes(response.request().method()), { timeout: 150000 })
    await control.click()
    const response = await responsePromise; const body = await response.json()
    if (!response.ok() || body.code !== 0) throw new Error(`${fragment}: ${response.status()} ${body.message}`)
    return body.data
  }
  const ready = async id => { await page.getByTestId(id).waitFor(); await page.waitForFunction(() => document.querySelector('[data-testid="knowledge-workspace"]')?.getAttribute('aria-busy') !== 'true') }
  const input = id => page.locator(`input[data-testid="${id}"], textarea[data-testid="${id}"], [data-testid="${id}"] input, [data-testid="${id}"] textarea`)
  const layout = async name => {
    const geometry = await page.evaluate(() => {
      const candidates = [...new Set([document.scrollingElement, ...document.querySelectorAll('*')])].filter(element => element?.getClientRects().length && element.clientHeight > 0 && element.scrollHeight > element.clientHeight + 2 && (element === document.scrollingElement || /auto|scroll/.test(getComputedStyle(element).overflowY)))
      for (const element of candidates) element.scrollTop = element.scrollHeight
      const main = document.querySelector('[data-testid="knowledge-workspace"]')
      const last = main?.querySelector(':scope > section:not([style*="display: none"]):last-of-type')
      const rect = last?.getBoundingClientRect()
      return { viewport: innerWidth, width: document.documentElement.scrollWidth, endBottom: rect?.bottom, reachable: !rect || rect.bottom <= innerHeight + 3, scrollableContainers: candidates.length }
    })
    check(`${name} has no horizontal overflow`, geometry.width <= geometry.viewport + 2, geometry)
    check(`${name} bottom content is reachable`, geometry.reachable, geometry)
  }
  const answerText = '我会先说明适用条件，再比较两种方法的代价，用原文依据核对结论，并设计正常、异常和边界用例。实际结果需要运行验证后记录，我没有测量的数据不会作为已实现的成果。'
  page.setDefaultTimeout(90000)
  page.on('pageerror', error => errors.push(error.message))
  try {
    await page.goto(config.baseUrl + '/login')
    auth = await api('/api/auth/register', 'POST', { username, password, displayName: '知识库自动化测试', role: 'STUDENT' }, null)
    await page.locator('input[autocomplete="username"]').fill(username)
    await page.locator('input[type="password"]').fill(password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/student/resume*')
    await page.goto(config.baseUrl + '/student/knowledge')
    await ready('knowledge-search')
    check('knowledge has three primary navigation entries', await page.getByTestId('knowledge-subnav').locator('a').count() === 3)
    check('home has no oversized introductory heading', await page.getByTestId('knowledge-search').locator('h1').count() === 0)
    const allTopics = await api('/api/ai/knowledge/topics')
    check('thirty Chinese topics published', allTopics.length >= 30)
    const groups = ['JAVA', 'FRONTEND', 'OPERATIONS']
    for (const role of groups) {
      const directionTopics = await api(`/api/ai/knowledge/topics?roleDirection=${role}`)
      check(`${role} has ten topics`, directionTopics.length >= 10)
      const queryResult = await api('/api/ai/knowledge/workspace/search', 'POST', { query: directionTopics[0].skill, roleDirection: role, useAi: false })
      check(`${role} search produces references`, queryResult.citations.length > 0)
      check(`${role} citations are readable`, Boolean(queryResult.citations.every(citation => citation.documentId && citation.chunkId && citation.snippet)))
    }
    await page.locator('.filters summary').click()
    let delayedDirectionRequests = 0
    const delayOldDirection = async route => {
      const response = await route.fetch()
      if (new URL(route.request().url()).searchParams.get('roleDirection') === 'JAVA') {
        delayedDirectionRequests++
        await new Promise(resolve => setTimeout(resolve, 900))
      }
      await route.fulfill({ response })
    }
    await page.route(/\/api\/ai\/knowledge\/(topics|recommendations)\?/, delayOldDirection)
    const direction = page.locator('.filter-grid label').filter({ hasText: '岗位方向' }).locator('.el-select__wrapper')
    const olderRequest = page.waitForRequest(request => request.url().includes('/knowledge/recommendations') && request.url().includes('roleDirection=JAVA'))
    await direction.click(); await page.getByRole('option', { name: 'Java', exact: true }).click(); await olderRequest
    const newerResponse = page.waitForResponse(response => response.url().includes('/knowledge/recommendations') && response.url().includes('roleDirection=FRONTEND'))
    await direction.click(); await page.getByRole('option', { name: '前端', exact: true }).click(); await newerResponse
    await ready('knowledge-search')
    await page.waitForTimeout(1200)
    const frontendTitles = new Set(allTopics.filter(topic => topic.role === 'FRONTEND').map(topic => topic.title))
    const currentRecommendations = await page.locator('.recommendation strong').allTextContents()
    check('late prior direction response cannot replace current recommendations', delayedDirectionRequests > 0 && currentRecommendations.length === 3 && currentRecommendations.every(title => frontendTitles.has(title)))
    await page.unroute(/\/api\/ai\/knowledge\/(topics|recommendations)\?/, delayOldDirection)
    await direction.click(); await page.getByRole('option', { name: 'Java', exact: true }).click(); await ready('knowledge-search')
    await page.locator('.filters summary').click()
    await page.getByTestId('knowledge-submit').click()
    check('empty query shows validation', await page.getByRole('alert').filter({ hasText: '请输入' }).count() > 0)
    await input('knowledge-query').fill('Redis 缓存')
    await page.getByTestId('knowledge-submit').click()
    await page.locator('.result').first().waitFor()
    check('search displays at most five results', await page.locator('.result').count() <= 5)
    const resultSummaries = await page.locator('.result > p').allTextContents()
    check('search excerpts include substantive source text', resultSummaries.every(text => text.trim().length >= 12 && !/^#+\s*$/.test(text)))
    await shot('01-search')
    await page.getByTestId('knowledge-explain').click()
    await ready('knowledge-answer')
    await page.locator('.answer-text').waitFor()
    check('explanation is on its own page with citations', await page.locator('.citation-links button').count() > 0)
    const explanationHistoryId = new URL(page.url()).searchParams.get('historyId')
    const explanationSnapshot = explanationHistoryId ? await api(`/api/ai/knowledge/me/history/${explanationHistoryId}`) : undefined
    check('explanation uses an exact history snapshot', Boolean(explanationHistoryId && explanationSnapshot?.answerSnapshot), { generationMode: explanationSnapshot?.answerSnapshot?.generationMode, mocked: explanationSnapshot?.answerSnapshot?.mocked, retrievalMode: explanationSnapshot?.answerSnapshot?.retrievalMode })
    const explainedText = await page.locator('.answer-text').textContent()
    await page.reload(); await ready('knowledge-answer')
    check('explanation restores after refresh', await page.locator('.answer-text').textContent() === explainedText)
    await page.locator('.citation-links button').first().click(); await ready('knowledge-reader')
    await page.reload(); await ready('knowledge-reader')
    await page.getByRole('button', { name: '返回结果', exact: true }).click(); await ready('knowledge-answer')
    check('citation reader returns to explanation after refresh', await page.locator('.answer-text').textContent() === explainedText)
    await page.getByRole('button', { name: '查询结果', exact: true }).click()
    await ready('knowledge-search')
    await page.locator('.result-title').first().click()
    await ready('knowledge-reader')
    await page.locator('.reader-page mark').first().waitFor()
    check('citation highlighted in full text', await page.locator('.reader-page mark').count() > 0)
    await page.reload(); await ready('knowledge-reader')
    check('citation survives refresh', await page.locator('.reader-page mark').count() > 0)
    await shot('02-reader')
    await page.goto(config.baseUrl + '/student/knowledge/topics')
    await ready('knowledge-topics')
    await page.locator('.topic-row').first().click()
    await ready('knowledge-topic')
    currentTopic = allTopics.find(topic => page.url().includes(encodeURIComponent(topic.id))) || allTopics[0]
    await page.getByTestId('knowledge-bookmark').click()
    await page.getByTestId('knowledge-bookmark').filter({ hasText: '已收藏' }).waitFor()
    check('bookmark saved server-side', (await api('/api/ai/knowledge/me/items')).some(item => item.topicId === currentTopic.id && item.kind === 'BOOKMARK'))
    await input('knowledge-note').fill('我需要验证适用条件，不能把示例结果当成自己的成果。')
    await page.getByTestId('knowledge-save-note').click()
    await page.getByRole('status').filter({ hasText: '笔记已保存' }).waitFor()
    await page.locator('summary').filter({ hasText: '学习状态与复习' }).click()
    await page.locator('.study-controls .el-select__wrapper').click()
    await page.getByRole('option', { name: '自报掌握', exact: true }).click()
    await page.getByRole('status').filter({ hasText: '自报掌握' }).waitFor()
    let study = (await api('/api/ai/knowledge/me/items')).find(item => item.topicId === currentTopic.id && item.kind === 'STUDY')
    check('self-reported mastery schedules review without evidence mutation', Boolean(study?.nextReviewAt))
    await page.getByLabel('复习间隔').fill('2,5,10')
    await writeAndWait(page.getByRole('button', { name: '保存安排', exact: true }), '/me/items/')
    study = (await api('/api/ai/knowledge/me/items')).find(item => item.topicId === currentTopic.id && item.kind === 'STUDY')
    check('review intervals are editable', study.intervalDays.join(',') === '2,5,10')
    await page.locator('.study-controls .el-checkbox').click()
    await writeAndWait(page.getByRole('button', { name: '保存安排', exact: true }), '/me/items/')
    study = (await api('/api/ai/knowledge/me/items')).find(item => item.topicId === currentTopic.id && item.kind === 'STUDY')
    check('review schedule can be disabled', !study.nextReviewAt)
    await page.getByTestId('knowledge-nav-learning').click(); await ready('knowledge-learning')
    await page.locator('.learning-filters label').filter({ hasText: '收藏' }).click()
    check('bookmarks show human topic title', await page.locator('.learning-row strong').first().textContent() === currentTopic.title)
    await page.locator('.learning-filters label').filter({ hasText: '笔记' }).click()
    check('notes are accessible separately', await page.locator('.learning-row p').first().textContent() === '我需要验证适用条件，不能把示例结果当成自己的成果。')
    await page.locator('.learning-row .result-title').first().click(); await ready('knowledge-topic')
    const records = await api('/api/ai/knowledge/me/items')
    const note = records.find(item => item.topicId === currentTopic.id && item.kind === 'NOTE')
    check('note persisted independently of bookmark', Boolean(note) && records.some(item => item.kind === 'BOOKMARK'))
    await api(`/api/ai/knowledge/me/items/${note.itemId}`, 'PUT', { topicId: note.topicId, kind: 'NOTE', status: 'TO_LEARN', note: '来自其他设备的笔记', expectedRevision: note.revision })
    await input('knowledge-note').fill('我的未保存输入应在冲突后保留')
    await page.getByTestId('knowledge-save-note').click()
    await page.getByRole('alert').filter({ hasText: /修改|更新|冲突/ }).first().waitFor()
    check('note conflict preserves input', await input('knowledge-note').inputValue() === '我的未保存输入应在冲突后保留')
    await page.getByRole('button', { name: /使用最新版本号/ }).click()
    await page.getByTestId('knowledge-save-note').click()
    await page.getByRole('status').filter({ hasText: '笔记已保存' }).waitFor()
    await page.getByRole('button', { name: '做 3 道短练习' }).click()
    await ready('knowledge-practice')
    const practiceId = new URL(page.url()).searchParams.get('practiceId')
    currentPractice = await api(`/api/ai/knowledge/practices/${practiceId}`)
    check('practice contains three sourced questions', currentPractice.questions.length === 3 && currentPractice.questions.every(q => q.referenceChunkIds.length))
    await page.getByTestId('knowledge-practice-submit').click()
    check('empty practice answer is rejected', await page.getByRole('alert').filter({ hasText: '填写回答' }).count() > 0)
    await input('knowledge-practice-answer').fill(answerText)
    await page.route('**/api/ai/knowledge/practices/*/attempts/*/evaluate', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '模拟评价中断', data: null }) }), { times: 1 })
    await page.getByTestId('knowledge-practice-submit').click()
    await page.getByTestId('knowledge-practice-retry').waitFor()
    let attempts = await api(`/api/ai/knowledge/practices/${practiceId}/attempts`)
    check('evaluation interruption preserves saved answer', attempts.length === 1 && attempts[0].answer === answerText)
    await writeAndWait(page.getByTestId('knowledge-practice-retry'), '/evaluate')
    await page.locator('.evaluation strong').waitFor()
    attempts = await api(`/api/ai/knowledge/practices/${practiceId}/attempts`)
    check('retry does not create answer duplicate', attempts.length === 1)
    check('saved answer retry reaches successful evaluation', attempts[0].status === 'SUCCEEDED', { evaluationMode: attempts[0].evaluationSnapshot?.status })
    await page.getByRole('button', { name: '重答', exact: true }).click()
    await input('knowledge-practice-answer').fill(answerText + '我还会记录验证过程与失败原因。')
    await page.reload(); await ready('knowledge-practice')
    check('pending reanswer restores after refresh', await input('knowledge-practice-answer').inputValue() === answerText + '我还会记录验证过程与失败原因。')
    await writeAndWait(page.getByTestId('knowledge-practice-submit'), '/evaluate')
    await page.locator('.evaluation strong').waitFor()
    check('reanswer keeps original attempt', (await api(`/api/ai/knowledge/practices/${practiceId}/attempts`)).length === 2)
    for (const index of [1, 2]) {
      await page.locator('.question-nav button').nth(index).click()
      await input('knowledge-practice-answer').fill(answerText + `这是第 ${index + 1} 题的独立回答。`)
      await writeAndWait(page.getByTestId('knowledge-practice-submit'), '/evaluate')
      await page.locator('.evaluation strong').waitFor()
    }
    const completedAttempts = await api(`/api/ai/knowledge/practices/${practiceId}/attempts`)
    check('all three practice questions keep evaluated answers', completedAttempts.length === 4 && completedAttempts.every(attempt => attempt.status === 'SUCCEEDED') && await page.getByRole('button', { name: '把薄弱项安排进学习计划', exact: true }).count() === 1, { evaluationModes: completedAttempts.map(attempt => attempt.evaluationSnapshot?.status) })
    await shot('03-practice')
    await page.getByRole('button', { name: '返回专题' }).click(); await ready('knowledge-topic')
    await page.getByRole('button', { name: '安排实践' }).click()
    await page.getByTestId('knowledge-action-preview').waitFor()
    await page.getByRole('button', { name: '查看调整预览' }).click()
    await page.getByTestId('knowledge-action-confirm').waitFor()
    check('plan preview does not activate a new plan', !(await api('/api/ai/learning/plans')).some(plan => plan.status === 'ACTIVE'))
    await page.getByTestId('knowledge-action-confirm').click()
    await page.waitForURL('**/student/plan/**')
    planId = new URL(page.url()).searchParams.get('planId')
    const plan = await api(`/api/ai/learning/plans/${planId}`)
    check('confirmed action creates active plan', plan.status === 'ACTIVE')
    check('plan task time within weekly budget', plan.tasks.reduce((sum, task) => sum + (task.estimatedMinutes || task.estimatedHours * 60), 0) <= plan.weeklyHours * 60 * plan.durationWeeks)
    await page.goto(config.baseUrl + `/student/knowledge/topics/${currentTopic.id}?topicId=${currentTopic.id}`); await ready('knowledge-topic')
    await page.getByRole('button', { name: /练一道面试题/ }).click()
    await page.getByRole('button', { name: '查看调整预览' }).click(); await page.getByTestId('knowledge-action-confirm').waitFor()
    await page.getByTestId('knowledge-action-confirm').click(); await page.waitForURL('**/student/interview/practice*')
    const interviewSessionId = new URL(page.url()).searchParams.get('sessionId')
    const interview = await api(`/api/ai/interview/sessions/${interviewSessionId}`)
    check('confirmed knowledge action creates sourced coaching practice', interview.mode === 'COACHING' && interview.questions.length > 0 && Boolean(interview.knowledgeReferences?.length || interview.questions.some(question => question.sourceReferences?.length || question.sourceBasis || question.sourceType)))
    await page.goto(config.baseUrl + '/student/knowledge/history'); await ready('knowledge-history')
    const beforeHistory = await page.locator('.history-row').count()
    await page.locator('.history-row > button').first().click(); await ready('knowledge-answer')
    check('history detail restores server snapshot', await page.locator('.answer-text').count() === 1)
    await page.goto(config.baseUrl + '/student/knowledge/history'); await ready('knowledge-history')
    await page.getByRole('button', { name: '删除查询记录' }).first().click()
    await page.waitForFunction(count => document.querySelectorAll('.history-row').length === count, beforeHistory - 1)
    check('history entry can be deleted', await page.locator('.history-row').count() === beforeHistory - 1)
    const other = await api('/api/auth/register', 'POST', { username: username + '_other', password, displayName: '隔离测试', role: 'STUDENT' }, null)
    const denied = await api(`/api/ai/knowledge/practices/${practiceId}`, 'GET', undefined, other.token, true)
    check('other account cannot read practice', denied.code !== 0)
    const otherItems = await api('/api/ai/knowledge/me/items', 'GET', undefined, other.token)
    check('other account cannot see notes', otherItems.length === 0)
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 900 })
      for (const [name, url, id] of [['search', '/student/knowledge', 'knowledge-search'], ['topics', '/student/knowledge/topics', 'knowledge-topics'], ['reader', `/student/knowledge/library?documentId=${currentTopic.documentId}`, 'knowledge-reader'], ['topic', `/student/knowledge/topics/${currentTopic.id}?topicId=${currentTopic.id}`, 'knowledge-topic'], ['practice', `/student/knowledge/practice?practiceId=${practiceId}&topicId=${currentTopic.id}`, 'knowledge-practice'], ['learning', '/student/knowledge/learning', 'knowledge-learning'], ['history', '/student/knowledge/history', 'knowledge-history']]) {
        await page.goto(config.baseUrl + url); await ready(id); await layout(`${name} ${width}px`); await shot(`${name}-${width}`)
        if (name === 'search') check(`recommendation reasons stay within two lines ${width}px`, await page.locator('.recommendation-reason').evaluateAll(elements => elements.every(element => element.getBoundingClientRect().height <= Number.parseFloat(getComputedStyle(element).lineHeight) * 2 + 2)))
        if (name === 'practice') {
          const feedbackText = await page.locator('.evaluation > p').allTextContents()
          check(`visible practice feedback has readable references ${width}px`, currentPractice.questions[0].referenceChunkIds.every(id => feedbackText.every(text => !text.includes(id))))
          await page.locator('.evaluation summary').click(); await layout(`practice expanded ${width}px`); await shot(`practice-expanded-${width}`)
        }
      }
    }
    await page.setViewportSize({ width: 1440, height: 900 })
    const adminLogin = await api('/api/auth/login', 'POST', { username: config.adminUsername, password: config.adminPassword }, null, true)
    if (adminLogin.code === 0 && adminLogin.data?.role === 'ADMIN') {
      const uploaded = await page.request.post(config.apiUrl + '/api/ai/knowledge/files', { headers: { Authorization: `Bearer ${adminLogin.data.token}` }, multipart: { file: { name: `browser-two-pages-${suffix}.pdf`, mimeType: 'application/pdf', buffer: Buffer.from(config.pdfBase64, 'base64') }, title: `浏览器 PDF 两页验收 ${suffix}`, roles: 'STUDENT,ADMIN' } })
      const uploadBody = await uploaded.json()
      if (!uploaded.ok() || uploadBody.code !== 0) throw new Error(`PDF import: ${uploadBody.message}`)
      let job = uploadBody.data
      for (let index = 0; index < 60 && !['READY', 'FAILED', 'DUPLICATE'].includes(job.status); index++) {
        await new Promise(resolve => setTimeout(resolve, 1000))
        job = (await api('/api/ai/knowledge/ingestions?limit=200', 'GET', undefined, adminLogin.data.token)).find(item => item.jobId === job.jobId) || job
      }
      check('two-page PDF import parsed', job.status === 'READY')
      await api(`/api/ai/knowledge/publications/${job.documentId}/publish`, 'POST', undefined, adminLogin.data.token)
      const pdfDocument = await api(`/api/ai/knowledge/library/${job.documentId}`)
      check('PDF full text maps both pages', pdfDocument.pages.length === 2)
      await page.goto(config.baseUrl + `/student/knowledge/library?documentId=${job.documentId}`); await ready('knowledge-reader')
      await page.getByRole('button', { name: '查看原件', exact: true }).click()
      await page.locator('.pdf-pages canvas').nth(1).waitFor()
      await page.waitForFunction(() => [...document.querySelectorAll('.pdf-pages canvas')].every(canvas => {
        if (canvas.width <= 300 || canvas.height <= 150) return false
        const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data
        return pixels.some((value, index) => index % 4 === 0 && value < 220 && pixels[index + 3] > 128)
      }))
      check('PDF original canvases render nonblank pages', await page.locator('.pdf-pages canvas').count() === 2)
      await layout('PDF original 1440px'); await shot('pdf-original-1440')
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 900 }); await layout(`PDF original ${width}px`); await shot(`pdf-original-${width}`)
      }
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(config.baseUrl + `/student/knowledge/library?documentId=${currentTopic.documentId}`); await ready('knowledge-reader')
      check('changing reader document removes prior original', await page.locator('.pdf-pages canvas').count() === 0 && await page.getByRole('link', { name: '下载原件', exact: true }).count() === 0)
      await api(`/api/ai/knowledge/publications/${job.documentId}/unpublish`, 'POST', undefined, adminLogin.data.token)
      await page.goto(config.baseUrl + '/login')
      await page.locator('input[autocomplete="username"]').fill(config.adminUsername)
      await page.locator('input[type="password"]').fill(config.adminPassword)
      await page.getByRole('button', { name: '登录', exact: true }).click()
      await page.waitForURL('**/admin/ai*')
      await page.getByTestId('knowledge-publications').waitFor()
      check('admin has three management navigation entries', await page.locator('.publication-nav button').count() === 3)
      await page.getByRole('button', { name: '导入任务', exact: true }).click()
      await page.waitForURL('**tab=imports*')
      check('admin import navigation loads', await page.locator('.import-form').count() === 1)
      await page.getByRole('button', { name: '维护', exact: true }).click()
      await page.waitForURL('**tab=maintenance*')
      check('admin maintenance navigation loads', await page.locator('.index-panel').count() > 0)
      await page.getByRole('button', { name: '资料', exact: true }).click()
      await page.getByTestId('knowledge-publication-new').click()
      await page.locator('.editor > label').filter({ hasText: '标题' }).locator('input').fill(`自动化中文资料 ${suffix}`)
      await page.locator('.editor-meta label').filter({ hasText: '来源' }).locator('input').fill('自动化测试自编资料')
      await page.locator('.editor > label').filter({ hasText: '正文' }).locator('textarea').fill('这是自动化测试资料。资料应先保存草稿，再审核发布，最后可以下架。')
      check('admin permissions are real checkboxes', await page.locator('.editor input[type="checkbox"]').count() === 3)
      const publication = await writeAndWait(page.getByTestId('knowledge-publication-save'), '/publications')
      check('admin creates draft before publishing', publication.status === 'DRAFT')
      await page.getByRole('button', { name: '发布', exact: true }).click()
      await page.getByRole('status').filter({ hasText: '已发布' }).waitFor()
      const readable = await api(`/api/ai/knowledge/library/${publication.documentId}`)
      check('published document becomes readable', readable.title.includes(suffix))
      await page.getByRole('button', { name: '下架', exact: true }).click()
      await page.getByRole('status').filter({ hasText: '已下架' }).waitFor()
      const hidden = await api(`/api/ai/knowledge/library/${publication.documentId}`, 'GET', undefined, auth.token, true)
      check('unpublished document no longer readable', hidden.code !== 0)
      await shot('admin-publication')
    } else checks.push({ name: 'admin browser flow unavailable credentials; separate API acceptance required', passed: null })
    check('no uncaught browser errors', errors.length === 0, { errors })
    return { status: 'PASSED', checks, screenshots, browserErrors: errors.length, account: { username, userId: auth.userId }, recovery: { practiceId, topicId: currentTopic.id, planId }, completedAt: new Date().toISOString() }
  } catch (error) { await shot('failure'); return { status: 'FAILED', error: error.message, checks, screenshots, browserErrors: errors.length, account: { username, userId: auth?.userId }, completedAt: new Date().toISOString() } }
}

try {
  fs.writeFileSync(runner, `async (page) => (${browserSuite.toString()})(page, ${JSON.stringify(config)})`, 'utf8')
  cli(['open', config.baseUrl + '/login', ...(process.env.E2E_HEADLESS === '1' ? [] : ['--headed'])])
  const output = cli(['run-code', `--filename=${runner}`])
  const resultText = output.split('### Result\n')[1]?.split('\n### ')[0]?.trim()
  if (!resultText) throw new Error('Browser did not return a structured result')
  const report = JSON.parse(resultText)
  fs.writeFileSync(path.join(artifacts, 'report.json'), JSON.stringify(report, null, 2), 'utf8')
  console.log(`${report.status}: ${report.checks.length} checks; ${report.screenshots.length} screenshots; ${report.browserErrors} browser errors`)
  console.log(`Report: ${path.join(artifacts, 'report.json')}`)
  if (report.status !== 'PASSED') throw new Error(report.error)
} catch (error) { console.error(error.message); process.exitCode = 1 }
finally { try { cli(['close']) } catch {}; if (fs.existsSync(runner)) fs.unlinkSync(runner) }
