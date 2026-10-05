const { spawn, spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

// Node 20 does not expose the WebSocket global unless the experimental
// runtime flag is enabled. Relaunch once so the CDP browser client works in
// both local shells and CI images without requiring an extra dependency.
if (typeof WebSocket === 'undefined' && !process.execArgv.includes('--experimental-websocket')) {
  const relaunched = spawnSync(process.execPath, ['--experimental-websocket', __filename, ...process.argv.slice(2)], {
    stdio: 'inherit',
    env: process.env
  })
  process.exit(relaunched.status || 0)
}

const rootDir = path.resolve(__dirname, '..')
const explicitBaseUrl = Boolean(process.env.E2E_BASE_URL)
const demoMode = ['1', 'true', 'yes', 'on'].includes((process.env.VITE_DEMO_MODE || '').trim().toLowerCase())
const localPort = process.env.E2E_PORT || '5174'
const rawBaseUrl = process.env.E2E_BASE_URL || `http://127.0.0.1:${localPort}`
const baseUrl = rawBaseUrl.replace(/\/+$/, '')
const apiProxyTarget = process.env.E2E_API_PROXY_TARGET || process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
const artifactsDir = process.env.E2E_ARTIFACTS_DIR || path.join(rootDir, '.e2e-artifacts')
const coreFixturePath = path.resolve(rootDir, '../logs/core-mvp-verification.json')
const coreFixturePassword = process.env.MVP_SMOKE_PASSWORD || 'Verification123!'
const persistedTaskFeedback = 'MVP-已完成并保存'
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

let devServer

async function main() {
  fs.mkdirSync(artifactsDir, { recursive: true })
  const reportPath = path.join(artifactsDir, 'smoke-report.json')
  const report = { status: 'RUNNING', startedAt: new Date().toISOString(), baseUrl, browserErrors: 0 }
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8')
  const coreFixture = demoMode ? null : readCoreFixture()
  console.log(`E2E smoke mode: ${demoMode ? 'offline demo UI' : 'live service UI'}`)
  await ensureFrontend()
  const browser = await startBrowser()
  const client = await connect(browser.webSocketDebuggerUrl)
  const runtimeErrors = []
  const stopErrors = client.on('Runtime.exceptionThrown', ({ exceptionDetails }) => {
    runtimeErrors.push(exceptionDetails.exception?.description || exceptionDetails.text)
  })
  try {
    await enablePage(client, 1440, 980)
    await navigate(client, `${baseUrl}/login`)
    await waitForExpression(client, "Boolean(document.querySelector('input[autocomplete=\"username\"]'))")
    await screenshot(client, '00-login-desktop.png')
    if (coreFixture) {
      await verifyCoreFixture(client, coreFixture)
    } else {
      await loginAs(client, 'student', 'STUDENT', '/student/resume')
    }
    await navigate(client, `${baseUrl}/student/resume`)
    await assertText(client, ['Campus Recruit', '简历', '岗位匹配', '学习路径', '模拟面试', '知识库'])
    await assertNoText(client, ['投递记录', '通知中心', '简历闭环'])
    await assertStudentWorkspace(client, 'resume')
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '01-student-resume.png')
    await verifyModuleNavigation(client, coreFixture)
    await verifyModuleNavigationBoundaries(client, coreFixture)

    await verifyGlobalSearch(client)
    await navigate(client, `${baseUrl}/student/resume`)
    await verifyStudentScrolling(client, coreFixture)
    await setViewport(client, 1440, 980)

    await navigate(client, `${baseUrl}/student/plan`)
    await assertStudentWorkspace(client, 'plan')
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '02-student-plan.png')

    await navigate(client, `${baseUrl}/student/jobs`)
    await assertText(client, ['岗位匹配', '岗位列表'])
    await assertStudentWorkspace(client, 'jobs')
    await assertNoHorizontalOverflow(client)
    await verifyJobsNavigation(client, coreFixture)
    await fillInput(client, '.job-search input', '__e2e_no_match__')
    await assertText(client, ['没有符合条件的岗位'])
    await fillInput(client, '.job-search input', '')
    await assertText(client, ['岗位列表'])
    await screenshot(client, '03-student-jobs.png')

    await navigate(client, `${baseUrl}/student/interview`)
    await assertStudentWorkspace(client, 'interview')
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '04-student-interview.png')

    await navigate(client, `${baseUrl}/student/knowledge`)
    await assertStudentWorkspace(client, 'knowledge')
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '05-student-knowledge.png')

    await navigate(client, `${baseUrl}/student/history`)
    await waitForExpression(client, "location.pathname === '/student/interview/history'")
    await assertOnlyModulePage(client, 'interview', 'history')
    await navigate(client, `${baseUrl}/student/deliveries`)
    await waitForExpression(client, "location.pathname === '/student/resume'")

    await setViewport(client, 1024, 900)
    await navigate(client, `${baseUrl}/student/plan`)
    await assertStudentWorkspace(client, 'plan')
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '06-student-plan-1024.png')

    await setViewport(client, 1440, 980)
    await loginAs(client, 'company', 'COMPANY', '/company/jobs')
    await assertText(client, ['岗位管理', '发布岗位'])
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '07-company-jobs.png')
    await navigate(client, `${baseUrl}/company/publish`)
    await assertText(client, ['发布岗位', '岗位信息'])
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '08-company-publish.png')
    await navigate(client, `${baseUrl}/company/screening`)
    await waitForExpression(client, "location.pathname === '/company/jobs'")

    await loginAs(client, 'admin', 'ADMIN', '/admin/ai')
    await assertText(client, ['知识库管理', '知识文档', '手工新增', '上传导入'])
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '09-admin-knowledge.png')
    await navigate(client, `${baseUrl}/admin/accounts`)
    await assertText(client, ['账号管理', '账号筛选', '创建账号', '账号列表'])
    await assertNoHorizontalOverflow(client)
    await assertNoInternalHorizontalOverflow(client, '.list-panel')
    await screenshot(client, '10-admin-accounts.png')
    await navigate(client, `${baseUrl}/admin/overview`)
    await waitForExpression(client, "location.pathname === '/admin/ai'")

    await setViewport(client, 390, 844)
    await navigate(client, `${baseUrl}/login`)
    await screenshot(client, '11-login-mobile.png')
    if (coreFixture) {
      await loginAs(client, coreFixture.studentUsername, 'STUDENT', '/student/resume', coreFixturePassword)
    } else {
      await loginAs(client, 'student', 'STUDENT', '/student/resume')
    }
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '12-student-resume-mobile.png')
    await clickSelector(client, '.mobile-menu')
    await waitForExpression(client, "Boolean(document.querySelector('.side-nav.is-open')) && Boolean(document.querySelector('.nav-backdrop'))")
    await screenshot(client, '13-student-drawer-mobile.png')
    await clickSelector(client, '.nav-backdrop')
    await waitForExpression(client, "!document.querySelector('.side-nav.is-open')")

    for (const [name, expected] of [
      ['resume', ['简历']],
      ['jobs', ['岗位匹配', '岗位列表']],
      ['plan', ['学习路径']],
      ['interview', ['开始辅导练习', '本次目标岗位']],
      ['knowledge', ['仅检索', 'AI 回答', '检索']]
    ]) {
      await navigate(client, `${baseUrl}/student/${name}`)
      await assertText(client, expected)
      await assertStudentWorkspace(client, name)
      await assertNoHorizontalOverflow(client)
      await screenshot(client, `14-student-${name}-mobile.png`)
    }

    await setViewport(client, 320, 900)
    for (const name of ['resume', 'jobs', 'plan', 'interview', 'knowledge']) {
      await navigate(client, `${baseUrl}/student/${name}`)
      await assertStudentWorkspace(client, name)
      await assertNoHorizontalOverflow(client)
      await screenshot(client, `18-student-${name}-320.png`)
    }
    await setViewport(client, 390, 844)

    await loginAs(client, 'company', 'COMPANY', '/company/jobs')
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '19-company-jobs-mobile.png')
    await navigate(client, `${baseUrl}/company/publish`)
    await assertText(client, ['发布岗位', '岗位信息'])
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '20-company-publish-mobile.png')

    await loginAs(client, 'admin', 'ADMIN', '/admin/ai')
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '21-admin-knowledge-mobile.png')
    await navigate(client, `${baseUrl}/admin/accounts`)
    await assertText(client, ['账号管理', '账号列表'])
    await assertNoHorizontalOverflow(client)
    await assertNoInternalHorizontalOverflow(client, '.list-panel')
    await screenshot(client, '22-admin-accounts-mobile.png')

    if (runtimeErrors.length) throw new Error(`Unhandled browser exceptions: ${JSON.stringify(runtimeErrors)}`)
    fs.writeFileSync(reportPath, JSON.stringify({ ...report, status: 'PASSED', completedAt: new Date().toISOString(),
      browserErrors: runtimeErrors.length,
      scrollingChecks: JSON.parse(fs.readFileSync(path.join(artifactsDir, 'student-scroll-results.json'), 'utf8')).length
    }, null, 2), 'utf8')
    console.log(`E2E smoke passed. Screenshots: ${artifactsDir}`)
  } catch (error) {
    try {
      await screenshot(client, 'failure.png')
      fs.writeFileSync(path.join(artifactsDir, 'failure-text.txt'), await bodyText(client), 'utf8')
      fs.writeFileSync(reportPath, JSON.stringify({ ...report, status: 'FAILED', completedAt: new Date().toISOString(),
        browserErrors: runtimeErrors.length, error: error.message
      }, null, 2), 'utf8')
    } catch {
      // Preserve the original failure.
    }
    throw error
  } finally {
    stopErrors()
    client.close()
    await stopBrowser(browser.process)
    if (devServer) {
      await stopProcessTree(devServer)
    }
  }
}

function readCoreFixture() {
  if (!fs.existsSync(coreFixturePath)) {
    return null
  }
  let fixture
  try {
    fixture = JSON.parse(fs.readFileSync(coreFixturePath, 'utf8'))
  } catch (error) {
    throw new Error(`Unable to read core MVP fixture ${coreFixturePath}: ${error.message}`)
  }
  for (const field of ['studentUsername', 'resumeId', 'jobId', 'matchId', 'planId', 'sessionId']) {
    if (typeof fixture[field] !== 'string' || !fixture[field].trim()) {
      throw new Error(`Core MVP fixture is missing ${field}: ${coreFixturePath}`)
    }
  }
  return fixture
}

async function verifyCoreFixture(client, fixture) {
  console.log(`Core MVP fixture detected for ${fixture.studentUsername}; checking persisted student data.`)
  await loginAs(client, fixture.studentUsername, 'STUDENT', '/student/resume', coreFixturePassword)
  const resume = await fetchFixtureData(client, `/api/resumes/${encodeURIComponent(fixture.resumeId)}`)
  const job = await fetchFixtureData(client, `/api/jobs/${encodeURIComponent(fixture.jobId)}`)
  const plan = await fetchFixtureData(client, `/api/ai/learning/plans/${encodeURIComponent(fixture.planId)}`)
  const session = await fetchFixtureData(client, `/api/ai/interview/sessions/${encodeURIComponent(fixture.sessionId)}`)
  const matches = await fetchFixtureData(client, '/api/matches')
  const fixtureMatch = matches.find((match) => match.matchId === fixture.matchId)
  if (!fixtureMatch) {
    throw new Error(`Fixture match was not restored: ${fixture.matchId}`)
  }
  if (plan.status !== 'ACTIVE' || !Number.isInteger(plan.version)) {
    throw new Error(`Fixture plan is not an active version: ${fixture.planId}`)
  }
  const completedTask = plan.tasks?.find((task) => task.status === 'COMPLETED'
    && task.feedback === persistedTaskFeedback)
  if (!completedTask) {
    throw new Error(`Fixture plan does not contain the persisted completed task: ${fixture.planId}`)
  }
  if (session.status !== 'COMPLETED' || !session.report) {
    throw new Error(`Fixture interview session is missing its completed report: ${fixture.sessionId}`)
  }

  await verifyResumeDeleteCancellation(client, fixture, resume)
  await verifyResumeDraftPersistence(client, fixture, resume)
  await verifyMatchHistoryRestoreAndContext(client, fixtureMatch, resume, job)

  await navigate(client, planTaskUrl(plan.planId, completedTask.taskId))
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"plan-task\"]'))")
  await assertPersistedTask(client, persistedTaskFeedback)
  await verifyTaskEvidenceEntry(client, plan, completedTask)
  await screenshot(client, '00-core-fixture-learning-plan.png')
  await verifyTaskSaveFailureRetention(client, plan)
  await verifyPlanHistoryReadOnly(client, plan)

  await navigate(client, `${baseUrl}/student/history`)
  await waitForExpression(client, "location.pathname === '/student/interview/history'")
  await assertText(client, ['模拟面试', '已完成'])
  await navigate(client, `${baseUrl}/student/interview/report?sessionId=${encodeURIComponent(fixture.sessionId)}`)
  await waitForExpression(client, "Boolean(document.querySelector('.report-details'))")
  await setDetailsOpen(client, '.report-details', true)
  await assertText(client, [
    '模拟面试',
    '面试报告',
    String(session.report.overallScore),
    session.report.recommendations[0]
  ])
  await screenshot(client, '00-core-fixture-interview-report.png')
  await verifyCompletedInterviewReadOnly(client, fixture, session)
  await verifyInterviewAnswerDraft(client, session)
  await verifyRetrievalOnlyKnowledge(client)
}

async function selectFixtureResume(client, resume) {
  await waitForExpression(client, "Boolean(document.querySelector('.resume-picker .el-select')) && !document.querySelector('.resume-hero .el-loading-mask')")
  await selectElementPlusOption(client, '.resume-picker .el-select', resume.fileName)
  await waitForExpression(client, `Boolean(document.querySelector('.resume-summary strong')?.innerText.includes(${JSON.stringify(resume.fileName)}))`)
}

async function verifyResumeDeleteCancellation(client, fixture, resume) {
  await navigate(client, `${baseUrl}/student/resume/original/${encodeURIComponent(fixture.resumeId)}`)
  await selectFixtureResume(client, resume)
  await waitForExpression(client, "!document.querySelector('.resume-hero .el-loading-mask')")
  await clickButton(client, '删除该版本')
  await waitForExpression(client, "Boolean(document.querySelector('.el-message-box'))")
  await clickElementContaining(client, '.el-message-box__btns button', '取消')
  await waitForExpression(client, "!document.querySelector('.el-message-box')")
  await waitForExpression(client, `Boolean(document.querySelector('.resume-summary strong')?.innerText.includes(${JSON.stringify(resume.fileName)}))`)
  const restored = await fetchFixtureData(client, `/api/resumes/${encodeURIComponent(fixture.resumeId)}`)
  if (restored.resumeId !== fixture.resumeId) {
    throw new Error('Resume was changed after cancelling the delete confirmation')
  }
  await screenshot(client, '00a-resume-delete-cancelled.png')
}

async function verifyResumeDraftPersistence(client, fixture, resume) {
  const educationSelector = '.profile-form .form-field input'
  const originalUrl = `${baseUrl}/student/resume/original/${encodeURIComponent(fixture.resumeId)}`
  await navigate(client, originalUrl)
  await selectFixtureResume(client, resume)
  await waitForExpression(client, "!document.querySelector('.resume-hero .el-loading-mask')")
  const originalEducation = await elementBox(client, `document.querySelector(${JSON.stringify(educationSelector)})?.value`)
  const userId = await elementBox(client, 'localStorage.getItem(\'userId\')')
  if (typeof originalEducation !== 'string' || !userId) {
    throw new Error('Unable to read the selected resume form before testing draft persistence')
  }

  const draftMarker = `E2E_DRAFT_${Date.now()}`
  const draftKey = `aicampus.draft.${encodeURIComponent(userId)}.resume.${encodeURIComponent(fixture.resumeId)}`
  await fillInput(client, educationSelector, draftMarker)
  await assertInputValue(client, educationSelector, draftMarker)
  await waitForExpression(client, "Boolean(document.querySelector('.form-dirty-note'))")

  await navigate(client, `${baseUrl}/student/jobs`)
  await waitForExpression(client, "location.pathname === '/student/jobs'")
  await navigate(client, originalUrl)
  await waitForExpression(client, "!document.querySelector('.resume-hero .el-loading-mask')")
  await assertInputValue(client, educationSelector, draftMarker)

  await navigate(client, originalUrl)
  await waitForExpression(client, "!document.querySelector('.resume-hero .el-loading-mask')")
  await assertInputValue(client, educationSelector, draftMarker)

  await fillInput(client, educationSelector, originalEducation)
  await client.send('Runtime.evaluate', {
    expression: `sessionStorage.removeItem(${JSON.stringify(draftKey)})`
  })
  await navigate(client, originalUrl)
  await waitForExpression(client, "!document.querySelector('.resume-hero .el-loading-mask')")
  await assertInputValue(client, educationSelector, originalEducation)
  await screenshot(client, '00g-resume-draft-restored.png')
}

async function verifyMatchHistoryRestoreAndContext(client, match, resume, job) {
  const analysisPath = `/student/jobs/${encodeURIComponent(job.jobId)}/match`
  const analysisUrl = `${baseUrl}${analysisPath}?matchId=${encodeURIComponent(match.matchId)}`
  await navigate(client, `${baseUrl}/student/jobs/history`)
  await waitForExpression(client, "Boolean(document.querySelector('button.match-record'))")
  await clickElementByData(client, '.match-records button.match-record', 'matchId', match.matchId)
  await waitForExpression(client, `location.pathname === ${JSON.stringify(analysisPath)} && new URLSearchParams(location.search).get('matchId') === ${JSON.stringify(match.matchId)}`)
  await assertMatchSelection(client, resume.fileName, job.title)
  const expectedScore = match.analysisSource === 'RULE_INSUFFICIENT_JOB_SKILLS' ? '—' : `${match.score}%`
  await waitForExpression(client, `document.querySelector('.jobs-coverage-summary strong')?.innerText === ${JSON.stringify(expectedScore)}`)
  const expandedEvidence = await elementBox(client, "Boolean(document.querySelector('[data-testid=\"jobs-analysis\"] details[open]'))")
  if (expandedEvidence) throw new Error('Matching evidence should be collapsed until requested')
  for (const testId of ['jobs-match-evidence', 'jobs-match-suggestions']) {
    const selector = `[data-testid="${testId}"]`
    if (await elementBox(client, `Boolean(document.querySelector(${JSON.stringify(selector)}))`)) {
      await setDetailsOpen(client, selector, true)
      await setDetailsOpen(client, selector, false)
    }
  }
  await screenshot(client, '00b-match-history-restored.png')

  await navigate(client, analysisUrl)
  await assertMatchSelection(client, resume.fileName, job.title)
  await clickSelector(client, '.match-next-actions button')
  await waitForExpression(client, "location.pathname === '/student/plan/create'")
  await waitForText(client, '已关联岗位匹配')
  await waitForText(client, `技能覆盖 ${match.score}%`)

  await navigate(client, analysisUrl)
  await assertMatchSelection(client, resume.fileName, job.title)
  await clickSelector(client, '.match-next-actions .el-button--primary')
  await waitForExpression(client, "location.pathname === '/student/interview'")
  await assertInputValue(client, '[aria-label="本次目标岗位"]', job.title)
  await screenshot(client, '00c-match-interview-context.png')
}

async function verifyJobsNavigation(client, fixture) {
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-list\"]'))")
  await waitForExpression(client, "!document.querySelector('[data-testid=\"jobs-list\"] .el-loading-mask')")
  await assertNoJobsPanelsExcept(client, 'jobs-list')
  const initial = await elementBox(client, `(() => ({
    cards: [...document.querySelectorAll('button.job-card[data-job-id]')].map((card) => ({
      id: card.dataset.jobId, title: card.querySelector('strong')?.innerText,
      compareLabel: card.querySelector('strong')?.innerText + ' · ' + card.querySelector('small')?.innerText.split(' · ')[0]
    })),
    redundantStats: Boolean(document.querySelector('.student-workspace .overview-grid')),
    comparisonControls: Boolean(document.querySelector('[data-testid="jobs-compare-selection"]')),
    matchResult: Boolean(document.querySelector('.match-result'))
  }))()`)
  if (initial.redundantStats || initial.comparisonControls || initial.matchResult || initial.cards.length > 12) {
    throw new Error(`Jobs list is not limited to one navigational level: ${JSON.stringify(initial)}`)
  }
  for (const name of ['list', 'compare', 'history']) {
    await waitForExpression(client, `Boolean(document.querySelector('[data-testid="jobs-nav-${name}"]'))`)
  }

  const firstJob = initial.cards[0]
  if (firstJob) {
    await fillInput(client, '[data-testid="jobs-search"]', firstJob.title)
    await waitForExpression(client, `Boolean(document.querySelector('button.job-card[data-job-id="${firstJob.id}"]'))`)
    await clickElementByData(client, 'button.job-card', 'jobId', firstJob.id)
    await waitForExpression(client, `location.pathname === ${JSON.stringify(`/student/jobs/${encodeURIComponent(firstJob.id)}`)}`)
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-detail\"]'))")
    await assertNoJobsPanelsExcept(client, 'jobs-detail')
    await clickSelector(client, '[data-testid="jobs-detail-match"]')
    await waitForExpression(client, `location.pathname === ${JSON.stringify(`/student/jobs/${encodeURIComponent(firstJob.id)}/match`)}`)
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-analysis\"]'))")
    await assertNoJobsPanelsExcept(client, 'jobs-analysis')
    const controls = await elementBox(client, `(() => ({
      resume: Boolean(document.querySelector('[data-testid="jobs-analysis"] [aria-label="匹配简历"]')),
      targetJob: Boolean(document.querySelector('[data-testid="jobs-analysis"] [aria-label="匹配岗位"]'))
    }))()`)
    if (!controls.resume || controls.targetJob) {
      throw new Error(`Analysis should use the selected job and one resume selector: ${JSON.stringify(controls)}`)
    }
    await browserBack(client)
    await waitForExpression(client, `location.pathname === ${JSON.stringify(`/student/jobs/${encodeURIComponent(firstJob.id)}`)}`)
    await clickSelector(client, '[data-testid="jobs-back"]')
    await waitForExpression(client, "location.pathname === '/student/jobs'")
    await assertInputValue(client, '[data-testid="jobs-search"]', firstJob.title)
    await client.send('Page.reload')
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-list\"]'))")
    await assertInputValue(client, '[data-testid="jobs-search"]', firstJob.title)
    await fillInput(client, '[data-testid="jobs-search"]', '')
  }

  const nextPageAvailable = await elementBox(client, "Boolean(document.querySelector('[data-testid=\"jobs-pagination\"] .btn-next:not(:disabled)'))")
  if (nextPageAvailable) {
    await clickSelector(client, '[data-testid="jobs-pagination"] .btn-next')
    const pageState = await elementBox(client, `(() => ({
      page: document.querySelector('[data-testid="jobs-pagination"] .number.is-active')?.innerText,
      firstJob: document.querySelector('button.job-card[data-job-id]')?.dataset.jobId,
      count: document.querySelectorAll('button.job-card[data-job-id]').length
    }))()`)
    if (pageState.page !== '2' || !pageState.firstJob || pageState.count > 12) {
      throw new Error(`Jobs page size or next-page navigation is incorrect: ${JSON.stringify(pageState)}`)
    }
    await clickSelector(client, 'button.job-card[data-job-id]')
    await clickSelector(client, '[data-testid="jobs-back"]')
    await waitForExpression(client, "location.pathname === '/student/jobs'")
    await waitForExpression(client, "document.querySelector('[data-testid=\"jobs-pagination\"] .number.is-active')?.innerText === '2'")
    await client.send('Page.reload')
    await waitForExpression(client, `document.querySelector('button.job-card[data-job-id]')?.dataset.jobId === ${JSON.stringify(pageState.firstJob)}`)
    await waitForExpression(client, "document.querySelector('[data-testid=\"jobs-pagination\"] .number.is-active')?.innerText === '2'")
    await clickSelector(client, '[data-testid="jobs-pagination"] .btn-prev')
    await waitForExpression(client, "document.querySelector('[data-testid=\"jobs-pagination\"] .number.is-active')?.innerText === '1'")
  }

  await clickSelector(client, '[data-testid="jobs-nav-compare"]')
  await waitForExpression(client, "location.pathname === '/student/jobs/compare'")
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-compare\"]'))")
  await assertNoJobsPanelsExcept(client, 'jobs-compare')
  await verifyCompareBoundaries(client, initial.cards)
  await clickSelector(client, '[data-testid="jobs-nav-history"]')
  await waitForExpression(client, "location.pathname === '/student/jobs/history'")
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-history\"]'))")
  await assertNoJobsPanelsExcept(client, 'jobs-history')
  if (fixture?.matchId) {
    await clickElementByData(client, 'button.match-record', 'matchId', fixture.matchId)
    await waitForExpression(client, `new URLSearchParams(location.search).get('matchId') === ${JSON.stringify(fixture.matchId)}`)
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-analysis\"] .match-result'))")
    await browserBack(client)
    await waitForExpression(client, "location.pathname === '/student/jobs/history'")
  }

  for (const suffix of ['', '/match']) {
    await navigate(client, `${baseUrl}/student/jobs/__e2e_invalid_job__${suffix}`)
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-unavailable\"]'))")
    const enabledMatching = await elementBox(client, "Boolean(document.querySelector('[data-testid=\"jobs-run-match\"]:not(:disabled)'))")
    if (enabledMatching) throw new Error('An unavailable job allows matching')
  }
  if (firstJob) {
    await navigate(client, `${baseUrl}/student/jobs/${encodeURIComponent(firstJob.id)}/match?matchId=__e2e_invalid_match__`)
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-unavailable\"]')) && !document.querySelector('.match-result')")
  }
  if (!demoMode) await verifyJobsFailureAndEmptyStates(client, firstJob)
  await navigate(client, `${baseUrl}/student/jobs`)
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-list\"]'))")
  await screenshot(client, '03a-jobs-list-navigation.png')
}

const modulePages = {
  resume: ['profile', 'templates', 'edit', 'diagnosis', 'versions', 'history', 'original', 'original-diagnosis'],
  plan: ['today', 'tasks', 'task', 'create', 'review', 'history'],
  interview: ['start', 'practice', 'report', 'history'],
  knowledge: ['search', 'answer', 'sources', 'history']
}

const resumePageSelectors = {
  preview: '.resume-preview-pane', profile: '[data-testid="resume-profile-form"]', templates: '[data-testid="resume-template-form"]',
  edit: '[data-testid="resume-page-edit"]', diagnosis: '[data-testid="resume-diagnosis-page"]',
  versions: '[data-testid="resume-versions-page"]', history: 'section[data-testid="resume-history"]',
  original: '[data-testid="resume-original"]', 'original-diagnosis': '[data-testid="resume-original-diagnosis"]'
}

async function assertOnlyModulePage(client, module, expected) {
  if (module === 'resume') {
    await waitForExpression(client, "Boolean(document.querySelector('.resume-builder, [data-testid=\"resume-history\"], [data-testid=\"resume-original\"], [data-testid=\"resume-original-diagnosis\"]')) && !document.querySelector('.resume-builder .el-loading-mask')")
    const visible = await elementBox(client, `(() => Object.entries(${JSON.stringify(resumePageSelectors)})
      .filter(([, selector]) => document.querySelector(selector)?.getClientRects().length).map(([page]) => page))()`)
    if (visible.some(page => page !== expected)) throw new Error(`Resume ${expected} contains unrelated pages: ${JSON.stringify(visible)}`)
    if (!visible.length && !await elementBox(client, "Boolean(document.querySelector('[data-testid=\"resume-result\"], [data-testid=\"resume-draft-empty\"]'))")) {
      throw new Error(`Resume ${expected} has neither content nor empty state`)
    }
    return
  }
  await waitForExpression(client, `Boolean(document.querySelector('[data-testid="${module}-${expected}"]')?.getClientRects().length)`)
  const visible = await elementBox(client, `(() => ${JSON.stringify(modulePages[module])}
    .filter(page => document.querySelector('[data-testid="${module}-' + page + '"]')?.getClientRects().length))()`)
  if (visible.length !== 1 || visible[0] !== expected) {
    throw new Error(`${module} should display only ${expected}: ${JSON.stringify(visible)}`)
  }
}

async function verifyModuleNavigation(client, fixture) {
  const sections = [
    ['resume', [['profile', '/profile'], ['templates', '/templates'], ['preview', ''], ['edit', '/edit'], ['diagnosis', '/diagnosis'], ['versions', '/versions'], ['history', '/history']]],
    ['plan', [['today', ''], ['tasks', '/tasks'], ['create', '/create'], ['review', '/review'], ['history', '/history']]],
    ['interview', [['start', ''], ['practice', '/practice'], ['report', '/report'], ['history', '/history']]],
    ['knowledge', [['search', ''], ['answer', '/answer'], ['sources', '/sources'], ['history', '/history']]]
  ]
  const calls = []
  const stopListening = client.on('Network.requestWillBeSent', ({ request }) => {
    if (request.method === 'POST' && (/\/api\/ai\//.test(request.url) || /\/api\/resumes\/drafts(?:\?|$)/.test(request.url))) calls.push(request.url)
  })
  try {
    for (const [module, pages] of sections) {
      const context = module === 'plan' && fixture ? `?planId=${encodeURIComponent(fixture.planId)}`
        : module === 'interview' && fixture ? `?sessionId=${encodeURIComponent(fixture.sessionId)}` : ''
      const root = `${baseUrl}/student/${module}`
      await navigate(client, root + context)
      let navigatedContext = context
      for (const [page, suffix] of pages) {
        const navKey = module === 'resume' && page === 'preview' ? 'editor' : page
        const navSelector = `[data-testid="${module}-nav-${navKey}"]`
        if (await elementBox(client, `Boolean(document.querySelector(${JSON.stringify(navSelector)}))`)) {
          await clickSelector(client, navSelector)
        } else {
          await navigate(client, root + suffix + navigatedContext)
        }
        await waitForExpression(client, `location.pathname === ${JSON.stringify(`/student/${module}${suffix}`)}`)
        await assertOnlyModulePage(client, module, page)
        await assertNoHorizontalOverflow(client)
        const beforeRefresh = await elementBox(client, 'location.pathname + location.search')
        navigatedContext = await elementBox(client, 'location.search')
        await navigate(client, baseUrl + beforeRefresh)
        await assertOnlyModulePage(client, module, page)
        if (module === 'plan' && fixture && page !== 'create') {
          await waitForExpression(client, `new URLSearchParams(location.search).get('planId') === ${JSON.stringify(fixture.planId)}`)
        }
        if (module === 'interview' && fixture && page !== 'start') {
          await waitForExpression(client, `new URLSearchParams(location.search).get('sessionId') === ${JSON.stringify(fixture.sessionId)}`)
        }
        await screenshot(client, `navigation-${module}-${page}.png`)
      }
    }
    if (calls.length) throw new Error(`Navigation unexpectedly generated AI or saved drafts: ${JSON.stringify(calls)}`)
  } finally { stopListening() }
  await navigate(client, `${baseUrl}/student/resume`)
}

async function verifyModuleNavigationBoundaries(client, fixture) {
  const invalid = `e2e-unavailable-${Date.now()}`
  await navigate(client, `${baseUrl}/student/resume/edit?draftId=${invalid}`)
  await waitForText(client, '这份简历不存在或已不可访问。')
  if (await elementBox(client, "Boolean(document.querySelector('[data-testid=\"resume-save-draft\"]:not(:disabled), [data-testid=\"resume-entry-title-0-0\"]'))")) {
    throw new Error('Invalid resume draft exposes a different saved draft for editing')
  }
  await navigate(client, `${baseUrl}/student/resume/original/${invalid}`)
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"resume-unavailable\"]'))")
  await navigate(client, `${baseUrl}/student/plan/tasks?planId=${invalid}`)
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"plan-unavailable\"]'))")
  if (await elementBox(client, "Boolean(document.querySelector('.task-row'))")) throw new Error('Invalid plan shows tasks from another plan')
  if (fixture) {
    await navigate(client, planTaskUrl(fixture.planId, invalid))
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"plan-unavailable\"]'))")
    if (await elementBox(client, "Boolean(document.querySelector('[data-testid=\"plan-task\"] input[placeholder=\"复盘备注\"]'))")) {
      throw new Error('Invalid task permits editing another task')
    }
  }
  await navigate(client, `${baseUrl}/student/interview/practice?sessionId=${invalid}`)
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"interview-unavailable\"]'))")
  if (await elementBox(client, "Boolean(document.querySelector('#interview-answer'))")) throw new Error('Invalid interview exposes another session answer')
  await withApiOverrides(client, [{ path: '/api/ai/learning/plans', data: [] }], async () => {
    await navigate(client, `${baseUrl}/student/plan`)
    await assertOnlyModulePage(client, 'plan', 'today')
    await waitForText(client, '还没有学习计划。')
    if (await elementBox(client, "Boolean(document.querySelector('.task-row'))")) throw new Error('Empty learning plans show cached tasks')
  })
  await withApiOverrides(client, [{ path: '/api/ai/interview/sessions', data: [] }], async () => {
    await navigate(client, `${baseUrl}/student/interview/practice`)
    await assertOnlyModulePage(client, 'interview', 'practice')
    if (await elementBox(client, "Boolean(document.querySelector('#interview-answer'))")) throw new Error('Empty interview list shows cached answers')
  })
  await navigate(client, `${baseUrl}/student/resume`)
}

async function assertNoJobsPanelsExcept(client, expected) {
  const panels = await elementBox(client, `(() => ['jobs-list', 'jobs-detail', 'jobs-analysis', 'jobs-compare', 'jobs-history']
    .filter((name) => document.querySelector('[data-testid="' + name + '"]')?.getClientRects().length))()`)
  if (panels.length !== 1 || panels[0] !== expected) {
    throw new Error(`Jobs navigation rendered multiple levels; expected ${expected}: ${JSON.stringify(panels)}`)
  }
}

async function verifyCompareBoundaries(client, jobs) {
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"jobs-compare-selection\"]'))")
  await waitForExpression(client, "!document.querySelector('[data-testid=\"jobs-compare\"] .el-loading-mask')")
  const runButton = '[data-testid="jobs-run-compare"]'
  const zeroDisabled = await elementBox(client, `Boolean(document.querySelector(${JSON.stringify(runButton)})?.disabled)`)
  if (!zeroDisabled) throw new Error('Comparison must be disabled with zero jobs')
  if (jobs.length < 2) return

  for (let index = 0; index < Math.min(4, jobs.length); index += 1) {
    await clickSelector(client, '[data-testid="jobs-compare-selection"]')
    const option = await elementBox(client, `(() => {
      const item = [...document.querySelectorAll('.el-select-dropdown__item')]
        .find((element) => element.getClientRects().length && !element.classList.contains('is-selected')
          && element.innerText.trim() === ${JSON.stringify(jobs[index].compareLabel)});
      return item ? { disabled: item.classList.contains('is-disabled'), selected: item.classList.contains('is-selected') } : null;
    })()`)
    if (!option) throw new Error(`Comparison option not available: ${jobs[index].title}`)
    if (index === 3 && !option.disabled) throw new Error('The fourth comparison option must be disabled at the three-job limit')
    if (!option.disabled) {
      await clickElementContaining(client, '.el-select-dropdown__item:not(.is-selected)', jobs[index].compareLabel)
    }
    await client.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
    await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
    const state = await elementBox(client, `(() => ({
      disabled: Boolean(document.querySelector(${JSON.stringify(runButton)})?.disabled),
      count: document.querySelector('[data-testid="jobs-compare-selection"]')?.querySelectorAll('.el-tag').length || 0
    }))()`)
    const attempted = index + 1
    if (state.count !== Math.min(attempted, 3)) {
      throw new Error(`Comparison did not preserve the expected job count after ${attempted} choices: ${JSON.stringify(state)}`)
    }
    if (attempted <= 3 && state.disabled !== (attempted === 1)) {
      throw new Error(`Comparison selection boundary failed for ${attempted} jobs: ${JSON.stringify(state)}`)
    }
  }
}

async function browserBack(client) {
  const navigation = await client.send('Page.getNavigationHistory')
  const previous = navigation.entries[navigation.currentIndex - 1]
  if (!previous) throw new Error('No previous browser history entry exists')
  await client.send('Page.navigateToHistoryEntry', { entryId: previous.id })
  await sleep(600)
}

async function verifyJobsFailureAndEmptyStates(client, firstJob) {
  await withApiOverrides(client, [{ path: '/api/jobs', data: [] }], async () => {
    await navigate(client, `${baseUrl}/student/jobs`)
    await assertText(client, ['暂无开放岗位'])
    await waitForExpression(client, "document.querySelectorAll('button.job-card').length === 0")
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '03b-jobs-empty.png')
  })
  await withApiOverrides(client, [{ path: '/api/jobs', code: 503, message: 'E2E jobs temporarily unavailable', times: 1 }], async () => {
    await navigate(client, `${baseUrl}/student/jobs`)
    await waitForExpression(client, "Boolean(document.querySelector('.jobs-load-error'))")
    await clickSelector(client, '[data-testid="jobs-load-retry"]')
    await waitForExpression(client, "!document.querySelector('.jobs-load-error') && !document.querySelector('[data-testid=\"jobs-list\"] .el-loading-mask')")
    if (firstJob) await waitForExpression(client, "Boolean(document.querySelector('button.job-card'))")
    await screenshot(client, '03c-jobs-load-recovered.png')
  })
  if (firstJob) {
    await withApiOverrides(client, [{ path: '/api/resumes', data: [] }], async () => {
      await navigate(client, `${baseUrl}/student/jobs/${encodeURIComponent(firstJob.id)}`)
      await assertText(client, ['暂无可匹配的简历'])
      await waitForExpression(client, "document.querySelector('[data-testid=\"jobs-detail-match\"]')?.disabled === true")
      await clickSelector(client, '[data-testid="jobs-nav-compare"]')
      await assertText(client, ['暂无可比较的简历'])
      await waitForExpression(client, "document.querySelector('[data-testid=\"jobs-run-compare\"]')?.disabled === true")
      await screenshot(client, '03d-jobs-no-resume.png')
    })
  }
}

async function withApiOverrides(client, overrides, action) {
  const intercepted = new Map()
  let interceptionError
  const stopListening = client.on('Fetch.requestPaused', (params) => {
    void (async () => {
      try {
        const apiPath = new URL(params.request.url).pathname
        const rule = overrides.find((item) => item.path === apiPath
          && (intercepted.get(item.path) || 0) < (item.times ?? Infinity))
        if (!rule) {
          await client.send('Fetch.continueRequest', { requestId: params.requestId })
          return
        }
        intercepted.set(rule.path, (intercepted.get(rule.path) || 0) + 1)
        await client.send('Fetch.fulfillRequest', {
          requestId: params.requestId,
          responseCode: rule.code || 200,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify({ code: rule.code || 0, message: rule.message || 'success', data: rule.data ?? null })).toString('base64')
        })
      } catch (error) {
        interceptionError = error
      }
    })()
  })
  try {
    await client.send('Fetch.enable', { patterns: overrides.map((rule) => ({ urlPattern: `*${rule.path}`, requestStage: 'Request' })) })
    await action()
    if (interceptionError) throw interceptionError
    for (const rule of overrides) {
      if (!intercepted.has(rule.path)) throw new Error(`Boundary response was not intercepted: ${rule.path}`)
    }
  } finally {
    await client.send('Fetch.disable')
    stopListening()
  }
}

async function verifyTaskSaveFailureRetention(client, plan) {
  const task = plan.tasks?.find((item) => item.status !== 'COMPLETED')
  if (!task) {
    throw new Error(`Fixture plan has no unfinished task for save failure coverage: ${plan.planId}`)
  }
  await navigate(client, planTaskUrl(plan.planId, task.taskId))
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"plan-task\"] .task-row'))")
  const taskPath = `/api/ai/learning/plans/${encodeURIComponent(plan.planId)}/tasks/${encodeURIComponent(task.taskId)}`
  const feedback = `E2E save failure ${Date.now()}`
  let intercepted = false
  let interceptionError
  const stopListening = client.on('Fetch.requestPaused', (params) => {
    void (async () => {
      if (params.request.method !== 'PUT' || !params.request.url.includes(taskPath)) {
        await client.send('Fetch.continueRequest', { requestId: params.requestId })
        return
      }
      try {
        intercepted = true
        await client.send('Fetch.fulfillRequest', {
          requestId: params.requestId,
          responseCode: 503,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify({ code: 503, message: 'E2E simulated task save failure', data: null })).toString('base64')
        })
      } catch (error) {
        interceptionError = error
      }
    })()
  })

  try {
    await client.send('Fetch.enable', { patterns: [{ urlPattern: `*${taskPath}`, requestStage: 'Request' }] })
    const taskIndex = await elementBox(client, `(() => [...document.querySelectorAll('.task-row')]
      .findIndex((row) => row.querySelector('h2, strong')?.innerText.includes(${JSON.stringify(task.title)})))()`)
    if (!Number.isInteger(taskIndex) || taskIndex < 0) {
      throw new Error(`Unable to locate unfinished task in the plan UI: ${task.taskId}`)
    }
    const feedbackInput = '[data-testid="plan-task"] input[placeholder="复盘备注"]'
    await fillInput(client, feedbackInput, feedback)
    for (let index = 0; index < 30 && !intercepted && !interceptionError; index += 1) {
      await sleep(250)
    }
    if (interceptionError) {
      throw interceptionError
    }
    if (!intercepted) {
      throw new Error(`Task update request was not intercepted: ${taskPath}`)
    }
    await waitForExpression(client, `(() => {
      const rows = [...document.querySelectorAll('.task-row')];
      const row = rows[${taskIndex}];
      const input = row?.querySelector('input[placeholder="复盘备注"]');
      return input?.value === ${JSON.stringify(feedback)} && !input.disabled && row.innerText.includes('重试');
    })()`)
    await screenshot(client, '00h-task-save-failure-retained.png')
  } finally {
    stopListening()
    await client.send('Fetch.disable')
  }

  await navigate(client, `${baseUrl}/student/history`)
  await waitForExpression(client, "location.pathname === '/student/interview/history'")
}

async function verifyTaskEvidenceEntry(client, plan, task) {
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"plan-task\"] .learning-evidence'))")
  await setDetailsOpen(client, '.learning-evidence', true)
  await waitForExpression(client, "Boolean(document.querySelector('.evidence-input textarea'))")
  await clickElementContaining(client, '.evidence-input button', '提交成果并评价')
  await waitForText(client, '请描述练习过程、实际结果与验收情况。')
  const path = `/api/ai/learning/plans/${encodeURIComponent(plan.planId)}/tasks/${encodeURIComponent(task.taskId)}/evidence`
  const text = `E2E retained evidence ${Date.now()}`
  await withApiOverrides(client, [{ path, code: 503, message: 'E2E evidence request failure' }], async () => {
    await fillInput(client, '.evidence-input textarea', text)
    await clickElementContaining(client, '.evidence-input button', '提交成果并评价')
    await waitForText(client, 'E2E evidence request failure')
    await assertInputValue(client, '.evidence-input textarea', text)
    await screenshot(client, '00j-evidence-failure-input-retained.png')
  })
}

function planTaskUrl(planId, taskId) {
  return `${baseUrl}/student/plan/tasks/${encodeURIComponent(taskId)}?planId=${encodeURIComponent(planId)}`
}

async function verifyPlanHistoryReadOnly(client, plan) {
  const versions = await fetchFixtureData(client, `/api/ai/learning/plans/${encodeURIComponent(plan.planId)}/versions`)
  const historical = versions.find((version) => version.status !== 'ACTIVE'
    && version.tasks?.some((task) => task.feedback === persistedTaskFeedback))
  if (!historical) {
    throw new Error(`Fixture plan has no historical version with persisted task feedback: ${plan.planId}`)
  }

  await navigate(client, `${baseUrl}/student/plan/history?planId=${encodeURIComponent(plan.planId)}`)
  await waitForExpression(client, "Boolean(document.querySelector('.version-actions button'))")
  await clickElementByData(client, '.version-actions button', 'planId', historical.planId)
  await waitForExpression(client, `location.pathname === '/student/plan/tasks' && new URLSearchParams(location.search).get('planId') === ${JSON.stringify(historical.planId)}`)
  const task = historical.tasks.find(item => item.feedback === persistedTaskFeedback)
  await navigate(client, planTaskUrl(historical.planId, task.taskId))
  await waitForText(client, '当前为历史版本')
  await assertReadOnlyPersistedTask(client, persistedTaskFeedback)
  await screenshot(client, '00d-plan-history-readonly.png')
}

async function verifyCompletedInterviewReadOnly(client, fixture, session) {
  const feedbackByQuestion = new Map((session.report.questionFeedback || []).map((feedback) => [feedback.questionId, feedback]))
  const activeQuestion = session.questions[session.questions.length - 1]
  const activeFeedback = feedbackByQuestion.get(activeQuestion?.questionId) || session.report.questionFeedback[0]
  const feedbackText = activeFeedback?.summary || activeFeedback?.suggestions?.[0]
  if (!feedbackText) {
    throw new Error(`Fixture interview report has no visible question feedback: ${fixture.sessionId}`)
  }

  await navigate(client, `${baseUrl}/student/interview?tab=history`)
  await waitForExpression(client, "location.pathname === '/student/interview/history'")
  await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"interview-history\"] [data-session-id]'))")
  await clickSessionCard(client, fixture.sessionId)
  await waitForExpression(client, `location.pathname === '/student/interview/report' && new URLSearchParams(location.search).get('sessionId') === ${JSON.stringify(fixture.sessionId)}`)
  await assertNoText(client, ['本次目标岗位', '题目数量'])
  await clickSelector(client, '[data-testid="interview-view-answers"]')
  await waitForExpression(client, `location.pathname === '/student/interview/practice' && new URLSearchParams(location.search).get('sessionId') === ${JSON.stringify(fixture.sessionId)}`)
  await waitForExpression(client, "Boolean(document.querySelector('#interview-answer')?.readOnly)")
  await setDetailsOpen(client, '.question-feedback', true)
  await waitForText(client, '本题反馈')
  await waitForText(client, feedbackText)
  await screenshot(client, '00e-completed-interview-readonly.png')
}

async function verifyInterviewAnswerDraft(client, completedSession) {
  const session = { ...completedSession, sessionId: `e2e-answer-draft-${Date.now()}`, status: 'IN_PROGRESS',
    questions: [completedSession.questions[0]], answers: [], attempts: [], report: undefined, partialReport: undefined,
    completedAt: undefined, mode: 'COACHING', timer: undefined, pausedAt: undefined }
  const text = `E2E unsaved answer ${Date.now()}`
  const path = `/student/interview/practice?sessionId=${encodeURIComponent(session.sessionId)}`
  await withApiOverrides(client, [{ path: '/api/ai/interview/sessions', data: [session] },
    { path: `/api/ai/interview/sessions/${session.sessionId}`, data: session }], async () => {
    await navigate(client, baseUrl + path)
    await waitForExpression(client, "Boolean(document.querySelector('#interview-answer')) && !document.querySelector('#interview-answer').readOnly")
    await fillInput(client, '#interview-answer', text)
    await assertInputValue(client, '#interview-answer', text)
    await clickSelector(client, '[data-testid="interview-nav-start"]')
    await waitForExpression(client, "location.pathname === '/student/interview'")
    await clickSelector(client, '[data-testid="interview-nav-practice"]')
    await waitForExpression(client, "location.pathname === '/student/interview/practice'")
    await assertInputValue(client, '#interview-answer', text)
    await navigate(client, baseUrl + path)
    await assertInputValue(client, '#interview-answer', text)
    await screenshot(client, '00i-interview-draft-restored.png')
    const userId = await elementBox(client, "localStorage.getItem('userId')")
    await client.send('Runtime.evaluate', { expression: `localStorage.removeItem(${JSON.stringify(`aicampus.draft.${encodeURIComponent(userId)}.interview-practice.${encodeURIComponent(session.sessionId)}`)})` })
  })
}

async function verifyRetrievalOnlyKnowledge(client) {
  const query = 'Java Redis'
  await navigate(client, `${baseUrl}/student/knowledge`)
  await waitForExpression(client, "Boolean(document.querySelector('.knowledge-mode .el-switch'))")
  const retrievalOnly = await elementBox(client, `(() => {
    const control = document.querySelector('.knowledge-mode .el-switch');
    const input = control?.querySelector('input');
    return control?.getAttribute('aria-checked') === 'false' || Boolean(input && !input.checked);
  })()`)
  if (!retrievalOnly) {
    throw new Error('Knowledge search defaults to AI generation instead of retrieval-only mode')
  }
  await fillInput(client, '.knowledge-search input', query)
  await clickSelector(client, '.knowledge-search button')
  await waitForExpression(client, "Boolean(document.querySelector('.knowledge-retrieval .retrieval-result'))")
  await assertInputValue(client, '.knowledge-search input', query)
  if (await elementBox(client, "Boolean(document.querySelector('.rag-answer, .citation-row'))")) {
    throw new Error('Knowledge query page still contains full answers or citations')
  }
  await clickSelector(client, '[data-testid="knowledge-nav-sources"]')
  await waitForExpression(client, "location.pathname === '/student/knowledge/sources' && Boolean(document.querySelector('.citation-row'))")
  await waitForExpression(client, `new URLSearchParams(location.search).get('q') === ${JSON.stringify(query)} && new URLSearchParams(location.search).get('ai') === '0'`)
  await setDetailsOpen(client, '.citation-row', true)
  if (!await elementBox(client, "Boolean(document.querySelector('.citation-row div')?.textContent.trim())")) throw new Error('Source page does not expose citation text')
  const citationsBeforeRefresh = await elementBox(client, "[...document.querySelectorAll('.citation-row summary')].map(item => item.innerText)")
  const sourceUrl = await elementBox(client, 'location.href')
  const sourceCalls = []
  const stopSourceCalls = client.on('Network.requestWillBeSent', ({ request }) => {
    if (request.method === 'POST' && /\/api\/ai\//.test(request.url)) sourceCalls.push(request.url)
  })
  try {
    await navigate(client, sourceUrl)
    await waitForExpression(client, "Boolean(document.querySelector('.citation-row'))")
    const restoredCitations = await elementBox(client, "[...document.querySelectorAll('.citation-row summary')].map(item => item.innerText)")
    if (JSON.stringify(restoredCitations) !== JSON.stringify(citationsBeforeRefresh)) throw new Error('Citation source context changed after refresh')
    await browserBack(client)
    await waitForExpression(client, "location.pathname === '/student/knowledge'")
    await assertInputValue(client, '.knowledge-search input', query)
    if (sourceCalls.length) throw new Error(`Knowledge source navigation repeats AI request: ${JSON.stringify(sourceCalls)}`)
  } finally { stopSourceCalls() }
  await navigate(client, `${baseUrl}/student/knowledge`)
  await fillInput(client, '.knowledge-search input', '')
  await clickSelector(client, '.knowledge-search button')
  await waitForText(client, '请输入检索关键词')
  await navigate(client, `${baseUrl}/student/knowledge/history`)
  await waitForText(client, '最近查询')
  await waitForText(client, query)
  await screenshot(client, '00f-retrieval-only-rag.png')
  await verifyKnowledgeLateResponse(client, query, 'history')
  await verifyKnowledgeLateResponse(client, query, 'edit')
  await verifyKnowledgePermissionRevision(client, query)
}

async function knowledgeSnapshot(client, required = true) {
  const snapshot = await elementBox(client, `(() => {
    const key = 'aicampus.knowledge-result.' + localStorage.getItem('userId');
    try { return JSON.parse(sessionStorage.getItem(key) || 'null') } catch { return null }
  })()`)
  if (required && (!snapshot?.answer?.citations?.length || !snapshot.answer.permissionVersion)) {
    throw new Error('Knowledge navigation boundary tests require a current quoted answer snapshot')
  }
  return snapshot
}

async function verifyKnowledgeLateResponse(client, stableQuery, mode) {
  const snapshot = await knowledgeSnapshot(client)
  const pendingQuery = `E2E pending ${mode} ${Date.now()}`
  const marker = `E2E_LATE_ANSWER_${Date.now()}`
  let paused
  let interceptionError
  let released = false
  const stopListening = client.on('Fetch.requestPaused', params => {
    void (async () => {
      try {
        const body = JSON.parse(params.request.postData || '{}')
        if (params.request.method === 'POST' && body.query === pendingQuery) paused = params
        else await client.send('Fetch.continueRequest', { requestId: params.requestId })
      } catch (error) { interceptionError = error }
    })()
  })
  try {
    await client.send('Fetch.enable', { patterns: [{ urlPattern: '*/api/ai/knowledge/answer', requestStage: 'Request' }] })
    await navigate(client, `${baseUrl}/student/knowledge?q=${encodeURIComponent(stableQuery)}&ai=0`)
    await waitForExpression(client, "Boolean(document.querySelector('.knowledge-mode .el-switch'))")
    await clickSelector(client, '.knowledge-mode .el-switch')
    await waitForExpression(client, "document.querySelector('.knowledge-mode .el-switch')?.getAttribute('aria-checked') === 'true' || Boolean(document.querySelector('.knowledge-mode .el-switch input')?.checked)")
    await fillInput(client, '.knowledge-search input', pendingQuery)
    await clickSelector(client, '.knowledge-search button')
    for (let index = 0; index < 40 && !paused && !interceptionError; index++) await sleep(100)
    if (interceptionError) throw interceptionError
    if (!paused) throw new Error(`Knowledge ${mode} delayed query was not intercepted`)
    if (mode === 'history') {
      await clickSelector(client, '[data-testid="knowledge-nav-history"]')
      await waitForExpression(client, "location.pathname === '/student/knowledge/history'")
      await clickElementContaining(client, '.knowledge-history button.module-record', stableQuery)
      await waitForExpression(client, `location.pathname === '/student/knowledge' && new URLSearchParams(location.search).get('q') === ${JSON.stringify(stableQuery)}`)
      await waitForExpression(client, "Boolean(document.querySelector('.knowledge-retrieval .retrieval-result')) && !document.querySelector('.knowledge-search button.is-loading')")
    } else {
      await fillInput(client, '.knowledge-search input', stableQuery)
    }
    const staleAnswer = { ...snapshot.answer, query: pendingQuery, answer: marker, generationMode: 'AI', provider: 'e2e' }
    await client.send('Fetch.fulfillRequest', { requestId: paused.requestId, responseCode: 200,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
      body: Buffer.from(JSON.stringify({ code: 0, message: 'success', data: staleAnswer })).toString('base64') })
    released = true
    await sleep(800)
    await waitForExpression(client, "location.pathname === '/student/knowledge'")
    await assertInputValue(client, '.knowledge-search input', stableQuery)
    if ((await bodyText(client)).includes(marker)) throw new Error(`Late ${mode} answer overwrote current knowledge context`)
    const saved = await knowledgeSnapshot(client, false)
    if (saved?.query === pendingQuery || saved?.answer?.answer.includes(marker)) throw new Error(`Late ${mode} answer replaced the stored knowledge result`)
    if (mode === 'history') {
      await waitForExpression(client, `new URLSearchParams(location.search).get('q') === ${JSON.stringify(stableQuery)}`)
    }
    await screenshot(client, `knowledge-late-${mode}-ignored.png`)
  } finally {
    if (paused && !released) {
      try { await client.send('Fetch.failRequest', { requestId: paused.requestId, errorReason: 'Aborted' }) } catch {}
    }
    await client.send('Fetch.disable')
    stopListening()
  }
  await navigate(client, `${baseUrl}/student/knowledge`)
  await fillInput(client, '.knowledge-search input', stableQuery)
  await clickSelector(client, '.knowledge-search button')
  await waitForExpression(client, "Boolean(document.querySelector('.knowledge-retrieval .retrieval-result'))")
}

async function verifyKnowledgePermissionRevision(client, query) {
  const snapshot = await knowledgeSnapshot(client)
  const changedRevision = `e2e-changed-${snapshot.answer.permissionVersion}-${Date.now()}`
  const answerCalls = []
  const stopCalls = client.on('Network.requestWillBeSent', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/ai/knowledge/answer') answerCalls.push(request.url)
  })
  try {
    await withApiOverrides(client, [{ path: '/api/ai/knowledge/revision', data: changedRevision }], async () => {
      await navigate(client, `${baseUrl}/student/knowledge/sources?q=${encodeURIComponent(query)}&ai=0`)
      await assertOnlyModulePage(client, 'knowledge', 'sources')
      await waitForExpression(client, "Boolean(document.querySelector('.knowledge-error'))")
      if (await elementBox(client, "Boolean(document.querySelector('.citation-row'))")) throw new Error('Changed knowledge revision restored stale citations')
      await waitForText(client, '重新检索')
      await screenshot(client, 'knowledge-revision-invalidated-sources.png')
      if (answerCalls.length) throw new Error(`Knowledge revision validation repeated answer generation: ${JSON.stringify(answerCalls)}`)
    })
  } finally { stopCalls() }
  await navigate(client, `${baseUrl}/student/knowledge`)
  await fillInput(client, '.knowledge-search input', query)
  await clickSelector(client, '.knowledge-search button')
  await waitForExpression(client, "Boolean(document.querySelector('.knowledge-retrieval .retrieval-result'))")
  await clickSelector(client, '[data-testid="knowledge-nav-sources"]')
  await waitForExpression(client, "Boolean(document.querySelector('.citation-row')) && !document.querySelector('.knowledge-error')")
  await screenshot(client, 'knowledge-revision-fresh-search-restored.png')
}

async function fetchFixtureData(client, route) {
  const response = await client.send('Runtime.evaluate', {
    expression: `(async () => {
      const token = localStorage.getItem('token');
      const response = await fetch(${JSON.stringify(route)}, {
        headers: token ? { Authorization: token.toLowerCase().startsWith('bearer ') ? token : 'Bearer ' + token } : {}
      });
      const body = await response.json();
      return { ok: response.ok, body };
    })()`,
    awaitPromise: true,
    returnByValue: true
  })
  const value = response.result.value
  if (!value?.ok || value.body?.code !== 0 || !value.body?.data) {
    throw new Error(`Unable to load fixture data from ${route}`)
  }
  return value.body.data
}

async function ensureFrontend() {
  if (await isReachable(baseUrl)) {
    return
  }
  if (explicitBaseUrl) {
    throw new Error(`E2E_BASE_URL is not reachable: ${baseUrl}`)
  }
  const url = new URL(baseUrl)
  const host = url.hostname || '127.0.0.1'
  const port = url.port || localPort
  const command = process.platform === 'win32' ? 'cmd.exe' : 'npm'
  const args = process.platform === 'win32'
    ? ['/d', '/s', '/c', `npm run dev -- --host ${host} --port ${port}`]
    : ['run', 'dev', '--', '--host', host, '--port', port]
  devServer = spawn(command, args, {
    cwd: rootDir,
    env: {
      ...process.env,
      VITE_DEMO_MODE: demoMode ? 'true' : 'false',
      VITE_API_PROXY_TARGET: demoMode ? '' : apiProxyTarget,
      ...(demoMode ? { VITE_API_BASE_URL: '', VITE_AI_PROXY_TARGET: '' } : {})
    },
    stdio: ['ignore', 'pipe', 'pipe']
  })
  devServer.stdout.on('data', (chunk) => process.stdout.write(chunk))
  devServer.stderr.on('data', (chunk) => process.stderr.write(chunk))
  for (let index = 0; index < 60; index += 1) {
    if (await isReachable(baseUrl)) {
      return
    }
    await sleep(1000)
  }
  throw new Error(`Frontend dev server did not become reachable at ${baseUrl}`)
}

async function isReachable(url) {
  try {
    const response = await fetch(url)
    return response.ok
  } catch {
    return false
  }
}

async function startBrowser() {
  const browserPath = process.env.E2E_BROWSER || findBrowser()
  if (!browserPath) {
    throw new Error('No Edge/Chrome executable found. Set E2E_BROWSER to a Chromium-based browser path.')
  }
  const port = Number(process.env.E2E_CDP_PORT || 9300 + Math.floor(Math.random() * 600))
  const userDataDir = path.join(process.env.TEMP || artifactsDir, `aicampus-e2e-${Date.now()}`)
  const proc = spawn(browserPath, [
    ...(process.env.E2E_HEADED === '1' ? [] : ['--headless=new']),
    '--disable-gpu',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDataDir}`,
    'about:blank'
  ], { stdio: 'ignore' })
  for (let index = 0; index < 30; index += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })
      if (response.ok) {
        const tab = await response.json()
        return { process: proc, webSocketDebuggerUrl: tab.webSocketDebuggerUrl }
      }
    } catch {
      // Retry until DevTools is ready.
    }
    await sleep(500)
  }
  await stopBrowser(proc)
  throw new Error('Browser DevTools endpoint did not become ready')
}

function findBrowser() {
  const candidates = [
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser'
  ]
  return candidates.find((candidate) => fs.existsSync(candidate))
}

async function connect(wsUrl) {
  const ws = new WebSocket(wsUrl)
  const pending = new Map()
  const listeners = new Map()
  let sequence = 0
  ws.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (!message.id) {
      for (const listener of listeners.get(message.method) || []) {
        Promise.resolve(listener(message.params || {})).catch(() => {})
      }
      return
    }
    if (!pending.has(message.id)) return
    const request = pending.get(message.id)
    pending.delete(message.id)
    if (message.error) {
      request.reject(new Error(JSON.stringify(message.error)))
    } else {
      request.resolve(message.result || {})
    }
  })
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', reject, { once: true })
  })
  return {
    send(method, params = {}) {
      const id = ++sequence
      ws.send(JSON.stringify({ id, method, params }))
      return new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
    },
    on(method, listener) {
      const handlers = listeners.get(method) || new Set()
      handlers.add(listener)
      listeners.set(method, handlers)
      return () => handlers.delete(listener)
    },
    close() {
      ws.close()
    }
  }
}

async function enablePage(client, width, height) {
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Network.enable')
  await client.send('Input.setIgnoreInputEvents', { ignore: false })
  await client.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: width < 700
  })
}

async function setViewport(client, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: width < 700
  })
  await sleep(500)
}

async function loginAs(client, username, role, expectedPath, password = '123456') {
  await navigate(client, `${baseUrl}/login`)
  await waitForExpression(client, "location.pathname === '/login' && Boolean(document.querySelector('input[autocomplete=\"username\"]'))")
  await fillInput(client, 'input[autocomplete="username"]', username)
  await fillInput(client, 'input[type="password"]', password)
  await clickButton(client, '登录')
  await waitForExpression(client, `localStorage.getItem('role') === ${JSON.stringify(role)} && location.pathname === ${JSON.stringify(expectedPath)}`)
}

async function fillInput(client, selector, value) {
  const success = await elementBox(client, `(() => {
    const root = document.querySelector(${JSON.stringify(selector)});
    const input = root && /^(INPUT|TEXTAREA)$/.test(root.tagName) ? root : root?.querySelector('input, textarea');
    if (!input) return false;
    const setter = Object.getOwnPropertyDescriptor(input.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value')?.set;
    setter?.call(input, ${JSON.stringify(value)});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  })()`)
  if (!success) {
    throw new Error(`Input not available: ${selector}`)
  }
  await sleep(200)
}

async function navigate(client, url) {
  await client.send('Page.navigate', { url })
  await sleep(1200)
}

async function clickButton(client, label) {
  const box = await waitForButton(client, label)
  if (!box || box.disabled) {
    throw new Error(`Button not available: ${label}`)
  }
  await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y })
  await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
  await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
  await sleep(1000)
}

async function clickSelector(client, selector) {
  for (let index = 0; index < 24; index += 1) {
    const box = await elementBox(client, `(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      if (!element) return null;
      element.scrollIntoView({ block: 'center', inline: 'nearest' });
      const rect = element.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, disabled: element.disabled };
    })()`)
    if (box && !box.disabled) {
      await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y })
      await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await sleep(500)
      return
    }
    await sleep(250)
  }
  throw new Error(`Element not available: ${selector}`)
}

async function clickElementContaining(client, selector, expectedText) {
  for (let index = 0; index < 30; index += 1) {
    const box = await elementBox(client, `(() => {
      const element = [...document.querySelectorAll(${JSON.stringify(selector)})]
        .find((item) => item.getClientRects().length && item.innerText.includes(${JSON.stringify(expectedText)}));
      if (!element) return null;
      element.scrollIntoView({ block: 'center', inline: 'nearest' });
      const rect = element.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, disabled: element.disabled };
    })()`)
    if (box && !box.disabled) {
      await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y })
      await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await sleep(500)
      return
    }
    await sleep(250)
  }
  throw new Error(`Element containing "${expectedText}" not available: ${selector}`)
}

async function clickElementByData(client, selector, dataName, expectedValue) {
  for (let index = 0; index < 30; index += 1) {
    const box = await elementBox(client, `(() => {
      const element = [...document.querySelectorAll(${JSON.stringify(selector)})]
        .find((item) => item.getClientRects().length && item.dataset[${JSON.stringify(dataName)}] === ${JSON.stringify(expectedValue)});
      if (!element) return null;
      element.scrollIntoView({ block: 'center', inline: 'nearest' });
      const rect = element.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, disabled: element.disabled };
    })()`)
    if (box && !box.disabled) {
      await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y })
      await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await sleep(500)
      return
    }
    await sleep(250)
  }
  throw new Error(`Element with ${dataName}=${expectedValue} not available: ${selector}`)
}

async function clickSessionCard(client, sessionId) {
  for (let index = 0; index < 30; index += 1) {
    const box = await elementBox(client, `(() => {
      const element = [...document.querySelectorAll('[data-testid="interview-history"] [data-session-id]')].find((item) => item.getClientRects().length
        && item.dataset.sessionId === ${JSON.stringify(sessionId)});
      if (!element) return null;
      element.scrollIntoView({ block: 'center', inline: 'nearest' });
      const rect = element.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, disabled: element.disabled };
    })()`)
    if (box && !box.disabled) {
      await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y })
      await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await sleep(500)
      return
    }
    await sleep(250)
  }
  throw new Error(`Completed fixture session is not available: ${sessionId}`)
}

async function selectElementPlusOption(client, selector, expectedText) {
  await clickSelector(client, selector)
  await clickElementContaining(client, '.el-select-dropdown__item', expectedText)
}

async function assertMatchSelection(client, resumeFileName, jobTitle) {
  await waitForExpression(client, `Boolean([...document.querySelectorAll('[data-testid="jobs-analysis"] .el-select')]
    .some((select) => select.innerText.includes(${JSON.stringify(resumeFileName)})))`)
  await waitForExpression(client, `Boolean(document.querySelector('.jobs-breadcrumb')?.innerText.includes(${JSON.stringify(jobTitle)}))`)
  await waitForExpression(client, "Boolean(document.querySelector('.match-result'))")
}

async function assertInputValue(client, selector, expectedValue) {
  await waitForExpression(client, `Boolean([...document.querySelectorAll(${JSON.stringify(selector)})]
    .some((root) => {
      const input = /^(INPUT|TEXTAREA)$/.test(root.tagName) ? root : root.querySelector('input, textarea');
      return input?.value === ${JSON.stringify(expectedValue)};
    }))`)
}

async function assertReadOnlyPersistedTask(client, feedback) {
  for (let index = 0; index < 30; index += 1) {
    const state = await elementBox(client, `(() => {
      const row = [...document.querySelectorAll('.task-row')].find((item) =>
        [...item.querySelectorAll('input, textarea')].some((input) => input.value === ${JSON.stringify(feedback)}));
      const feedbackInput = row && [...row.querySelectorAll('input, textarea')]
        .find((input) => input.value === ${JSON.stringify(feedback)});
      return {
        feedbackReadOnly: Boolean(feedbackInput && (feedbackInput.disabled || feedbackInput.readOnly)),
        evidenceReadOnly: !document.querySelector('[data-testid="plan-task"] .evidence-input')
      };
    })()`)
    if (state?.feedbackReadOnly && state?.evidenceReadOnly) {
      return
    }
    await sleep(500)
  }
  throw new Error(`Historical plan task feedback is not read-only: ${feedback}`)
}

async function waitForButton(client, label) {
  for (let index = 0; index < 24; index += 1) {
    const box = await elementBox(client, `(() => {
      const buttons = [...document.querySelectorAll('button')];
      const button = buttons.find((item) => item.innerText.includes(${JSON.stringify(label)}));
      if (!button) return null;
      button.scrollIntoView({ block: 'center', inline: 'nearest' });
      const rect = button.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, disabled: button.disabled, text: button.innerText };
    })()`)
    if (box && !box.disabled) {
      return box
    }
    await sleep(500)
  }
  return null
}

async function elementBox(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true })
  return result.result.value
}

async function assertText(client, expectedParts) {
  let latestText = ''
  for (let index = 0; index < 30; index += 1) {
    latestText = await bodyText(client)
    const missing = expectedParts.filter((part) => !latestText.includes(part))
    if (!missing.length) {
      return
    }
    await sleep(500)
  }
  const missing = expectedParts.filter((part) => !latestText.includes(part))
  throw new Error(`Missing text: ${missing.join(', ')}`)
}

async function assertNoText(client, unexpectedParts) {
  const text = await bodyText(client)
  const found = unexpectedParts.filter((part) => text.includes(part))
  if (found.length) {
    throw new Error(`Unexpected text: ${found.join(', ')}`)
  }
}

async function assertNoHorizontalOverflow(client) {
  const dimensions = await elementBox(client, `(() => ({
    viewport: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth
  }))()`)
  const widest = Math.max(dimensions.scrollWidth, dimensions.bodyScrollWidth)
  if (widest > dimensions.viewport + 1) {
    throw new Error(`Horizontal overflow detected: viewport=${dimensions.viewport}, content=${widest}`)
  }
}

async function verifyStudentScrolling(client, fixture) {
  const results = []
  for (const [width, height] of [[1440, 720], [390, 844], [320, 900]]) {
    const mode = width < 700 ? 'touch' : 'wheel'
    await setViewport(client, width, height)
    await client.send('Emulation.setTouchEmulationEnabled', { enabled: mode === 'touch', maxTouchPoints: 1 })
    for (const module of ['resume', 'jobs', 'plan', 'interview', 'knowledge']) {
      await navigate(client, `${baseUrl}/student/${module}`)
      await assertStudentWorkspace(client, module)
      await assertNoHorizontalOverflow(client)
      const label = `${module}-${width}x${height}`
      results.push({ module, width, height, ...await assertDocumentScrolling(client, mode, label) })
      if (module === 'resume') {
        await verifyPreviewScrollRelease(client, mode, label)
        if (mode === 'touch') {
          await clickSelector(client, '.mobile-menu')
          await waitForExpression(client, "Boolean(document.querySelector('.side-nav.is-open'))")
          await clickSelector(client, '.nav-backdrop')
          await waitForExpression(client, "!document.querySelector('.side-nav.is-open') && !document.querySelector('.nav-backdrop')")
          await assertDocumentScrolling(client, mode, `${label}-drawer-closed`)
        }
      }
    }
    if (width <= 390) {
      const planQuery = fixture ? `?planId=${encodeURIComponent(fixture.planId)}` : ''
      const sessionQuery = fixture ? `?sessionId=${encodeURIComponent(fixture.sessionId)}` : ''
      const routes = [
        ['resume', 'profile', '/student/resume/profile'], ['resume', 'templates', '/student/resume/templates'],
        ['resume', 'edit', '/student/resume/edit'], ['resume', 'diagnosis', '/student/resume/diagnosis'],
        ['resume', 'versions', '/student/resume/versions'], ['resume', 'history', '/student/resume/history'],
        ['plan', 'tasks', '/student/plan/tasks' + planQuery], ['plan', 'create', '/student/plan/create' + planQuery],
        ['plan', 'review', '/student/plan/review' + planQuery], ['plan', 'history', '/student/plan/history' + planQuery],
        ['interview', 'practice', '/student/interview/practice' + sessionQuery],
        ['interview', 'report', '/student/interview/report' + sessionQuery], ['interview', 'history', '/student/interview/history' + sessionQuery],
        ['knowledge', 'answer', '/student/knowledge/answer'], ['knowledge', 'sources', '/student/knowledge/sources'],
        ['knowledge', 'history', '/student/knowledge/history']
      ]
      if (fixture) {
        routes.push(['resume', 'original', `/student/resume/original/${encodeURIComponent(fixture.resumeId)}`])
        routes.push(['resume', 'original-diagnosis', `/student/resume/original/${encodeURIComponent(fixture.resumeId)}/diagnosis`])
        const plan = await fetchFixtureData(client, `/api/ai/learning/plans/${encodeURIComponent(fixture.planId)}`)
        if (plan.tasks?.length) routes.push(['plan', 'task', `/student/plan/tasks/${encodeURIComponent(plan.tasks[0].taskId)}${planQuery}`])
      }
      for (const [module, page, route] of routes) {
        await navigate(client, baseUrl + route)
        await assertOnlyModulePage(client, module, page)
        await assertNoHorizontalOverflow(client)
        results.push({ module, page, width, height, ...await assertDocumentScrolling(client, mode, `${module}-${page}-${width}x${height}`) })
      }
    }
  }
  await client.send('Emulation.setTouchEmulationEnabled', { enabled: false })
  fs.writeFileSync(path.join(artifactsDir, 'student-scroll-results.json'), JSON.stringify(results, null, 2), 'utf8')
  console.log(`Real document scrolling passed: ${results.length} module/viewports (mouse wheel and touch swipes).`)
}

async function documentScrollState(client) {
  return elementBox(client, `(() => {
    const root = document.scrollingElement;
    const bodyStyle = getComputedStyle(document.body);
    const htmlStyle = getComputedStyle(document.documentElement);
    const clipped = [...document.querySelectorAll('.app-shell, .main-view, .workspace-content, .student-workspace')]
      .filter((element) => ['hidden', 'clip'].includes(getComputedStyle(element).overflowY)
        && element.scrollHeight > element.clientHeight + 2)
      .map((element) => element.className);
    return {
      top: root.scrollTop,
      maximum: Math.max(0, root.scrollHeight - root.clientHeight),
      viewport: root.clientHeight,
      bodyOverflow: bodyStyle.overflowY,
      htmlOverflow: htmlStyle.overflowY,
      bodyPosition: bodyStyle.position,
      clipped,
      contentBottom: document.querySelector('.workspace-content')?.getBoundingClientRect().bottom
    };
  })()`)
}

async function scrollInputPoint(client) {
  const point = await elementBox(client, `(() => {
    const width = document.documentElement.clientWidth;
    const height = innerHeight;
    const main = document.querySelector('.main-view')?.getBoundingClientRect();
    const candidates = [width - 8, width - 24, main ? main.left + 8 : 8];
    for (const x of candidates) {
      const y = Math.round(height * .76);
      const hit = document.elementFromPoint(x, y);
      if (!hit || hit.closest('.side-nav, .resume-pdf-preview, input, textarea, select, button, a, [role="combobox"], .el-overlay, .nav-backdrop')) continue;
      let nestedScroller = false;
      for (let element = hit; element && element !== document.body && element !== document.documentElement; element = element.parentElement) {
        const style = getComputedStyle(element);
        if ((['auto', 'scroll'].includes(style.overflowY) && element.scrollHeight > element.clientHeight + 2)
          || style.touchAction === 'none') nestedScroller = true;
      }
      if (!nestedScroller) return { x, y, height };
    }
    return null;
  })()`)
  if (!point) throw new Error('No safe document scroll input point outside nested scroll regions')
  return point
}

async function dispatchDocumentScroll(client, mode, direction) {
  const point = await scrollInputPoint(client)
  if (mode === 'wheel') {
    await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: point.x, y: point.y })
    await client.send('Input.dispatchMouseEvent', {
      type: 'mouseWheel', x: point.x, y: point.y, deltaX: 0, deltaY: direction * point.height * .8
    })
  } else {
    const start = direction > 0 ? point.y : Math.round(point.height * .28)
    const end = direction > 0 ? Math.round(point.height * .28) : point.y
    const touchPoint = (y) => [{ x: point.x, y, radiusX: 4, radiusY: 4, force: 1, id: 0 }]
    await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: touchPoint(start) })
    for (let index = 1; index <= 8; index += 1) {
      await client.send('Input.dispatchTouchEvent', {
        type: 'touchMove', touchPoints: touchPoint(Math.round(start + (end - start) * index / 8))
      })
      await sleep(16)
    }
    await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  }
  await sleep(300)
}

async function scrollDocumentToEdge(client, mode, direction, label) {
  let state = await documentScrollState(client)
  const maximumSteps = Math.min(100, Math.ceil(state.maximum / Math.max(1, state.viewport * .3)) + 8)
  for (let index = 0; index < maximumSteps; index += 1) {
    const distance = direction > 0 ? state.maximum - state.top : state.top
    if (distance <= 2) return state
    const previousTop = state.top
    await dispatchDocumentScroll(client, mode, direction)
    state = await documentScrollState(client)
    if (Math.abs(state.top - previousTop) <= .5) {
      throw new Error(`Document ${mode} scroll is blocked in ${label}: ${JSON.stringify(state)}`)
    }
  }
  throw new Error(`Document ${mode} scroll did not reach ${direction > 0 ? 'bottom' : 'top'} in ${label}: ${JSON.stringify(state)}`)
}

async function assertDocumentScrolling(client, mode, label) {
  const initial = await documentScrollState(client)
  if (['hidden', 'clip'].includes(initial.bodyOverflow) || ['hidden', 'clip'].includes(initial.htmlOverflow)
    || initial.bodyPosition === 'fixed' || initial.clipped.length) {
    throw new Error(`Document has a residual scroll lock or clipped content in ${label}: ${JSON.stringify(initial)}`)
  }
  await scrollDocumentToEdge(client, mode, -1, label)
  const top = await documentScrollState(client)
  const bottom = await scrollDocumentToEdge(client, mode, 1, label)
  if (top.maximum > 2 && bottom.top <= top.top + 1) {
    throw new Error(`Document did not move with real ${mode} input in ${label}`)
  }
  if (bottom.contentBottom > bottom.viewport + 2) {
    throw new Error(`Workspace bottom is unreachable in ${label}: ${JSON.stringify(bottom)}`)
  }
  await screenshot(client, `scroll-${label}-bottom.png`)
  await scrollDocumentToEdge(client, mode, -1, label)
  await assertNoHorizontalOverflow(client)
  return { mode, scrollRange: top.maximum, reachedBottom: true, moved: bottom.top > top.top + 1 }
}

async function verifyPreviewScrollRelease(client, mode, label) {
  const hasPreview = await elementBox(client, "Boolean(document.querySelector('[data-testid=\"resume-pdf-expand\"]')?.getClientRects().length)")
  if (!hasPreview) return
  await verifyPreviewWheelBoundary(client, label)
  await clickSelector(client, '[data-testid="resume-pdf-expand"]')
  await waitForExpression(client, "Boolean(document.querySelector('.resume-pdf-overlay .resume-pdf-preview.expanded'))")
  const expandedOverscroll = await elementBox(client, "getComputedStyle(document.querySelector('.resume-pdf-overlay .resume-pdf-pages')).overscrollBehaviorY")
  if (expandedOverscroll !== 'contain') {
    throw new Error(`Expanded PDF should contain boundary scrolling in ${label}: ${expandedOverscroll}`)
  }
  await client.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await waitForExpression(client, "!document.querySelector('.resume-pdf-overlay')")
  await assertDocumentScrolling(client, mode, `${label}-preview-closed`)
}

async function verifyPreviewWheelBoundary(client, label) {
  const state = await elementBox(client, `(() => {
    const pages = document.querySelector('.resume-pdf-pages');
    const root = document.scrollingElement;
    if (!pages || !root) return null;
    // Position the nested viewport for the boundary probe; only wheel input may satisfy the assertion.
    pages.scrollIntoView({ block: 'center', inline: 'nearest' });
    const rect = pages.getBoundingClientRect();
    const maximum = Math.max(0, root.scrollHeight - root.clientHeight);
    const direction = maximum - root.scrollTop > 2 ? 1 : -1;
    const pagesMaximum = Math.max(0, pages.scrollHeight - pages.clientHeight);
    pages.scrollTop = direction > 0 ? pagesMaximum : 0;
    return {
      x: Math.round(rect.left + rect.width / 2),
      y: Math.round((Math.max(0, rect.top) + Math.min(innerHeight, rect.bottom)) / 2),
      documentTop: root.scrollTop,
      documentMaximum: maximum,
      pagesTop: pages.scrollTop,
      pagesMaximum,
      direction,
      overscroll: getComputedStyle(pages).overscrollBehaviorY
    };
  })()`)
  if (!state || state.documentMaximum <= 2) return
  if (state.overscroll !== 'auto') {
    throw new Error(`Inline PDF should allow boundary scrolling to the document in ${label}: ${state.overscroll}`)
  }
  await sleep(200)
  await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: state.x, y: state.y })
  await client.send('Input.dispatchMouseEvent', {
    type: 'mouseWheel', x: state.x, y: state.y, deltaX: 0, deltaY: state.direction * 780
  })
  await sleep(350)
  const after = await elementBox(client, `(() => ({
    documentTop: document.scrollingElement?.scrollTop || 0,
    pagesTop: document.querySelector('.resume-pdf-pages')?.scrollTop || 0
  }))()`)
  if ((after.documentTop - state.documentTop) * state.direction <= 1) {
    throw new Error(`Wheel at PDF boundary did not reach document in ${label}: ${JSON.stringify({ state, after })}`)
  }
  await client.send('Runtime.evaluate', { expression: 'document.scrollingElement.scrollTop = 0' })
  await client.send('Runtime.evaluate', { expression: "document.querySelector('.resume-pdf-pages')?.scrollTo(0, 0)" })
  await sleep(200)
}

async function assertNoInternalHorizontalOverflow(client, selector) {
  const overflowing = await elementBox(client, `(() => [...document.querySelectorAll(${JSON.stringify(selector)})]
    .map((element) => ({ clientWidth: element.clientWidth, scrollWidth: element.scrollWidth }))
    .find((dimensions) => dimensions.scrollWidth > dimensions.clientWidth + 1) || null)()`)
  if (overflowing) {
    throw new Error(`Internal horizontal overflow in ${selector}: client=${overflowing.clientWidth}, content=${overflowing.scrollWidth}`)
  }
}

async function assertStudentWorkspace(client, module) {
  await waitForExpression(client, "Boolean(document.querySelector('.student-workspace'))")
  const redundantChrome = await elementBox(client, "Boolean(document.querySelector('.student-workspace .eyebrow, .compact-workspace-header, .target-role-control'))")
  if (redundantChrome) {
    throw new Error(`Redundant heading or target role row remains in student ${module}`)
  }

  if (module === 'interview') {
    await assertText(client, ['开始辅导练习', '本次目标岗位', '题目数量'])
    await assertNoText(client, ['模拟面试会话', 'AI INTERVIEW STUDIO'])
    await waitForExpression(client, "Boolean(document.querySelector('[aria-label=\"本次目标岗位\"]')?.getClientRects().length)")
  }

  if (module === 'knowledge') {
    await assertText(client, ['仅检索', 'AI 回答', '检索'])
    await assertNoText(client, ['RAG KNOWLEDGE BASE', 'RAG 知识库问答'])
    const placeholder = await elementBox(client, "Boolean(document.querySelector('.knowledge-overview, .knowledge-intro, .knowledge-shell .el-empty'))")
    if (placeholder) {
      throw new Error('Initial knowledge page still contains decorative statistics or an empty illustration')
    }
    await waitForExpression(client, "Boolean(document.querySelector('.knowledge-search input')?.getClientRects().length) && Boolean(document.querySelector('.knowledge-search button')?.getClientRects().length)")
  }

  if (module === 'plan') {
    await waitForExpression(client, "Boolean(document.querySelector('[data-testid=\"plan-today\"]'))")
  }
  if (module !== 'jobs') {
    await waitForExpression(client, `Boolean(document.querySelector('[data-testid="${module}-subnav"]'))`)
    const rootPage = { resume: 'preview', plan: 'today', interview: 'start', knowledge: 'search' }[module]
    await assertOnlyModulePage(client, module, rootPage)
  }
}

async function setDetailsOpen(client, selector, open) {
  await waitForExpression(client, `document.querySelector(${JSON.stringify(selector)})?.tagName === 'DETAILS'`)
  const current = await elementBox(client, `document.querySelector(${JSON.stringify(selector)}).open`)
  if (current !== open) {
    await clickSelector(client, `${selector} > summary`)
  }
  await waitForExpression(client, `document.querySelector(${JSON.stringify(selector)}).open === ${open}`)
}

async function openTaskManagement(client, feedback) {
  const taskIndex = await elementBox(client, `(() => [...document.querySelectorAll('.task-row')]
    .findIndex((row) => [...row.querySelectorAll('input, textarea')]
      .some((input) => input.value === ${JSON.stringify(feedback)})))()`)
  if (!Number.isInteger(taskIndex) || taskIndex < 0) {
    throw new Error(`Task with persisted feedback is missing: ${feedback}`)
  }
  if (await elementBox(client, "Boolean(document.querySelector('.task-management-details'))")) {
    await setDetailsOpen(client, `.task-row:nth-child(${taskIndex + 1}) .task-management-details`, true)
  }
}

async function assertDetailsToggle(client, selector) {
  const exists = await elementBox(client, `Boolean(document.querySelector(${JSON.stringify(selector)}))`)
  if (!exists) {
    throw new Error(`Details control not available: ${selector}`)
  }
  const initiallyOpen = await elementBox(client, `document.querySelector(${JSON.stringify(selector)}).open`)
  if (initiallyOpen) {
    throw new Error(`Details control should be collapsed by default: ${selector}`)
  }
  await clickSelector(client, `${selector} > summary`)
  await waitForExpression(client, `document.querySelector(${JSON.stringify(selector)}).open === true`)
  await screenshot(client, '01b-student-resume-report-expanded.png')
  await clickSelector(client, `${selector} > summary`)
  await waitForExpression(client, `document.querySelector(${JSON.stringify(selector)}).open === false`)
}

async function verifyGlobalSearch(client) {
  await clickSelector(client, '.global-search')
  await waitForExpression(client, "Boolean(document.querySelector('.search-overlay'))")
  await fillInput(client, '.command-search-input input', '学习路径')
  await client.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 })
  await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 })
  await waitForExpression(client, "location.pathname === '/student/plan' && !document.querySelector('.search-overlay')")
}

async function assertSelectDisplay(client, expected) {
  for (let index = 0; index < 30; index += 1) {
    const found = await elementBox(client, `(() => [...document.querySelectorAll('.el-select')]
      .some((select) => select.innerText.includes(${JSON.stringify(expected)})))()`)
    if (found) {
      return
    }
    await sleep(500)
  }
  throw new Error(`Missing select display containing: ${expected}`)
}

async function assertPersistedTask(client, feedback) {
  for (let index = 0; index < 30; index += 1) {
    const found = await elementBox(client, `(() => [...document.querySelectorAll('.task-row')]
      .some((row) => row.innerText.includes('已完成')
        && [...row.querySelectorAll('input, textarea')]
          .some((input) => input.value === ${JSON.stringify(feedback)} && !input.disabled)))()`)
    if (found) {
      return
    }
    await sleep(500)
  }
  throw new Error(`Persisted completed task is not selected and editable: ${feedback}`)
}

async function waitForText(client, expected) {
  for (let index = 0; index < 20; index += 1) {
    if ((await bodyText(client)).includes(expected)) {
      return
    }
    await sleep(500)
  }
  throw new Error(`Timed out waiting for text: ${expected}`)
}

async function scrollToText(client, expected) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const target = [...document.querySelectorAll('h1,h2,h3,strong,span,button,label')]
          .find((item) => item.innerText && item.innerText.includes(${JSON.stringify(expected)}));
        if (target) {
          target.scrollIntoView({ block: 'center', inline: 'nearest' });
        }
      })()
    `
  })
  await sleep(700)
}

async function waitForExpression(client, expression) {
  for (let index = 0; index < 30; index += 1) {
    const result = await client.send('Runtime.evaluate', {
      expression,
      returnByValue: true
    })
    if (result.result.value === true) {
      return
    }
    await sleep(500)
  }
  throw new Error(`Timed out waiting for expression: ${expression}`)
}

async function bodyText(client) {
  const result = await client.send('Runtime.evaluate', {
    expression: 'document.body.innerText',
    returnByValue: true
  })
  return result.result.value || ''
}

async function screenshot(client, fileName) {
  const result = await client.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  fs.writeFileSync(path.join(artifactsDir, fileName), Buffer.from(result.data, 'base64'))
}

async function stopBrowser(proc) {
  await stopProcessTree(proc)
}

async function stopProcessTree(proc) {
  if (!proc || proc.killed) {
    return
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(proc.pid), '/t', '/f'], { stdio: 'ignore' })
  } else {
    proc.kill()
  }
  await sleep(300)
}

main().catch((error) => {
  if (devServer) {
    if (process.platform === 'win32') {
      spawnSync('taskkill', ['/pid', String(devServer.pid), '/t', '/f'], { stdio: 'ignore' })
    } else {
      devServer.kill()
    }
  }
  console.error(error)
  process.exit(1)
})
