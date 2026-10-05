const { spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '../..')
const parentReportPath = path.resolve(process.env.E2E_INTERVIEW_PARENT_REPORT || path.join(root, 'output/playwright/interview-workspace/release-20261006-verified/report.json'))
const parentReport = JSON.parse(fs.readFileSync(parentReportPath, 'utf8'))
if (!parentReport.account?.token || !parentReport.account?.username) throw new Error('Parent report has no reusable test account')
const options = {
  baseUrl: (process.env.E2E_INTERVIEW_BASE_URL || 'http://localhost').replace(/\/+$/, ''),
  apiUrl: (process.env.E2E_INTERVIEW_API_URL || 'http://localhost:18080').replace(/\/+$/, ''),
  artifacts: path.resolve(process.env.E2E_INTERVIEW_CONTINUATION_ARTIFACTS_DIR || path.join(root, 'output/playwright/interview-workspace/release-20261006-ui-verified')),
  runId: new Date().toISOString().replace(/[:.]/g, '-'),
  parentReport: parentReportPath,
  inheritedChecks: (parentReport.checks || []).filter(check => check.passed).map(check => ({ ...check, inherited: true, sourceReport: parentReportPath })),
  account: { ...parentReport.account, password: `InterviewE2e!${parentReport.account.username.replace(/^interview_e2e_/, '')}` }
}
if (path.join(options.artifacts, 'report.json') === parentReportPath) throw new Error('Continuation output must not replace the parent report')
const sessionName = `interview-continuation-${Date.now().toString(36)}`
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

async function continuationSuite(page, config) {
  const checks = []
  const screenshots = []
  const browserErrors = []
  const modifyingRequests = []
  const account = config.account
  const check = (name, passed, detail = {}) => {
    checks.push({ name, passed: Boolean(passed), ...detail })
    if (!passed) throw new Error(`Continuation failed: ${name}`)
  }
  const api = async route => {
    const response = await page.request.get(config.apiUrl + route, { timeout: 90000, headers: { Authorization: `Bearer ${account.token}` } })
    const body = await response.json()
    if (!response.ok() || body.code !== 0 || body.data == null) throw new Error(`${route}: ${response.status()} ${body.message || 'missing data'}`)
    return body.data
  }
  const snap = async name => {
    const file = `${config.artifacts}/${name}.png`
    await page.screenshot({ path: file, fullPage: true })
    screenshots.push(file)
  }
  const waitView = async view => {
    await page.getByTestId(`interview-${view}`).waitFor()
    await page.waitForFunction(id => !document.querySelector(`[data-testid="${id}"] .el-loading-mask`), `interview-${view}`)
    if (view === 'practice') await page.locator('.question-text').waitFor()
    if (view === 'report') await page.locator('.report-header').waitFor()
    if (view === 'history') await page.locator('.record').first().waitFor()
    if (view === 'start') {
      await page.getByTestId('interview-start-button').waitFor()
      await page.waitForFunction(() => !document.querySelector('[data-testid="interview-start-button"]')?.disabled)
    }
  }
  const layout = async (name, view) => {
    const geometry = await page.evaluate(async currentView => {
      const rootScroller = document.scrollingElement
      const candidates = [...new Set([rootScroller, ...document.querySelectorAll('*')])].filter(element =>
        element && element.clientHeight > 0 && element.getClientRects().length
        && element.scrollHeight > element.clientHeight + 2
        && (element === rootScroller || /auto|scroll/.test(getComputedStyle(element).overflowY)))
      for (const element of candidates) element.scrollTop = element.scrollHeight
      await new Promise(resolve => requestAnimationFrame(resolve))
      const section = document.querySelector(`[data-testid="interview-${currentView}"]`)
      const endSelectors = { start: '.launch-footer', practice: '.answer-actions, :scope > .el-button', report: ':scope > .report-details', history: '.record' }
      const end = Array.from(section?.querySelectorAll(endSelectors[currentView]) || []).at(-1)
      const rect = end?.getBoundingClientRect()
      let endVisible = Boolean(rect && rect.top < innerHeight && rect.bottom > 0 && rect.bottom <= innerHeight + 2)
      for (let ancestor = end?.parentElement; endVisible && ancestor; ancestor = ancestor.parentElement) {
        if (/auto|scroll|hidden|clip/.test(getComputedStyle(ancestor).overflowY)) {
          const parentRect = ancestor.getBoundingClientRect()
          if (rect.bottom > parentRect.bottom + 2 || rect.bottom < parentRect.top) endVisible = false
        }
      }
      return { viewport: innerWidth, height: innerHeight, content: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth), endVisible, endBottom: rect?.bottom,
        scrollContainers: candidates.map(element => ({ root: element === rootScroller, className: element.className, overflowY: getComputedStyle(element).overflowY, top: element.scrollTop, maximum: element.scrollHeight - element.clientHeight })) }
    }, view)
    check(`${name}: no horizontal overflow`, geometry.content <= geometry.viewport + 1, geometry)
    check(`${name}: scroll can reach end`, geometry.scrollContainers.every(item => Math.abs(item.top - item.maximum) <= 2), geometry)
    check(`${name}: final controls are reachable`, geometry.endVisible, geometry)
    await snap(`${name}-bottom`)
    await page.evaluate(() => { window.scrollTo(0, 0); for (const element of document.querySelectorAll('*')) if (element.scrollTop) element.scrollTop = 0 })
    await snap(`${name}-top`)
  }
  let recovery
  try {
    page.setDefaultTimeout(90000)
    page.on('pageerror', error => browserErrors.push(error.message))
    page.on('request', request => {
      const pathname = new URL(request.url()).pathname
      if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && !/^\/api\/auth\/login$/.test(pathname)) modifyingRequests.push({ path: pathname, method: request.method() })
    })
    const [sessions, plans] = await Promise.all([api('/api/ai/interview/sessions'), api('/api/ai/learning/plans')])
    const coaching = sessions.filter(item => item.mode === 'COACHING' && item.status === 'COMPLETED' && item.partialReports?.length)
      .sort((left, right) => right.attempts.length - left.attempts.length)[0]
    const mock = sessions.find(item => item.mode === 'MOCK' && item.status === 'COMPLETED' && item.questions.filter(question => !question.followUp).length === 4)
    const activePlan = plans.find(plan => plan.status === 'ACTIVE')
    const draftPlan = activePlan && plans.find(plan => plan.status === 'DRAFT' && plan.revisionOfPlanId === activePlan.planId)
    check('recover completed coaching with reanswer and partial history', Boolean(coaching?.report && coaching.attempts.length > coaching.answers.length))
    check('recover completed four-question mock', Boolean(mock?.report))
    check('recover active plan and unconfirmed adjustment draft', Boolean(activePlan && draftPlan))
    const histories = await Promise.all(activePlan.tasks.map(async task => ({ task, entries: await api(`/api/ai/learning/plans/${activePlan.planId}/tasks/${task.taskId}/evidence`) })))
    const confirmedHistory = histories.find(history => history.entries.some(entry => entry.confirmed))
    const evidence = confirmedHistory?.entries.find(entry => entry.confirmed)
    check('recover student-confirmed learning evidence', Boolean(evidence))
    const firstQuestion = coaching.questions.find(question => !question.followUp)
    const adoptedAttempt = coaching.attempts.find(attempt => attempt.questionId === firstQuestion.questionId && attempt.selectedForReport)
    check('recover selected reanswer version', Boolean(adoptedAttempt && adoptedAttempt.attemptNo > 1))
    recovery = { coachingSessionId: coaching.sessionId, mockSessionId: mock.sessionId,
      learningPlanId: activePlan.planId, learningDraftId: draftPlan.planId,
      taskId: confirmedHistory.task.taskId, evidenceId: evidence.evidenceId, selectedAttemptId: adoptedAttempt.attemptId }

    await page.goto(config.baseUrl + '/login')
    await page.locator('input[autocomplete="username"]').fill(account.username)
    await page.locator('input[type="password"]').fill(account.password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/student/**')
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 900 })
      for (const view of ['start', 'practice', 'report', 'history']) {
        const route = view === 'start' ? '/student/interview' : `/student/interview/${view}`
        const sessionId = view === 'practice' ? mock.sessionId : view === 'report' ? coaching.sessionId : null
        await page.goto(`${config.baseUrl}${route}${sessionId ? `?sessionId=${encodeURIComponent(sessionId)}` : ''}`)
        await waitView(view)
        await layout(`${width}px-${view}`, view)
      }
    }
    await page.locator(`[data-session-id="${coaching.sessionId}"]`).click()
    await waitView('report')
    await page.locator('.question-reports > details > summary').first().click()
    const questionReport = page.locator('.question-reports > details').first()
    const feedbackPanel = questionReport.locator('.feedback-panel')
    const sourceFeedback = coaching.report.questionFeedback[0]
    check('feedback shows score and summary without expanded details', await feedbackPanel.locator('header').isVisible() && await feedbackPanel.locator('.feedback-summary').isVisible())
    check('feedback initially hides dimensions, citations and model version', !(await feedbackPanel.locator('.dimensions').isVisible())
      && !(await feedbackPanel.locator('.evidence-list').isVisible()) && !(await feedbackPanel.locator('.feedback-details small').isVisible()))
    await snap('320px-feedback-collapsed')
    const completeFeedback = questionReport.getByText('完整评价与依据', { exact: true })
    await completeFeedback.click()
    check('mobile report expands full feedback on demand', await feedbackPanel.locator('.dimensions').isVisible()
      && await feedbackPanel.locator('.dimensions article').count() === (sourceFeedback.dimensions || []).length)
    check('expanded feedback retains all original answer citations', await feedbackPanel.locator('.evidence-list blockquote').count() === (sourceFeedback.evidence || []).length
      && (sourceFeedback.evidence || []).every(note => !note.quote || (coaching.attempts.find(attempt => attempt.questionId === sourceFeedback.questionId && attempt.selectedForReport)?.answer || '').includes(note.quote)))
    check('expanded feedback exposes rubric and model version', await feedbackPanel.locator('.feedback-details small').isVisible()
      && (await feedbackPanel.locator('.feedback-details small').textContent()).includes(sourceFeedback.rubricVersion))
    await layout('320px-report-expanded', 'report')

    await page.goto(config.baseUrl + '/student/interview/practice?sessionId=invalid-interview')
    await page.getByTestId('interview-unavailable').waitFor()
    check('invalid session never silently selects another session', true)
    check('UI continuation sends no modifying business requests', modifyingRequests.length === 0, { requests: modifyingRequests })
    check('no uncaught browser errors in continuation', browserErrors.length === 0, { errors: browserErrors })
    return { status: 'PASSED', runId: config.runId, completedAt: new Date().toISOString(), parentReport: config.parentReport,
      inheritedCheckCount: config.inheritedChecks.length, continuationCheckCount: checks.length,
      account: { username: account.username, userId: account.userId, token: account.token },
      checks: [...config.inheritedChecks, ...checks], screenshots, browserErrors: browserErrors.length, recovery,
      note: 'Earlier successful business checks are inherited from parentReport. This continuation performs real GETs and browser UI checks only; no paid model request is repeated.' }
  } catch (error) {
    await snap('failure')
    return { status: 'FAILED', runId: config.runId, completedAt: new Date().toISOString(), parentReport: config.parentReport,
      inheritedCheckCount: config.inheritedChecks.length, continuationCheckCount: checks.length, checks,
      account: { username: account.username, userId: account.userId }, screenshots, browserErrors: browserErrors.length, error: error.message }
  }
}

try {
  fs.writeFileSync(runner, `async (page) => (${continuationSuite.toString()})(page, ${JSON.stringify(options)})`, 'utf8')
  cli(['open', options.baseUrl + '/login', ...(process.env.E2E_HEADLESS === '1' ? [] : ['--headed'])])
  const output = cli(['run-code', `--filename=${runner}`])
  const resultText = output.split('### Result\n')[1]?.split('\n### ')[0]?.trim()
  if (!resultText) throw new Error('Browser did not return a structured result')
  const report = JSON.parse(resultText)
  fs.writeFileSync(path.join(options.artifacts, 'report.json'), JSON.stringify(report, null, 2), 'utf8')
  console.log(`${report.status}: ${report.inheritedCheckCount} inherited; ${report.continuationCheckCount} continuation checks; ${report.screenshots.length} screenshots; ${report.browserErrors} browser errors`)
  console.log(`Report: ${path.join(options.artifacts, 'report.json')}`)
  if (report.status !== 'PASSED') throw new Error(report.error)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
} finally {
  try { cli(['close']) } catch { /* Preserve the original result. */ }
  if (fs.existsSync(runner)) fs.unlinkSync(runner)
}
