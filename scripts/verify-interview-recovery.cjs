const fs = require('node:fs')
const path = require('node:path')
const { spawn } = require('node:child_process')
const { isDeepStrictEqual } = require('node:util')

const root = path.resolve(__dirname, '..')
const reportPath = process.argv[2]
const apiUrl = (process.env.E2E_INTERVIEW_API_URL || 'http://localhost:18080').replace(/\/+$/, '')

async function main() {
  if (!reportPath) throw new Error('Provide a passed interview browser report path')
  const browser = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
  if (browser.status !== 'PASSED' || !browser.recovery || !browser.account?.token) {
    throw new Error('A passed browser report with recovery fixtures is required')
  }
  const token = browser.account.token
  const fixtures = browser.recovery
  const routes = [
    `/api/ai/interview/sessions/${fixtures.coachingSessionId}`,
    `/api/ai/interview/sessions/${fixtures.mockSessionId}`,
    `/api/ai/learning/plans/${fixtures.learningPlanId}`,
    `/api/ai/learning/plans/${fixtures.learningDraftId}`,
    `/api/ai/learning/plans/${fixtures.learningPlanId}/tasks/${fixtures.taskId}/evidence`
  ]
  async function read(route) {
    const response = await fetch(apiUrl + route, {
      headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(15000)
    })
    const body = await response.json()
    if (!response.ok || body.code !== 0 || body.data == null) throw new Error('Recovery fixture unavailable')
    return body.data
  }
  const before = await Promise.all(routes.map(read))
  await new Promise((resolve, reject) => {
    const child = spawn('docker', ['compose', 'restart', 'ai-service', 'resume-service'], { cwd: root, stdio: 'pipe' })
    child.on('error', reject)
    child.on('exit', code => code === 0 ? resolve() : reject(new Error(`Docker restart failed (${code})`)))
  })
  let after
  const deadline = Date.now() + 120000
  while (Date.now() < deadline) {
    try { after = await Promise.all(routes.map(read)); break } catch {
      await new Promise(resolve => setTimeout(resolve, 1500))
    }
  }
  if (!after) throw new Error('Services did not recover within two minutes')
  const checks = []
  function check(name, actual, expected) {
    if (!isDeepStrictEqual(actual, expected)) throw new Error(`Recovery mismatch: ${name}`)
    checks.push({ name, passed: true })
  }
  for (let i = 0; i < 2; i++) {
    const fields = ['status', 'mode', 'sourceType', 'sourceId', 'sourceMaterial', 'sourceReferences',
      'questions', 'answers', 'attempts', 'report', 'partialReport', 'partialReports', 'actionPreviews']
    for (const field of fields) check(`${i === 0 ? 'coaching' : 'mock'} ${field}`, after[i][field], before[i][field])
    for (const field of ['startedAt', 'pausedAt', 'timerMinutes', 'pausedSeconds', 'runningSince']) {
      check(`${i === 0 ? 'coaching' : 'mock'} timer ${field}`, after[i].timer?.[field], before[i].timer?.[field])
    }
    if (before[i].status === 'COMPLETED' || before[i].timer?.pausedAt) {
      check(`${i === 0 ? 'coaching' : 'mock'} frozen elapsed`, after[i].timer?.accumulatedSeconds, before[i].timer?.accumulatedSeconds)
    }
  }
  check('active learning plan snapshot', after[2], before[2])
  check('draft learning revision snapshot', after[3], before[3])
  check('learning evidence history and confirmation', after[4], before[4])
  const result = { status: 'PASSED', completedAt: new Date().toISOString(), checks }
  const output = path.join(path.dirname(reportPath), 'recovery-report.json')
  fs.writeFileSync(output, JSON.stringify(result, null, 2))
  console.log(`PASSED: ${checks.length} restart recovery checks`)
  console.log(`Report: ${output}`)
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
