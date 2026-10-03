const { spawn, spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

if (typeof WebSocket === 'undefined') {
  const relaunched = spawnSync(process.execPath, ['--experimental-websocket', __filename, ...process.argv.slice(2)], { stdio: 'inherit', env: process.env })
  process.exit(relaunched.status || 0)
}

const rootDir = path.resolve(__dirname, '../..')
const baseUrl = (process.env.E2E_RESUME_BASE_URL || 'http://localhost').replace(/\/+$/, '')
const gatewayUrl = (process.env.E2E_RESUME_API_URL || 'http://localhost:18080').replace(/\/+$/, '')
const runId = new Date().toISOString().replace(/[:.]/g, '-')
const artifactsDir = path.resolve(process.env.E2E_RESUME_ARTIFACTS_DIR || path.join(rootDir, 'output/playwright/resume-workspace', runId))
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const username = `resume_e2e_${Date.now().toString(36)}_${crypto.randomBytes(3).toString('hex')}`
const password = `ResumeE2e!${crypto.randomBytes(12).toString('hex')}`
const report = { runId, startedAt: new Date().toISOString(), mode: 'live-browser', frontend: baseUrl, gateway: gatewayUrl, account: { username, role: 'STUDENT' }, checks: [], screenshots: [], status: 'RUNNING' }
let session
let browser
let client

function check(name, condition, detail = {}) {
  if (!condition) throw new Error(`Acceptance failed: ${name}`)
  report.checks.push({ name, passed: true, ...detail })
  console.log(`PASS: ${name}`)
}
async function api(route, init = {}) {
  const headers = { 'Content-Type': 'application/json', ...(session ? { Authorization: `Bearer ${session.token}` } : {}), ...(init.headers || {}) }
  const response = await fetch(`${gatewayUrl}${route}`, { ...init, headers, signal: AbortSignal.timeout(30000) })
  const body = await response.json()
  if (!response.ok || body.code !== 0 || body.data == null) throw new Error(`${route} returned ${response.status}: ${body.message || 'no data'}`)
  return body.data
}
async function evaluate(expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (result.exceptionDetails) throw new Error('Browser evaluation failed')
  return result.result.value
}
async function waitFor(expression, label, timeout = 30000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return
    await sleep(250)
  }
  throw new Error(`Timed out waiting for ${label}`)
}
async function click(selector, label) {
  const deadline = Date.now() + 15000
  while (Date.now() < deadline) {
    const box = await evaluate(`(() => { const e = document.querySelector(${JSON.stringify(selector)}); if (!e || !e.getClientRects().length || e.disabled) return null; e.scrollIntoView({ block: 'center' }); const r = e.getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 } })()`)
    if (box) {
      await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 })
      await sleep(200)
      return
    }
    await sleep(200)
  }
  throw new Error(`Control unavailable: ${label}`)
}
async function screenshot(name) {
  const image = await client.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  fs.mkdirSync(artifactsDir, { recursive: true })
  fs.writeFileSync(path.join(artifactsDir, name), Buffer.from(image.data, 'base64'))
  report.screenshots.push(name)
}
async function navigate(route) {
  await client.send('Page.navigate', { url: `${baseUrl}${route}` })
  await waitFor(`location.pathname === ${JSON.stringify(route)} && document.readyState === 'complete'`, route)
}
async function startBrowser() {
  const browserPath = process.env.E2E_BROWSER || [
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium'
  ].find(candidate => fs.existsSync(candidate))
  if (!browserPath) throw new Error('No Chromium browser found')
  const port = Number(process.env.E2E_CDP_PORT || 9300 + Math.floor(Math.random() * 400))
  const userDataDir = path.join(process.env.TEMP || artifactsDir, `aicampus-e2e-${Date.now()}`)
  const proc = spawn(browserPath, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${port}`, `--user-data-dir=${userDataDir}`, 'about:blank'], { stdio: 'ignore' })
  for (let i = 0; i < 30; i += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })
      if (response.ok) return { process: proc, webSocketDebuggerUrl: (await response.json()).webSocketDebuggerUrl }
    } catch {}
    await sleep(500)
  }
  proc.kill()
  throw new Error('Browser DevTools endpoint did not become ready')
}
async function connect(wsUrl) {
  const ws = new WebSocket(wsUrl)
  const pending = new Map()
  let sequence = 0
  ws.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (!message.id || !pending.has(message.id)) return
    const request = pending.get(message.id)
    pending.delete(message.id)
    if (message.error) request.reject(new Error(JSON.stringify(message.error)))
    else request.resolve(message.result || {})
  })
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', reject, { once: true })
  })
  return { send(method, params = {}) { const id = ++sequence; ws.send(JSON.stringify({ id, method, params })); return new Promise((resolve, reject) => pending.set(id, { resolve, reject })) }, close() { ws.close() } }
}
async function stopBrowser(proc) {
  if (!proc || proc.killed) return
  if (process.platform === 'win32') spawnSync('taskkill', ['/pid', String(proc.pid), '/t', '/f'], { stdio: 'ignore' })
  else proc.kill()
}

async function main() {
  fs.mkdirSync(artifactsDir, { recursive: true })
  try {
    const front = await fetch(baseUrl, { signal: AbortSignal.timeout(5000) })
    check('frontend is reachable', front.ok)
    session = await api('/api/auth/register', { method: 'POST', body: JSON.stringify({ username, password, displayName: 'Resume E2E', role: 'STUDENT' }) })
    report.account.userId = session.userId
    const jobs = await api('/api/jobs')
    const job = jobs.find(item => /java/i.test(item.title)) || jobs.find(item => /后端/.test(item.title))
    if (!job) throw new Error('No Java or backend job is available')
    report.jobId = job.jobId
    browser = await startBrowser()
    client = await connect(browser.webSocketDebuggerUrl)
    await client.send('Page.enable')
    await client.send('Runtime.enable')
    await client.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
    await navigate('/login')
    await waitFor('Boolean(document.querySelector("input[autocomplete=\\"username\\"]"))', 'login form')
    const loginUser = 'document.querySelector("input[autocomplete=\\"username\\"]")'
    const loginPassword = 'document.querySelector("input[autocomplete=\\"current-password\\"]")'
    for (const [expression, value] of [[loginUser, username], [loginPassword, password]]) {
      await evaluate(`(() => { const e = ${expression}; e.focus(); e.value = ${JSON.stringify(value)}; e.dispatchEvent(new Event('input', { bubbles: true })); e.dispatchEvent(new Event('change', { bubbles: true })); })()`)
    }
    await click('.login-form button[type="submit"], .login-form button', 'login')
    await waitFor("location.pathname === '/student/resume' && Boolean(document.querySelector('.resume-builder'))", 'resume workspace')
    await waitFor("!document.querySelector('.resume-builder .el-loading-mask')", 'resume workspace loading')
    check('resume workspace loaded', true)
    await waitFor("document.querySelectorAll('.template-card').length === 8", 'eight adapted templates')
    check('eight adapted templates are visible', true)
    await click('.builder-generate > .el-select', 'target job selector')
    await waitFor("[...document.querySelectorAll('.el-select-dropdown__item')].some(e => e.getClientRects().length && e.textContent.includes('" + job.title.replace(/'/g, "\\'") + "'))", 'target job options')
    const selected = await evaluate(`(() => { const title = ${JSON.stringify(job.title)}; const option = [...document.querySelectorAll('.el-select-dropdown__item')].find(e => e.getClientRects().length && e.textContent.includes(title)); if (!option) return false; option.click(); return true })()`)
    check('target job option can be selected', selected)
    await waitFor(`document.querySelector('.selected-job')?.textContent.includes(${JSON.stringify(job.title)})`, 'selected target job')
    check('selected job is shown in workspace', true, { jobId: job.jobId, title: job.title })
    await screenshot('workspace-job-selected.png')
    report.status = 'PASSED'
    report.finishedAt = new Date().toISOString()
    console.log(`Resume workspace browser acceptance passed: ${artifactsDir}`)
  } catch (error) {
    report.status = 'FAILED'
    report.error = String(error.message || error)
    report.finishedAt = new Date().toISOString()
    if (client) { try { await screenshot('failure.png') } catch {} }
    console.error(`Resume workspace browser acceptance failed: ${report.error}`)
    process.exitCode = 1
  } finally {
    fs.writeFileSync(path.join(artifactsDir, 'report.json'), JSON.stringify(report, null, 2), 'utf8')
    if (client) client.close()
    if (browser?.process) await stopBrowser(browser.process)
  }
}
main()
