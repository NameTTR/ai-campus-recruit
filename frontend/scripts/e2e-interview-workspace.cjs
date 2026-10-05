const { spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '../..')
const runId = new Date().toISOString().replace(/[:.]/g, '-')
const artifacts = path.resolve(process.env.E2E_INTERVIEW_ARTIFACTS_DIR || path.join(root, 'output/playwright/interview-workspace', runId))
const options = {
  baseUrl: (process.env.E2E_INTERVIEW_BASE_URL || 'http://localhost').replace(/\/+$/, ''),
  apiUrl: (process.env.E2E_INTERVIEW_API_URL || 'http://localhost:18080').replace(/\/+$/, ''),
  artifacts,
  runId
}
const browserSession = `interview-${Date.now().toString(36)}`
const command = process.platform === 'win32' ? 'npx.cmd' : 'npx'
const runner = path.join(artifacts, 'browser-run.js')
fs.mkdirSync(artifacts, { recursive: true })

// Use the same CLI browser as the visual review, without adding a test runner dependency.
function cli(args) {
  const result = spawnSync(command, ['--yes', '--package', '@playwright/cli', 'playwright-cli', `-s=${browserSession}`, ...args], {
    cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, env: process.env,
    shell: process.platform === 'win32'
  })
  if (result.error || result.status !== 0) throw result.error || new Error(`Browser command failed (${result.status}): ${result.stderr}`)
  return result.stdout
}

async function browserSuite(page, config) {
  const checks = []
  const screenshots = []
  const errors = []
  const sessions = []
  let auth
  const suffix = Date.now().toString(36)
  const username = `interview_e2e_${suffix}`
  const password = `InterviewE2e!${suffix}`
  const answer = '我负责课程项目的查询接口，使用 Java 编写输入校验，通过边界测试验证无效参数会被拒绝。我会先说明需求，再比较实现方式，最后展示测试说明。目前没有测量性能数据，因此不能断言性能提升。'
  const improved = '我个人负责课程项目查询接口的参数校验。我先列出空参数、非法参数和正常请求三种情况，再使用 Java 编写校验逻辑，并分别运行测试验证拒绝无效输入。我选择在入口统一校验，避免下游重复处理；结果是上述用例都得到预期响应。没有测量吞吐量，也没有使用 Redis。'
  const check = (name, condition, detail = {}) => {
    if (!condition) throw new Error(`Acceptance failed: ${name}`)
    checks.push({ name, passed: true, ...detail })
  }
  const api = async (route, method = 'GET', data, token = auth?.token, allowFailure = false) => {
    const response = await page.request.fetch(config.apiUrl + route, {
      method, data, timeout: 150000,
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Content-Type': 'application/json' }
    })
    let body
    try { body = await response.json() } catch { throw new Error(`Non-JSON response: ${route} (${response.status()})`) }
    if (allowFailure) {
      if (typeof body.code !== 'number') throw new Error(`${route}: rejected request must use ApiResponse (${response.status()})`)
      return { httpStatus: response.status(), ...body }
    }
    if (!response.ok() || body.code !== 0 || body.data == null) throw new Error(`${route}: ${response.status()} ${body.message || 'missing data'}`)
    return body.data
  }
  const getSession = id => api(`/api/ai/interview/sessions/${id}`)
  const save = (sessionId, questionId, text) => api(`/api/ai/interview/sessions/${sessionId}/questions/${questionId}/answer`, 'PUT', { questionId, answer: text })
  const evaluate = (sessionId, questionId, attemptId) => api(`/api/ai/interview/sessions/${sessionId}/questions/${questionId}${attemptId ? `/attempts/${attemptId}` : ''}/evaluate`, 'POST')
  const clickAndWait = async (control, urlSuffix, method = 'POST', allowFailure = false) => {
    const responsePromise = page.waitForResponse(response => response.url().includes(urlSuffix) && response.request().method() === method, { timeout: 150000 })
    await control.click()
    const response = await responsePromise
    const body = await response.json()
    if (!allowFailure && (!response.ok() || body.code !== 0)) throw new Error(`Browser operation ${urlSuffix}: ${response.status()} ${body.message}`)
    return body.data
  }
  const ready = async testId => {
    const control = page.getByTestId(testId)
    await control.waitFor()
    await page.waitForFunction(id => {
      const element = document.querySelector(`[data-testid="${id}"]`)
      return element && !element.disabled
    }, testId)
    return control
  }
  const attemptsFor = (session, questionId) => (session.attempts || []).filter(attempt => attempt.questionId === questionId)
  const snap = async name => {
    const file = `${config.artifacts}/${name}.png`
    await page.screenshot({ path: file, fullPage: true })
    screenshots.push(file)
  }
  const navigateSession = async (id, view = 'practice') => {
    await page.goto(`${config.baseUrl}/student/interview/${view}?sessionId=${encodeURIComponent(id)}`)
    await page.getByTestId(`interview-${view}`).waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-testid^="interview-"] .el-loading-mask'))
    await page.locator(view === 'practice' ? '.question-text' : '.report-header').waitFor()
  }
  const layout = async name => {
    const geometry = await page.evaluate(view => {
      const main = document.querySelector('.student-main, .workspace-main, main')
      const candidates = [...new Set([document.scrollingElement, ...document.querySelectorAll('*')])].filter(element => element && element.clientHeight > 0 && element.getClientRects().length && element.scrollHeight > element.clientHeight + 2 && (element === document.scrollingElement || /auto|scroll/.test(getComputedStyle(element).overflowY)))
      for (const element of candidates) element.scrollTop = element.scrollHeight
      const section = document.querySelector(`[data-testid="interview-${view}"]`)
      const endControls = {
        start: '.launch-footer', practice: '.answer-actions, :scope > .el-button',
        report: ':scope > .report-details', history: '.record'
      }
      const end = Array.from(section?.querySelectorAll(endControls[view]) || []).at(-1)
      const endRect = end?.getBoundingClientRect()
      let endVisible = Boolean(endRect && endRect.top < innerHeight && endRect.bottom > 0 && endRect.bottom <= innerHeight + 2)
      for (let ancestor = end?.parentElement; endVisible && ancestor; ancestor = ancestor.parentElement) {
        if (/auto|scroll|hidden|clip/.test(getComputedStyle(ancestor).overflowY)) {
          const rect = ancestor.getBoundingClientRect()
          if (endRect.bottom > rect.bottom + 2 || endRect.bottom < rect.top) endVisible = false
        }
      }
      return {
        viewport: innerWidth,
        content: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
        scrollContainers: candidates.map(element => ({ className: element.className, top: element.scrollTop, maximum: element.scrollHeight - element.clientHeight })),
        mainVisible: !main || main.getBoundingClientRect().width > 0,
        endVisible, endBottom: endRect?.bottom
      }
    }, name.split(' ')[1])
    check(`${name}: no horizontal overflow`, geometry.content <= geometry.viewport + 1, geometry)
    check(`${name}: scroll can reach end`, geometry.mainVisible && geometry.scrollContainers.every(item => Math.abs(item.top - item.maximum) <= 2), geometry)
    check(`${name}: final controls are reachable`, geometry.endVisible, geometry)
    await snap(`06-scroll-end-${name.replace(/[^a-z0-9]+/gi, '-')}`)
    await page.evaluate(() => { window.scrollTo(0, 0); for (const element of document.querySelectorAll('*')) if (element.scrollTop) element.scrollTop = 0 })
  }
  const create = async data => {
    const session = await api('/api/ai/interview/sessions', 'POST', data)
    sessions.push(session.sessionId)
    return session
  }
  const startInBrowser = async (context, { mode, sourceType, sourceLabel, timerOff = false, count = 4 }) => {
    await page.goto(`${config.baseUrl}/student/interview?matchId=${context.matchId}`)
    await page.getByTestId('interview-start').waitFor()
    await page.getByTestId('interview-mode').getByText(mode === 'MOCK' ? '模拟面试' : '辅导练习', { exact: true }).click()
    await page.getByTestId('interview-source-type').getByText({ JOB: '岗位', PROJECT: '项目', GAP: '薄弱项' }[sourceType], { exact: true }).click()
    if (sourceType !== 'JOB') {
      await page.getByTestId('interview-source-picker').click()
      await page.getByRole('option', { name: sourceLabel, exact: true }).click()
    }
    const countControl = page.locator('[aria-label="面试题数"]')
    const countInput = await countControl.locator('input').count() ? countControl.locator('input') : countControl
    await countInput.fill(String(count))
    await page.getByLabel('本次目标岗位', { exact: true }).fill(context.targetRole)
    if (timerOff) await page.getByTestId('interview-timer-enabled').click()
    await page.getByTestId('interview-start-button').click()
    await page.waitForURL('**/student/interview/practice?*', { timeout: 150000 })
    await page.getByTestId('interview-practice').waitFor()
    const sessionId = new URL(page.url()).searchParams.get('sessionId')
    const result = await getSession(sessionId)
    sessions.push(sessionId)
    check(`browser creates ${mode} ${sourceType} session`, result.mode === mode && result.sourceType === sourceType)
    return result
  }
  const finishAll = async session => {
    for (let guard = 0; guard < 24; guard++) {
      session = await getSession(session.sessionId)
      const pending = session.questions.find(question => !(session.mode === 'MOCK' && question.followUp) && !session.answers.some(item => item.questionId === question.questionId))
      if (!pending) break
      await save(session.sessionId, pending.questionId, answer)
      if (session.mode !== 'MOCK') await evaluate(session.sessionId, pending.questionId)
    }
    return api(`/api/ai/interview/sessions/${session.sessionId}/finish`, 'POST')
  }
  const assertQuotes = (session, report) => {
    for (const feedback of report.questionFeedback || []) {
      const attempt = attemptsFor(session, feedback.questionId).find(item => item.selectedForReport)
      const text = attempt?.answer || session.answers.find(item => item.questionId === feedback.questionId)?.answer || ''
      for (const evidence of feedback.evidence || []) check('feedback quote exists in selected answer', Boolean(evidence.quote) && text.includes(evidence.quote))
    }
  }

  page.setDefaultTimeout(90000)
  page.on('pageerror', error => errors.push(error.message))
  try {
    await page.setViewportSize({ width: 1440, height: 980 })
    await page.goto(config.baseUrl + '/login')
    auth = await api('/api/auth/register', 'POST', { username, password, displayName: '面试自动化测试', role: 'STUDENT' }, null)
    const jobs = await api('/api/jobs')
    const roles = [
      { name: 'Java 后端实习生', regex: /java|后端/i },
      { name: '前端实习生', regex: /前端|frontend/i },
      { name: '运营实习生', regex: /运营|operation/i }
    ].map(role => ({ ...role, job: jobs.find(job => role.regex.test(job.title)) }))
    check('three role job fixtures exist', roles.every(role => role.job))
    await page.locator('input[autocomplete="username"]').fill(username)
    await page.locator('input[type="password"]').fill(password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/student/resume*')
    await page.goto(config.baseUrl + '/student/interview')
    await page.getByTestId('interview-start').waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-testid="interview-start-button"]')?.disabled)
    check('fresh account can start without resume', await page.getByTestId('interview-start-button').isEnabled())
    await snap('01-empty-start')

    const currentProfile = await api('/api/resumes/master-profile')
    const profile = await api('/api/resumes/master-profile', 'PUT', {
      expectedRevision: currentProfile.revision, confirmed: true,
      data: {
        basics: { name: '陈合成', phone: '13800000001', email: 'interview@example.test', city: '上海', portfolioUrl: '' },
        education: [{ id: 'education-1', school: '合成测试大学', major: '计算机科学', degree: '本科', startDate: '2023-09', endDate: '2027-06', graduationDate: '2027-06', courses: ['软件工程'], notes: '' }],
        skills: [{ id: 'skill-java', name: 'Java' }],
        experiences: [{ id: 'project-java', type: 'PROJECT', title: '课程查询接口', organization: '合成测试大学', startDate: '2026-01', endDate: '2026-02', role: '个人开发', actions: '使用 Java 实现课程查询接口的输入校验。', methods: '列出正常和非法参数用例，验证接口响应。', results: '课程作品已提交。未测量性能数据。', skills: ['Java'], links: [], confirmed: true }],
        credentials: [], availability: { cities: ['上海'], earliestStartDate: '2026-10-07', daysPerWeek: 3, continuousMonths: 3, graduationDate: '2027-06' }
      }
    })
    const draft = await api('/api/resumes/drafts', 'POST', { templateId: 'T01', targetRole: roles[0].name, jobId: roles[0].job.jobId, profileRevision: profile.revision })
    const confirmed = await api(`/api/resumes/drafts/${draft.id}`, 'PATCH', { expectedRevision: draft.revision, data: draft.data, confirm: true })
    const resumeId = confirmed.resumeId
    check('confirmed resume fixture exists', Boolean(resumeId))
    const match = await api('/api/matches/resume-job', 'POST', { resumeId, jobId: roles[0].job.jobId })
    const context = { resumeId, jobId: roles[0].job.jobId, matchId: match.matchId, targetRole: roles[0].name }
    const sources = await api(`/api/ai/interview/sessions/sources?resumeId=${resumeId}&jobId=${context.jobId}&matchId=${match.matchId}`)
    const projectSource = sources.find(source => source.sourceType === 'PROJECT')
    const gapSource = sources.find(source => source.sourceType === 'GAP')
    check('server exposes real project source', Boolean(projectSource))
    check('server exposes match gap source', Boolean(gapSource))
    for (const sourceType of ['PROJECT', 'GAP']) {
      const rejected = await api('/api/ai/interview/sessions', 'POST', { ...context, mode: 'COACHING', sourceType, sourceId: 'forged-source', questionCount: 1 }, undefined, true)
      check(`forged ${sourceType} source rejected`, rejected.code !== 0)
    }

    let coaching = await startInBrowser(context, { mode: 'COACHING', sourceType: 'PROJECT', sourceLabel: projectSource.label })
    check('coaching creates four main questions', coaching.questions.filter(question => !question.followUp).length === 4)
    check('project questions stay focused on the selected material', coaching.questions.every(question => question.question.includes(projectSource.label)))
    check('coaching has no timer', !coaching.timer?.timerMinutes)
    await navigateSession(coaching.sessionId)
    await page.locator('#interview-answer').fill(answer)
    await clickAndWait(page.getByTestId('interview-save-answer'), `/sessions/${coaching.sessionId}/questions/`)
    await ready('interview-reanswer')
    coaching = await getSession(coaching.sessionId)
    const q1 = coaching.questions[0].questionId
    const firstAttempt = attemptsFor(coaching, q1)[0]
    check('first answer retained as immutable attempt', Boolean(firstAttempt) && firstAttempt.answer === answer && firstAttempt.selectedForReport)
    check('coaching remains on submitted question', await page.locator('#interview-answer').inputValue() === answer)
    const replayed = await save(coaching.sessionId, q1, answer)
    check('duplicate answer creates no extra attempt', attemptsFor(replayed, q1).length === 1)
    await page.getByTestId('interview-reanswer').click()
    await page.locator('#interview-answer').fill(improved)
    await page.reload()
    await page.getByTestId('interview-practice').waitFor()
    await page.waitForFunction(text => document.querySelector('#interview-answer')?.value === text, improved)
    check('unsaved reanswer survives refresh', true)
    await clickAndWait(page.getByTestId('interview-save-answer'), `/sessions/${coaching.sessionId}/questions/`)
    await ready('interview-adopt-attempt')
    coaching = await getSession(coaching.sessionId)
    let attempts = attemptsFor(coaching, q1)
    check('reanswer keeps original attempt', attempts.length === 2 && attempts[0].answer === answer && attempts[1].answer === improved)
    check('new attempt is not silently adopted', attempts[0].selectedForReport && !attempts[1].selectedForReport)
    await clickAndWait(page.getByTestId('interview-adopt-attempt'), `/attempts/${attempts[1].attemptId}/select`)
    await page.waitForFunction(() => !document.querySelector('[data-testid="interview-adopt-attempt"]'))
    coaching = await getSession(coaching.sessionId)
    attempts = attemptsFor(coaching, q1)
    check('explicit adoption switches report answer', attempts[1].selectedForReport && !attempts[0].selectedForReport)
    const success = attempts[1].evaluation
    await evaluate(coaching.sessionId, q1, attempts[1].attemptId)
    coaching = await getSession(coaching.sessionId)
    check('successful evaluation reused', JSON.stringify(attemptsFor(coaching, q1)[1].evaluation) === JSON.stringify(success))
    await snap('02-coaching-reanswer')
    await clickAndWait(page.getByTestId('interview-partial-report'), `/sessions/${coaching.sessionId}/partial-report`)
    await page.getByTestId('interview-report').waitFor()
    await page.locator('.report-header').waitFor()
    coaching = await getSession(coaching.sessionId)
    check('partial report keeps unfinished session', coaching.status !== 'COMPLETED' && coaching.partialReport?.reportType === 'PARTIAL')
    check('partial report identifies unanswered questions', coaching.partialReport.unansweredQuestionIds?.length > 0)
    await page.getByTestId('interview-view-answers').click()
    await page.getByTestId('interview-practice').waitFor()
    await page.waitForFunction(() => {
      const input = document.querySelector('#interview-answer')
      return input && !input.readOnly && !input.disabled
    })
    check('partial report allows continuation', await page.locator('#interview-answer').isEditable())

    let mock = await startInBrowser(context, { mode: 'MOCK', sourceType: 'JOB' })
    check('mock defaults to twenty minutes', mock.timer?.timerMinutes === 20)
    check('mock API hides reference points', mock.questions.every(question => !question.referencePoints?.length))
    check('mock API hides context reference answers', !JSON.stringify(mock.contextSnapshot || {}).includes('referencePoints'))
    await navigateSession(mock.sessionId)
    check('mock UI hides coaching reference and feedback', !(await page.locator('.reference-points,.question-feedback,[data-testid="interview-reanswer"]').count()))
    await page.locator('#interview-answer').fill(answer)
    await clickAndWait(page.getByTestId('interview-save-answer'), `/sessions/${mock.sessionId}/questions/`)
    mock = await getSession(mock.sessionId)
    const mockQ = mock.questions[0].questionId
    const blocked = await api(`/api/ai/interview/sessions/${mock.sessionId}/questions/${mockQ}/evaluate`, 'POST', undefined, undefined, true)
    check('mock live evaluation cannot leak hints', blocked.code !== 0)
    check('mock saved answer hides evaluation', !mock.answers.find(item => item.questionId === mockQ)?.evaluation)
    check('mock has no live followups', !(await api(`/api/ai/interview/sessions/${mock.sessionId}/follow-ups`)).length)
    await clickAndWait(page.getByTestId('interview-pause'), `/sessions/${mock.sessionId}/pause`)
    await ready('interview-resume')
    mock = await getSession(mock.sessionId)
    check('server records paused timer', Boolean(mock.timer?.pausedAt))
    const elapsed = mock.timer.accumulatedSeconds
    await page.waitForTimeout(1200)
    mock = await getSession(mock.sessionId)
    check('paused timer does not advance', mock.timer.accumulatedSeconds === elapsed)
    await page.reload()
    await page.getByTestId('interview-resume').waitFor()
    check('paused state survives refresh', true)
    await clickAndWait(page.getByTestId('interview-resume'), `/sessions/${mock.sessionId}/resume`)
    await ready('interview-pause')
    mock = await getSession(mock.sessionId)
    check('server records resumed timer', !mock.timer.pausedAt && Boolean(mock.timer.runningSince))
    await snap('03-mock-practice')

    for (const minutes of [0, 5, 60]) {
      const timed = minutes === 0
        ? await startInBrowser(context, { mode: 'MOCK', sourceType: 'JOB', count: 1, timerOff: true })
        : await create({ targetRole: 'Java 后端实习生', mode: 'MOCK', sourceType: 'JOB', timerMinutes: minutes, questionCount: 1 })
      check(`timer ${minutes} accepted`, (timed.timer?.timerMinutes || 0) === minutes)
    }
    for (const minutes of [4, 61]) {
      const rejected = await api('/api/ai/interview/sessions', 'POST', { targetRole: 'Java 后端实习生', mode: 'MOCK', timerMinutes: minutes, questionCount: 1 }, undefined, true)
      check(`timer ${minutes} rejected`, rejected.code !== 0)
    }
    const eight = await startInBrowser(context, { mode: 'COACHING', sourceType: 'GAP', sourceLabel: gapSource.label, count: 8 })
    check('eight-question gap boundary', eight.questions.filter(question => !question.followUp).length === 8)
    check('gap questions stay focused on the selected weakness', eight.questions.every(question => question.question.toLowerCase().includes(gapSource.label.toLowerCase())))
    for (const invalidAnswer of ['', ' '.repeat(5), 'a'.repeat(8001)]) {
      const rejected = await api(`/api/ai/interview/sessions/${eight.sessionId}/questions/${eight.questions[0].questionId}/answer`, 'PUT', { questionId: eight.questions[0].questionId, answer: invalidAnswer }, undefined, true)
      check(`invalid answer length ${invalidAnswer.length} rejected`, rejected.code !== 0)
    }
    for (const count of [0, 9]) {
      const rejected = await api('/api/ai/interview/sessions', 'POST', { targetRole: 'Java', questionCount: count }, undefined, true)
      check(`question count ${count} rejected`, rejected.code !== 0)
    }

    await navigateSession(eight.sessionId)
    await page.locator('#interview-answer').fill(answer)
    const answerRoute = `**/api/ai/interview/sessions/${eight.sessionId}/questions/*/attempts`
    await page.route(answerRoute, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: 'E2E simulated save outage' }) }))
    await clickAndWait(page.getByTestId('interview-save-answer'), `/sessions/${eight.sessionId}/questions/`, 'POST', true)
    check('save failure preserves local answer', await page.locator('#interview-answer').inputValue() === answer)
    check('save failure creates no server attempt', !(await getSession(eight.sessionId)).attempts.length)
    await page.unroute(answerRoute)
    const evaluationRoute = `**/api/ai/interview/sessions/${eight.sessionId}/questions/**/evaluate`
    await page.route(evaluationRoute, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: 'E2E simulated evaluation outage' }) }))
    await clickAndWait(page.getByTestId('interview-save-answer'), `/sessions/${eight.sessionId}/questions/`)
    await ready('interview-evaluate-attempt')
    check('evaluation failure keeps saved original answer', (await getSession(eight.sessionId)).attempts[0].answer === answer)
    await page.unroute(evaluationRoute)
    await clickAndWait(page.getByTestId('interview-evaluate-attempt'), '/evaluate')
    await page.waitForFunction(() => !document.querySelector('[data-testid="interview-evaluate-attempt"]'))
    check('evaluation retry uses saved attempt', (await getSession(eight.sessionId)).attempts[0].evaluationStatus === 'SUCCEEDED')

    const coachingReport = await finishAll(coaching)
    coaching = await getSession(coaching.sessionId)
    check('coaching final report complete', coachingReport.reportType === 'FINAL' && coaching.status === 'COMPLETED')
    assertQuotes(coaching, coachingReport)
    check('each main question gets at most one followup', coaching.questions.filter(question => !question.followUp).every(question => coaching.questions.filter(item => item.followUp && item.mainQuestionId === question.questionId).length <= 1))
    const mockReport = await finishAll(mock)
    mock = await getSession(mock.sessionId)
    check('mock final report complete', mockReport.reportType === 'FINAL' && mock.status === 'COMPLETED')
    assertQuotes(mock, mockReport)
    check('cross-mode reports are not compared', !(mockReport.comparableSessionIds || []).includes(coaching.sessionId))
    check('report discloses difficulty', Boolean(mockReport.difficultyNote))

    const actions = await api(`/api/ai/interview/sessions/${coaching.sessionId}/next-actions`)
    check('report actions expose actionable options', actions.length > 0 && actions.every(action => action.actionId && action.title && action.estimatedMinutes > 0))
    const practiceAction = actions.find(action => /PRACTICE|INTERVIEW/.test(action.type))
    check('report offers targeted practice', Boolean(practiceAction))
    const beforePreview = await api('/api/ai/interview/sessions')
    const preview = await api(`/api/ai/interview/sessions/${coaching.sessionId}/next-actions/preview`, 'POST', { actionId: practiceAction.actionId })
    check('action preview has no creation side effect', !preview.createdSessionId && (await api('/api/ai/interview/sessions')).length === beforePreview.length)
    const confirmedAction = await api(`/api/ai/interview/sessions/${coaching.sessionId}/next-actions/confirm`, 'POST', { previewId: preview.previewId })
    check('confirmed action creates targeted practice', Boolean(confirmedAction.createdSessionId))
    const again = await api(`/api/ai/interview/sessions/${coaching.sessionId}/next-actions/confirm`, 'POST', { previewId: preview.previewId })
    check('action confirmation idempotent', again.createdSessionId === confirmedAction.createdSessionId)
    const profileBeforeCandidate = await api('/api/resumes/master-profile')
    const selectedAttempt = attemptsFor(coaching, q1).find(attempt => attempt.selectedForReport)
    const candidate = await api('/api/resumes/master-profile/interview-candidate', 'POST', { sessionId: coaching.sessionId, questionId: q1, attemptId: selectedAttempt.attemptId })
    check('interview candidate remains unconfirmed', candidate.confirmed === false && candidate.source?.kind === 'INTERVIEW_ANSWER')
    check('candidate creation does not mutate profile', (await api('/api/resumes/master-profile')).revision === profileBeforeCandidate.revision)

    await navigateSession(coaching.sessionId, 'report')
    await page.locator('.question-reports > details').first().locator('summary').first().click()
    await page.getByRole('button', { name: '补充简历材料', exact: true }).first().click()
    await page.waitForURL('**/student/resume/profile?*')
    await page.getByTestId('resume-interview-candidate').waitFor()
    await page.getByRole('button', { name: '加入这次面试的候选材料', exact: true }).click()
    await page.getByTestId('resume-profile-experience').click()
    await page.getByTestId('resume-experience-title-1').waitFor()
    check('browser imports interview candidate into editing area', Boolean(await page.getByTestId('resume-experience-title-1').evaluate(element => (element.matches('input') ? element : element.querySelector('input'))?.value)))
    check('browser import awaits explicit source confirmation', (await api('/api/resumes/master-profile')).revision === profileBeforeCandidate.revision)
    await snap('04-resume-candidate')

    const learningPlan = await api('/api/ai/learning/plans', 'POST', { ...context, weeklyHours: 2, durationWeeks: 1,
      dailyMinutesCap: 60, studyDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] })
    check('linked active learning plan exists', learningPlan.status === 'ACTIVE' && learningPlan.tasks.length > 0)
    check('two-hour plan retains positive minutes within budget', learningPlan.tasks.every(task => task.estimatedMinutes > 0)
      && learningPlan.tasks.reduce((total, task) => total + task.estimatedMinutes, 0) <= 120, {
      mocked: learningPlan.mocked, taskMinutes: learningPlan.tasks.map(task => task.estimatedMinutes)
    })
    const learningTask = learningPlan.tasks[0]
    const practiceText = `${learningTask.skillGap} ${learningTask.title}`
    const implementation = /redis|缓存/i.test(practiceText)
      ? '缓存方案：使用 course:{id} 作为键；命中时读缓存，未命中时查询数据库并以120秒TTL写入；更新数据库成功后删除缓存。空值缓存30秒，过期时间增加随机偏移。验证：首次读取查库，第二次命中；更新后删除键并读到新值；无记录返回不存在且短期缓存空值。对比Redis缓存与ConcurrentHashMap，选择前者用于多实例一致读取；未验证跨系统事务，不声称强一致。'
      : /mysql|sql|索引/i.test(practiceText)
        ? 'SQL练习：CREATE TABLE course(id BIGINT PRIMARY KEY,name VARCHAR(60),status INT); CREATE INDEX ix_status_id ON course(status,id); 查询SELECT id,name FROM course WHERE status=1 ORDER BY id LIMIT 20。记录EXPLAIN的key、rows与Extra，并比较索引前后扫描路径。边界：无记录返回空集合，状态过滤正确，分页不重复。分析：索引按过滤条件和排序组织，列顺序改变会影响使用方式；写入成本增加，未声称生产性能倍数。'
        : /spring|接口/i.test(practiceText)
          ? '接口实现：@RestController class CourseController { @GetMapping("/courses/{id}") public Course get(@PathVariable Long id) { if(id<=0) throw new IllegalArgumentException("id"); return service.find(id); } }。使用统一异常处理返回400或404，用MockMvc验证正常请求、非法id和不存在课程。测试矩阵：id=1=>200及课程字段；id=0=>400；id=999=>404。区分Controller、Service、Repository职责，避免控制器直接拼SQL；本练习使用合成课程数据，不声称真实用户规模。'
          : /vue|前端|react|javascript/i.test(practiceText)
            ? '界面练习：列表加载按ID更新状态，输入变化清空旧请求关联；旧请求完成时先核对请求序号再写入，失败保留输入并提供重试。测试：空列表有状态提示，加载中按钮防重复，慢请求不覆盖新搜索结果，320px无横向溢出。记录正常、空数据、503和迟到响应四类测试，并说明组件职责和可访问标签。'
            : '实现输入校验练习：function validate(id){if(!Number.isInteger(id)||id<=0)return {status:400};return {status:200,id}}。验证矩阵：输入1=>200；输入0=>400；输入null=>400；输入字符串=>400。逐项核对预期与实际输出，区分单元逻辑与真实接口集成；本成果仅提供合成输入的可复核实践说明。'
    const initialEvidenceDescription = `合成验收练习：${learningTask.title}。目标技能：${learningTask.skillGap}。练习目标与操作：${learningTask.description}。个人职责：独立完成方案设计、关键逻辑实现、验证用例和复盘。成果形式：${learningTask.practiceDeliverable}。验收要求：${learningTask.acceptanceCriteria}。具体成果：${implementation}。每个用例记录输入、期望输出、实际输出和是否通过。复盘：记录适用范围、异常分支、权衡和尚未验证的限制。未测量真实生产性能，不声称性能提升。`
    let evidence = await api(`/api/ai/learning/plans/${learningPlan.planId}/tasks/${learningTask.taskId}/evidence`, 'POST', { description: initialEvidenceDescription, links: [] })
    if (evidence.status === 'NEEDS_REVISION') {
      const improvedEvidence = `${initialEvidenceDescription}\n根据反馈补充：${(evidence.evaluation?.gaps || []).join('；')}。具体操作说明：将练习拆为输入准备、核心处理、异常分支和输出验证；对每条验收项逐项核对，保留执行截图的文本转录、测试矩阵和关键逻辑。测试矩阵：正常输入=>返回预期结果（通过）；空输入=>拒绝并提示（通过）；重复输入=>同一结果（通过）；非法输入=>失败状态可识别（通过）。关键逻辑：检查输入完整性；按目标规则处理；记录分支和输出；断言输出与预期一致。当前资料提供说明与测试记录，外部代码仓库尚未提供。`
      evidence = await api(`/api/ai/learning/plans/${learningPlan.planId}/tasks/${learningTask.taskId}/evidence`, 'POST', { description: improvedEvidence, links: [] })
    }
    check('learning evidence receives real successful evaluation', evidence.status === 'SUCCEEDED' && !evidence.evaluation.mocked && evidence.evaluation.score >= 70, { status: evidence.status, score: evidence.evaluation?.score, mocked: evidence.evaluation?.mocked })
    const unconfirmedSources = await api(`/api/ai/interview/sessions/sources?resumeId=${resumeId}&jobId=${context.jobId}&matchId=${match.matchId}`)
    check('unconfirmed evidence excluded from project options', !unconfirmedSources.some(item => item.sourceId.includes(evidence.evidenceId)))
    evidence = await api(`/api/ai/learning/plans/${learningPlan.planId}/tasks/${learningTask.taskId}/evidence/${evidence.evidenceId}/accept`, 'POST')
    const evidenceSources = await api(`/api/ai/interview/sessions/sources?resumeId=${resumeId}&jobId=${context.jobId}&matchId=${match.matchId}`)
    const evidenceSource = evidenceSources.find(item => item.sourceId.includes(evidence.evidenceId))
    check('confirmed successful evidence becomes project source', Boolean(evidenceSource))
    const evidenceInterview = await startInBrowser(context, { mode: 'COACHING', sourceType: 'PROJECT', sourceLabel: evidenceSource.label, count: 1 })
    check('learning source retains actual submitted content', evidenceInterview.sourceMaterial.includes(evidence.description) && evidenceInterview.sourceReferences.some(item => item.kind === 'LEARNING_EVIDENCE'))

    await navigateSession(coaching.sessionId, 'report')
    await page.locator('[data-testid="interview-action-preview"][data-action-type="LEARNING"]').first().click()
    await page.getByTestId('interview-action-confirm').waitFor()
    check('learning action preview leaves original plan active', (await api(`/api/ai/learning/plans/${learningPlan.planId}`)).status === 'ACTIVE')
    await snap('04-learning-action-preview')
    await clickAndWait(page.getByTestId('interview-action-confirm'), `/sessions/${coaching.sessionId}/next-actions/confirm`)
    await page.waitForURL('**/student/plan/review?*', { timeout: 150000 })
    const createdPlanId = new URL(page.url()).searchParams.get('revisionId')
    check('learning confirmation opens adjustment preview route', Boolean(createdPlanId))
    const learningDraft = await api(`/api/ai/learning/plans/${createdPlanId}`)
    check('learning confirmation creates DRAFT, original remains active', learningDraft.status === 'DRAFT' && (await api(`/api/ai/learning/plans/${learningPlan.planId}`)).status === 'ACTIVE')
    check('learning adjustment preserves submitted evidence', (await api(`/api/ai/learning/plans/${learningPlan.planId}/tasks/${learningTask.taskId}/evidence`)).some(item => item.evidenceId === evidence.evidenceId && item.confirmed))

    const other = await api('/api/auth/register', 'POST', { username: username + '_other', password, displayName: '隔离测试', role: 'STUDENT' }, null)
    for (const route of [`/api/ai/interview/sessions/${coaching.sessionId}`, `/api/ai/interview/sessions/${coaching.sessionId}/next-actions`]) {
      const denied = await api(route, 'GET', undefined, other.token, true)
      check(`ownership denies ${route.split('/').pop()}`, denied.code !== 0)
    }
    const deniedCandidate = await api('/api/resumes/master-profile/interview-candidate', 'POST', { sessionId: coaching.sessionId, questionId: q1, attemptId: selectedAttempt.attemptId }, other.token, true)
    check('foreign interview candidate rejected', deniedCandidate.code !== 0)

    await navigateSession(coaching.sessionId, 'report')
    await page.getByTestId('interview-action-preview').first().click()
    await page.getByTestId('interview-action-confirm').waitFor()
    check('browser presents action impact before confirmation', true)
    await snap('04-action-preview')
    await clickAndWait(page.getByTestId('interview-action-confirm'), `/sessions/${coaching.sessionId}/next-actions/confirm`)
    await page.waitForURL('**/student/interview/practice?*', { timeout: 150000 })
    check('browser action confirmation completes', !(await page.getByTestId('interview-action-confirm').count()) || await page.getByTestId('interview-action-confirm').isDisabled())

    for (const role of roles) {
      for (const mode of ['COACHING', 'MOCK']) {
        const roleSession = await create({ targetRole: role.name, jobId: role.job.jobId, mode, sourceType: 'JOB', sourceId: role.job.jobId, questionCount: 1, timerMinutes: mode === 'MOCK' ? 5 : null })
        const roleReport = await finishAll(roleSession)
        check(`${role.name} ${mode} real workflow`, roleReport.reportType === 'FINAL' && roleReport.questionFeedback.length > 0 && !roleReport.mocked, { mocked: roleReport.mocked })
      }
    }

    const expiredMock = { ...mock, status: 'IN_PROGRESS', report: null, partialReport: null,
      timer: { ...mock.timer, accumulatedSeconds: 1201, timerMinutes: 20, timeoutReached: true, runningSince: new Date().toISOString() },
      accumulatedSeconds: 1201, timerMinutes: 20, timeoutReached: true }
    const expiredRoute = `**/api/ai/interview/sessions/${mock.sessionId}`
    await page.route(expiredRoute, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: expiredMock }) }))
    await navigateSession(mock.sessionId)
    await page.getByText('已到计划时间，可以继续回答。', { exact: true }).waitFor()
    check('expired timer displays continue reminder (UI fixture)', await page.getByTestId('interview-final-report').isVisible(), { fixture: 'browser-only timeout state' })
    await page.unroute(expiredRoute)

    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(config.baseUrl + '/student/interview')
      await page.getByTestId('interview-start').waitFor()
      await layout(`${width}px start`)
      await snap(`04-start-${width}`)
      for (const [view, id] of [['practice', mock.sessionId], ['report', coaching.sessionId]]) {
        await navigateSession(id, view)
        await layout(`${width}px ${view}`)
        await snap(`04-${view}-${width}`)
      }
      await page.goto(config.baseUrl + '/student/interview/history')
      await page.getByTestId('interview-history').waitFor()
      await layout(`${width}px history`)
      await snap(`05-history-${width}`)
    }
    await page.goto(config.baseUrl + '/student/interview/practice?sessionId=invalid-interview')
    await page.getByTestId('interview-unavailable').waitFor()
    check('invalid session never silently selects another', true)
    check('no uncaught browser errors', errors.length === 0, { errors })
    return { status: 'PASSED', runId: config.runId, startedAt: config.runId, completedAt: new Date().toISOString(), account: { username, userId: auth.userId, token: auth.token }, checks, screenshots, sessions, browserErrors: errors.length,
      recovery: { coachingSessionId: coaching.sessionId, mockSessionId: mock.sessionId, learningPlanId: learningPlan.planId, learningDraftId: createdPlanId, taskId: learningTask.taskId, evidenceId: evidence.evidenceId, selectedAttemptId: selectedAttempt.attemptId } }
  } catch (error) {
    await snap('failure')
    return { status: 'FAILED', runId: config.runId, completedAt: new Date().toISOString(), account: { username, userId: auth?.userId, token: auth?.token }, checks, screenshots, sessions, browserErrors: errors.length, error: error.message }
  }
}

try {
  fs.writeFileSync(runner, `async (page) => (${browserSuite.toString()})(page, ${JSON.stringify(options)})`, 'utf8')
  cli(['open', options.baseUrl + '/login', ...(process.env.E2E_HEADLESS === '1' ? [] : ['--headed'])])
  const output = cli(['run-code', `--filename=${runner}`])
  const resultText = output.split('### Result\n')[1]?.split('\n### ')[0]?.trim()
  if (!resultText) throw new Error('Browser did not return a structured result')
  const report = JSON.parse(resultText)
  fs.writeFileSync(path.join(artifacts, 'report.json'), JSON.stringify(report, null, 2), 'utf8')
  console.log(`${report.status}: ${report.checks.length} checks; ${report.screenshots.length} screenshots; ${report.browserErrors} browser errors`)
  console.log(`Report: ${path.join(artifacts, 'report.json')}`)
  if (report.status !== 'PASSED') throw new Error(report.error)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
} finally {
  try { cli(['close']) } catch { /* Keep the original test result. */ }
  if (fs.existsSync(runner)) fs.unlinkSync(runner)
}
