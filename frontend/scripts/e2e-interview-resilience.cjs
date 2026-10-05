const { spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '../..')
const reportPath = process.env.E2E_INTERVIEW_REPORT || path.join(root, 'output/playwright/interview-workspace/release-20261006/report.json')
const sourceReport = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
const account = sourceReport.account || {}
if (!account.username || !account.token) throw new Error(`Report has no reusable account: ${reportPath}`)
const options = {
  baseUrl: (process.env.E2E_INTERVIEW_BASE_URL || 'http://localhost').replace(/\/+$/, ''),
  apiUrl: (process.env.E2E_INTERVIEW_API_URL || 'http://localhost:18080').replace(/\/+$/, ''),
  artifacts: path.resolve(process.env.E2E_INTERVIEW_RESILIENCE_ARTIFACTS_DIR || path.join(root, 'output/playwright/interview-resilience-20261006')),
  reportPath,
  runId: new Date().toISOString().replace(/[:.]/g, '-')
}
const suffix = account.username.replace(/^interview_e2e_/, '')
const password = `InterviewE2e!${suffix}`
options.account = { ...account, password }
const sessionName = `interview-resilience-${Date.now().toString(36)}`
const command = process.platform === 'win32' ? 'npx.cmd' : 'npx'
const runner = path.join(options.artifacts, 'browser-run.js')
fs.mkdirSync(options.artifacts, { recursive: true })

function cli(args) {
  const result = spawnSync(command, ['--yes', '--package', '@playwright/cli', 'playwright-cli', `-s=${sessionName}`, ...args], {
    cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, env: process.env, shell: process.platform === 'win32'
  })
  if (result.error || result.status !== 0) throw result.error || new Error(`Browser command failed (${result.status}): ${result.stderr}`)
  return result.stdout
}

async function resilienceSuite(page, config) {
  const account = config.account
  const checks = []
  const screenshots = []
  const browserErrors = []
  const modelRequests = []
  const controlledEvaluationPaths = new Set()
  const check = (name, passed, detail = {}) => {
    checks.push({ name, passed: Boolean(passed), ...detail })
    if (!passed) throw new Error(`Resilience failed: ${name}`)
  }
  const api = async (route, method = 'GET', data, token = account.token, allowFailure = false) => {
    const response = await page.request.fetch(config.apiUrl + route, {
      method, data, timeout: 90000,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
    })
    let body
    try { body = await response.json() } catch { throw new Error(`Non-JSON response: ${route} (${response.status()})`) }
    if (allowFailure) return { httpStatus: response.status(), ...body }
    if (!response.ok() || body.code !== 0 || body.data == null) throw new Error(`${route}: ${response.status()} ${body.message || 'missing data'}`)
    return body.data
  }
  const snap = async name => {
    const file = `${config.artifacts}/${name}.png`
    await page.screenshot({ path: file, fullPage: true })
    screenshots.push(file)
  }
  const waitView = async testId => {
    await page.getByTestId(testId).waitFor({ timeout: 90000 })
    await page.waitForFunction(id => !document.querySelector(`[data-testid="${id}"] .el-loading-mask`), testId)
  }
  const login = async () => {
    await page.goto(`${config.baseUrl}/login`)
    await page.locator('input[autocomplete="username"]').fill(account.username)
    await page.locator('input[type="password"]').fill(account.password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/student/**', { timeout: 90000 })
  }
  let draftState
  try {
    page.setDefaultTimeout(90000)
    page.on('pageerror', error => browserErrors.push(error.message))
    page.on('request', request => {
      const pathname = new URL(request.url()).pathname
      if (request.method() === 'POST' && (/\/api\/ai\/interview\/sessions$/.test(pathname)
        || /\/evaluate$|\/finish$|\/partial-report$|\/next-actions\/confirm$/.test(pathname))) {
        modelRequests.push({ path: pathname, controlled: controlledEvaluationPaths.has(pathname) })
      }
    })
    const sessions = await api('/api/ai/interview/sessions')
    const session = sessions.find(item => item.status !== 'COMPLETED') || sessions[0]
    if (!session) throw new Error('No reusable interview session in supplied report account')
    const sessionId = session.sessionId
    const sessionSnapshot = await api(`/api/ai/interview/sessions/${sessionId}`)
    const matchQuery = session.matchId ? `?matchId=${encodeURIComponent(session.matchId)}` : ''
    await login()

    // Controlled outage: this is a browser fixture around the real sources request.
    await page.goto(`${config.baseUrl}/student/interview${matchQuery}`)
    await waitView('interview-start')
    const sourceRoute = '**/api/ai/interview/sessions/sources**'
    await page.route(sourceRoute, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: 'E2E controlled sources outage' }) }))
    await page.reload()
    await page.getByText('E2E controlled sources outage', { exact: false }).waitFor()
    check('sources outage shows a retry state', await page.locator('.inline-error').getByRole('button', { name: '重试', exact: true }).count() > 0, { kind: 'controlled browser outage' })
    await page.unroute(sourceRoute)
    const sourcesResponse = page.waitForResponse(response => response.url().includes('/api/ai/interview/sessions/sources') && response.request().method() === 'GET')
    await page.locator('.inline-error').getByRole('button', { name: '重试', exact: true }).click()
    await sourcesResponse
    await page.waitForFunction(() => !document.querySelector('.inline-error'))
    check('sources retry recovers from controlled outage', await page.getByTestId('interview-start-button').isEnabled(), { kind: 'real API after fixture removed' })
    await snap('01-sources-retry')

    // Delayed old-session response: same-owner snapshot is used only as a UI race fixture.
    const lateId = `IS-fixture-late-${Date.now()}`
    const lateRoute = `**/api/ai/interview/sessions/${lateId}`
    let releaseOld
    let oldRequested
    const oldGate = new Promise(resolve => { releaseOld = resolve })
    const oldStarted = new Promise(resolve => { oldRequested = resolve })
    await page.route(lateRoute, async route => {
      oldRequested()
      await oldGate
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: { ...sessionSnapshot, sessionId: lateId,
        questions: sessionSnapshot.questions.map(question => ({ ...question, question: `LATE RESPONSE FIXTURE: ${question.question}` })) } }) })
    })
    await page.goto(`${config.baseUrl}/student/interview/practice?sessionId=${lateId}`)
    await oldStarted
    await page.getByRole('link', { name: '历史', exact: true }).click()
    await waitView('interview-history')
    await page.locator(`[data-session-id="${sessionId}"]`).click()
    await waitView('interview-practice')
    await page.locator('.question-text').waitFor()
    const currentQuestion = await page.locator('.question-text').textContent()
    const oldResponse = page.waitForResponse(response => response.url().endsWith(`/api/ai/interview/sessions/${lateId}`))
    releaseOld()
    await oldResponse
    await page.waitForTimeout(200)
    check('late old-session response cannot replace current session', new URL(page.url()).searchParams.get('sessionId') === sessionId && await page.locator('.question-text').textContent() === currentQuestion && !(await page.getByText('LATE RESPONSE FIXTURE:', { exact: false }).count()), { kind: 'controlled delayed same-owner snapshot' })
    await page.unroute(lateRoute)
    await snap('02-late-session-ignored')

    // A real saved answer is retained; only its evaluation response is replaced.
    const savedQuestion = sessionSnapshot.questions.find(question => !question.followUp && sessionSnapshot.attempts?.some(attempt => attempt.questionId === question.questionId))
    if (!savedQuestion) throw new Error('No saved main-question attempt available for fallback fixture')
    const savedAttempts = sessionSnapshot.attempts.filter(attempt => attempt.questionId === savedQuestion.questionId)
    const currentAttempt = savedAttempts.at(-1)
    const fallbackFeedback = { ...(currentAttempt.evaluation || {}), questionId: savedQuestion.questionId,
      mocked: true, score: 50, summary: '受控规则兜底评价：模型评价失败，原回答保留。',
      strengths: ['回答已保存'], gaps: ['模型评价暂不可用'], suggestions: ['重试原回答评价'],
      rubricVersion: 'interview-four-dimensions-v2' }
    const fallbackId = `IS-fixture-fallback-${Date.now()}`
    let fallbackSession = { ...sessionSnapshot, sessionId: fallbackId, mode: 'COACHING', status: 'IN_PROGRESS',
      report: null, partialReport: null, partialReports: [], pausedAt: null, timerMinutes: null,
      timer: { ...(sessionSnapshot.timer || {}), pausedAt: null, timerMinutes: null },
      questions: [savedQuestion], attempts: savedAttempts.map(attempt => attempt.attemptId === currentAttempt.attemptId
        ? { ...attempt, evaluationStatus: 'FAILED', evaluation: fallbackFeedback, evaluationError: 'AI temporarily unavailable (controlled fixture)' } : attempt),
      answers: [{ ...currentAttempt, evaluationStatus: 'FAILED', evaluation: fallbackFeedback }] }
    const fallbackRoute = `**/api/ai/interview/sessions/${fallbackId}`
    const retryPath = `/api/ai/interview/sessions/${fallbackId}/questions/${savedQuestion.questionId}/attempts/${currentAttempt.attemptId}/evaluate`
    const retryRoute = `**${retryPath}`
    const immutableAttempts = JSON.stringify(fallbackSession.attempts.map(attempt => ({ attemptId: attempt.attemptId, answer: attempt.answer, attemptNo: attempt.attemptNo, submittedAt: attempt.submittedAt })))
    let retryRequests = 0
    controlledEvaluationPaths.add(retryPath)
    await page.route(fallbackRoute, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: fallbackSession }) }))
    await page.route(retryRoute, route => {
      if (route.request().method() !== 'POST') return route.abort()
      retryRequests++
      const feedback = { ...fallbackFeedback, mocked: false, score: 80, summary: '受控成功评价：原回答评价重试已完成。', gaps: [], suggestions: [] }
      fallbackSession = { ...fallbackSession,
        attempts: fallbackSession.attempts.map(attempt => attempt.attemptId === currentAttempt.attemptId
          ? { ...attempt, evaluationStatus: 'SUCCEEDED', evaluation: feedback, evaluationError: null } : attempt),
        answers: [{ ...currentAttempt, evaluationStatus: 'SUCCEEDED', evaluation: feedback }] }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: {
        sessionId: fallbackId, questionId: savedQuestion.questionId, attemptId: currentAttempt.attemptId, status: 'SUCCEEDED', feedback, error: null
      } }) })
    })
    await page.goto(`${config.baseUrl}/student/interview/practice?sessionId=${fallbackId}`)
    await waitView('interview-practice')
    await page.getByText(fallbackFeedback.summary, { exact: true }).waitFor()
    check('FAILED evaluation still displays preserved rule fallback', await page.locator('.question-feedback').isVisible(), { kind: 'controlled FAILED plus rule evaluation snapshot' })
    const retryButton = page.getByTestId('interview-evaluate-attempt')
    check('FAILED evaluation with rule fallback remains retryable', await retryButton.isVisible() && await retryButton.isEnabled(), { kind: 'controlled FAILED plus rule evaluation snapshot' })
    check('FAILED fallback keeps immutable original answer visible', await page.locator('#interview-answer').inputValue() === currentAttempt.answer, { kind: 'real answer inside controlled snapshot' })
    await snap('03-fallback-failed-retry-visible')
    const retryResponse = page.waitForResponse(response => new URL(response.url()).pathname === retryPath && response.request().method() === 'POST')
    await retryButton.click()
    const retryResult = await (await retryResponse).json()
    await page.getByText('受控成功评价：原回答评价重试已完成。', { exact: true }).waitFor()
    check('fallback evaluation retry succeeds without saving answer', retryResult.data.status === 'SUCCEEDED' && retryRequests === 1 && !(await retryButton.count()), { kind: 'controlled successful retry response' })
    check('retry retains every original immutable attempt', immutableAttempts === JSON.stringify(fallbackSession.attempts.map(attempt => ({ attemptId: attempt.attemptId, answer: attempt.answer, attemptNo: attempt.attemptNo, submittedAt: attempt.submittedAt }))) && await page.locator('#interview-answer').inputValue() === currentAttempt.answer, { kind: 'controlled response with original attempts preserved' })
    await snap('03-fallback-retry-succeeded')
    await page.unroute(retryRoute)
    await page.unroute(fallbackRoute)

    // Mock mode isolation: no real session is created; only a controlled UI fixture is served.
    const mockFixtureId = `IS-fixture-mock-${Date.now()}`
    const mockFixture = { ...fallbackSession, sessionId: mockFixtureId, mode: 'MOCK', status: 'IN_PROGRESS', report: null, partialReport: null,
      timer: { ...(sessionSnapshot.timer || {}), timerMinutes: 20, pausedAt: null }, timerMinutes: 20,
      questions: [savedQuestion], answers: [{ ...currentAttempt, evaluationStatus: 'FAILED', evaluation: fallbackFeedback }],
      attempts: savedAttempts.map(attempt => ({ ...attempt, evaluationStatus: 'FAILED', evaluation: fallbackFeedback })) }
    const mockRoute = `**/api/ai/interview/sessions/${mockFixtureId}`
    await page.route(mockRoute, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: mockFixture }) }))
    await page.goto(`${config.baseUrl}/student/interview/practice?sessionId=${mockFixtureId}`)
    await waitView('interview-practice')
    check('mock fixture hides coaching reference points', !(await page.locator('.reference-points,[data-testid="interview-reference"]').count()), { kind: 'controlled UI fixture' })
    check('mock fixture hides feedback and reanswer controls', !(await page.locator('.question-feedback,[data-testid="interview-reanswer"]').count()), { kind: 'controlled UI fixture' })
    check('mock fixture hides FAILED fallback evaluation retry', !(await page.getByTestId('interview-evaluate-attempt').count()), { kind: 'controlled MOCK with FAILED rule fallback' })
    check('mock fixture displays mock mode timer', await page.getByText(/模拟面试/).count() > 0 && await page.getByTestId('interview-timer').count() > 0, { kind: 'controlled UI fixture' })
    await snap('03-mock-isolation-fixture')
    await page.unroute(mockRoute)

    // Draft is browser-local by design. Save no answer to the API, log out, log back in, and recover it.
    await page.goto(`${config.baseUrl}/student/interview/practice?sessionId=${encodeURIComponent(sessionId)}`)
    await waitView('interview-practice')
    await page.waitForFunction(() => {
      const input = document.querySelector('#interview-answer')
      return input && !input.readOnly && !input.disabled
    })
    const draftKey = `aicampus.draft.${encodeURIComponent(account.userId)}.interview-practice.${encodeURIComponent(sessionId)}`
    draftState = await page.evaluate(key => ({ key, value: localStorage.getItem(key) }), draftKey)
    const draftText = `未提交草稿恢复检查 ${Date.now()}`
    const answerBox = page.locator('#interview-answer')
    await answerBox.fill(draftText)
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await page.waitForURL('**/login')
    await login()
    await page.goto(`${config.baseUrl}/student/interview/practice?sessionId=${encodeURIComponent(sessionId)}`)
    await waitView('interview-practice')
    await page.waitForFunction(text => document.querySelector('#interview-answer')?.value === text, draftText)
    check('unsubmitted answer draft survives logout and login', await answerBox.inputValue() === draftText, { kind: 'real browser local draft' })
    check('draft recovery did not create a server attempt', !(await api(`/api/ai/interview/sessions/${sessionId}`)).attempts?.some(item => item.answer === draftText), { kind: 'real API' })
    await snap('04-draft-restored-after-login')

    check('resilience suite submits no billable model-generating requests', modelRequests.every(request => request.controlled) && retryRequests === 1, { requests: modelRequests, controlledRetries: retryRequests })
    check('no uncaught browser errors', browserErrors.length === 0, { errors: browserErrors })
    return { status: 'PASSED', runId: config.runId, startedAt: config.runId, completedAt: new Date().toISOString(), account: { username: account.username, userId: account.userId }, sessionId, checks, screenshots, browserErrors: browserErrors.length,
      fixtures: ['controlled sources 503', 'controlled delayed old session', 'controlled FAILED rule fallback and retry', 'controlled MOCK snapshot'] }
  } catch (error) {
    await snap('failure')
    return { status: 'FAILED', runId: config.runId, completedAt: new Date().toISOString(), account: { username: account.username, userId: account.userId }, checks, screenshots, browserErrors: browserErrors.length, error: error.message }
  } finally {
    if (draftState) {
      await page.goto(`${config.baseUrl}/student/interview/history`)
      await waitView('interview-history')
      await page.evaluate(state => {
        if (state.value == null) localStorage.removeItem(state.key)
        else localStorage.setItem(state.key, state.value)
      }, draftState)
    }
  }
}

try {
  fs.writeFileSync(runner, `async (page) => (${resilienceSuite.toString()})(page, ${JSON.stringify(options)})`, 'utf8')
  cli(['open', options.baseUrl + '/login', ...(process.env.E2E_HEADLESS === '1' ? [] : ['--headed'])])
  const output = cli(['run-code', `--filename=${runner}`])
  const resultText = output.split('### Result\n')[1]?.split('\n### ')[0]?.trim()
  if (!resultText) throw new Error('Browser did not return a structured result')
  const report = JSON.parse(resultText)
  fs.writeFileSync(path.join(options.artifacts, 'report.json'), JSON.stringify(report, null, 2), 'utf8')
  console.log(`${report.status}: ${report.checks.length} checks; ${report.screenshots.length} screenshots; ${report.browserErrors} browser errors`)
  console.log(`Report: ${path.join(options.artifacts, 'report.json')}`)
  if (report.status !== 'PASSED') throw new Error(report.error)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
} finally {
  try { cli(['close']) } catch { /* Keep the original test result. */ }
  if (fs.existsSync(runner)) fs.unlinkSync(runner)
}
