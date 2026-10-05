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
const browserErrors = []
let pdfBlobRequests = 0
const downloadDir = path.join(artifactsDir, 'downloads')
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
    const box = await evaluate(`(() => { const e = document.querySelector(${JSON.stringify(selector)}); if (!e || !e.getClientRects().length || e.disabled) return null; e.scrollIntoView({ block: 'nearest', behavior: 'instant' }); const r = e.getBoundingClientRect(); const x = r.left + r.width / 2, y = r.top + r.height / 2; const hit = document.elementFromPoint(x, y); return hit && (hit === e || e.contains(hit)) ? { x, y } : null })()`)
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
async function fill(selector, value) {
  await waitFor(`Boolean(document.querySelector(${JSON.stringify(selector)}))`, selector)
  await evaluate(`(() => { const e = document.querySelector(${JSON.stringify(selector)}); e.focus(); e.value = ${JSON.stringify(value)}; e.dispatchEvent(new Event('input', { bubbles: true })); e.dispatchEvent(new Event('change', { bubbles: true })); })()`)
}
const byId = id => `[data-testid="${id}"]`
const inputById = id => `input${byId(id)}, textarea${byId(id)}, ${byId(id)} input, ${byId(id)} textarea`
const canvasReady = `(() => { const c = document.querySelector('[data-resume-pdf-page="1"]'); const download = document.querySelector('[data-testid="resume-download-pdf"]'); if (!c || !c.getClientRects().length || c.width < 400 || c.height < 600 || !download || download.disabled || document.querySelector('.preview-notice') || document.querySelector('[data-testid="resume-update-preview"]')?.classList.contains('is-loading') || document.querySelector('[data-testid="resume-save-draft"]')?.classList.contains('is-loading')) return false; const a = c.getContext('2d').getImageData(0,0,c.width,c.height).data; let ink = 0; for (let i=0;i<a.length;i+=16) if(a[i]<230 || a[i+1]<230 || a[i+2]<230) ink++; return ink > 150 })()`
async function waitCanvas(label) {
  await waitFor(canvasReady, label, 180000)
  check(label, true)
}
async function fitAndCheckViewer(label) {
  await evaluate("document.querySelector('[data-testid=\"resume-pdf-preview\"]')?.scrollIntoView({block: 'nearest'})")
  await click(byId('resume-pdf-fit-page'), 'fit whole page')
  await waitFor(`(() => {
    const area = document.querySelector('[data-testid="resume-pdf-pages"]');
    const viewer = document.querySelector('[data-testid="resume-pdf-preview"]');
    const canvas = document.querySelector('[data-resume-pdf-page="' + (viewer?.dataset.currentPage || '1') + '"]');
    if (!area || !canvas) return false;
    const a = area.getBoundingClientRect(), c = canvas.getBoundingClientRect();
    return c.width > 100 && c.height > 100 && c.left >= a.left - 1 && c.right <= a.right + 1 && c.top >= a.top - 1 && c.bottom <= a.bottom + 1;
  })()`, label)
  const geometry = await evaluate(`(() => {
    const viewer = document.querySelector('[data-testid="resume-pdf-preview"]');
    const area = document.querySelector('[data-testid="resume-pdf-pages"]');
    const canvas = document.querySelector('[data-resume-pdf-page="' + (viewer.dataset.currentPage || '1') + '"]');
    const toolbar = viewer.querySelector('.resume-pdf-toolbar').getBoundingClientRect();
    const a = area.getBoundingClientRect(), c = canvas.getBoundingClientRect();
    const v = viewer.getBoundingClientRect();
    return { pageWidth: c.width, pageHeight: c.height, viewportWidth: a.width, viewportHeight: a.height,
      toolbarVisible: toolbar.top >= 0 && toolbar.bottom <= innerHeight,
      viewerVisible: v.top >= -1 && v.bottom <= innerHeight + 1 && v.left >= -1 && v.right <= innerWidth + 1,
      canvasVisible: c.top >= -1 && c.bottom <= innerHeight + 1 && c.left >= -1 && c.right <= innerWidth + 1,
      overflow: document.documentElement.scrollWidth > innerWidth + 1 };
  })()`)
  check(label, geometry.toolbarVisible && geometry.viewerVisible && geometry.canvasVisible && !geometry.overflow, geometry)
}
async function checkZoomAndPan(label) {
  await click(byId('resume-pdf-fit-width'), 'fit page width')
  const width = await evaluate("document.querySelector('[data-resume-pdf-page]').getBoundingClientRect().width")
  const requests = pdfBlobRequests
  for (let i = 0; i < 5; i++) await click('[aria-label="放大"]', 'zoom PDF')
  await waitFor(`document.querySelector('[data-resume-pdf-page]').getBoundingClientRect().width > ${width + 5}`, 'zoomed page width')
  const geometry = await evaluate(`(() => {
    const area = document.querySelector('[data-testid="resume-pdf-pages"]');
    area.scrollLeft = 0; area.scrollTop = 0;
    const origin = area.querySelector('canvas').getBoundingClientRect(), originArea = area.getBoundingClientRect();
    const leftReachable = origin.left >= originArea.left - 1, topReachable = origin.top >= originArea.top - 1;
    area.scrollLeft = area.scrollWidth; area.scrollTop = area.scrollHeight;
    const a = area.getBoundingClientRect(), c = area.querySelector('canvas:last-child').getBoundingClientRect();
    const t = document.querySelector('.resume-pdf-toolbar').getBoundingClientRect();
    return { scrollLeft: area.scrollLeft, scrollTop: area.scrollTop, leftReachable, topReachable, rightReachable: c.right <= a.right + 1,
      bottomReachable: c.bottom <= a.bottom + 1, toolbarVisible: t.top >= 0 && t.bottom <= innerHeight,
      overflow: document.documentElement.scrollWidth > innerWidth + 1 };
  })()`)
  check(label, geometry.scrollLeft > 0 && geometry.scrollTop > 0 && geometry.leftReachable && geometry.topReachable && geometry.rightReachable && geometry.bottomReachable && geometry.toolbarVisible && !geometry.overflow, geometry)
  check(label + ' reuses loaded PDF', pdfBlobRequests === requests, { additionalRequests: pdfBlobRequests - requests })
}
async function checkExpandedViewer(label) {
  const selectedPage = await evaluate("document.querySelector('[data-testid=\"resume-pdf-preview\"]').dataset.currentPage")
  await click(byId('resume-pdf-expand'), 'expand PDF viewer')
  await waitFor(`document.querySelector(${JSON.stringify(byId('resume-pdf-close'))})?.getClientRects().length > 0`, 'expanded viewer')
  await fitAndCheckViewer(label + ' fits whole page')
  check(label + ' keeps selected page', await evaluate(`document.querySelector('[data-testid="resume-pdf-preview"]').dataset.currentPage === ${JSON.stringify(selectedPage)}`))
  const geometry = await evaluate(`(() => {
    const r = document.querySelector('[data-testid="resume-pdf-preview"]').getBoundingClientRect();
    return { left: r.left, right: r.right, top: r.top, bottom: r.bottom, width: innerWidth, height: innerHeight };
  })()`)
  check(label + ' stays inside screen', geometry.left >= -1 && geometry.right <= geometry.width + 1 && geometry.top >= -1 && geometry.bottom <= geometry.height + 1, geometry)
  await screenshot(label.replace(/[^a-z0-9]/gi, '-') + '.png')
  await client.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await waitFor(`!document.querySelector(${JSON.stringify(byId('resume-pdf-close'))})?.getClientRects().length`, 'close expanded viewer with Escape')
  await waitFor(`document.activeElement === document.querySelector(${JSON.stringify(byId('resume-pdf-expand'))})`, 'focus restored after layout settles')
  check(label + ' Escape restores focus', true)
}
async function checkTwoPagePreview(draft) {
  const data = JSON.parse(JSON.stringify(draft.data))
  const project = data.blocks.find(block => block.type === 'PROJECT')
  if (!project?.entries.length) throw new Error('Project fixture is missing')
  const original = project.entries[0]
  project.entries = Array.from({ length: 4 }, (_, index) => ({ ...original, id: 'pagination-' + index,
    title: '课程项目排版验收 ' + (index + 1), visible: true, confirmed: true,
    bullets: [
      '个人使用 Java 实现课程查询接口，编写输入校验测试并提交课程作品。',
      '逐项核对输入、处理过程和实际结果，记录异常情况并修正已有实现。' + '通过课程要求和现有材料对照检查，保留过程说明及验证记录。'.repeat(3),
      '完成课程验收并提交作品说明，实际结果和个人职责可在提交记录中核对。' + '复盘发现的问题并说明处理方式，没有测得的数据不写提升比例。'.repeat(2)
    ] }))
  const latest = await api(`/api/resumes/drafts/${draft.id}`)
  await api(`/api/resumes/drafts/${draft.id}`, { method: 'PATCH', body: JSON.stringify({ expectedRevision: latest.revision, templateId: 'T08', data, confirm: true }) })
  await navigate(`/student/resume?draftId=${encodeURIComponent(draft.id)}`)
  await waitCanvas('two-page exported PDF renders')
  await waitFor("document.querySelectorAll('[data-resume-pdf-page]').length === 2", 'two-page PDF fixture')
  await fitAndCheckViewer('two-page first page fits all four edges')
  await click('[aria-label="下一页"]', 'next PDF page')
  await waitFor("document.querySelector('.resume-pdf-toolbar').textContent.includes('2 / 2')", 'second page selected')
  check('page navigation keeps window position', await evaluate("document.querySelector('.resume-pdf-toolbar').getBoundingClientRect().top >= 0"))
  await evaluate("document.querySelector('[data-testid=\"resume-pdf-pages\"]').scrollTop = 0")
  await waitFor("document.querySelector('.resume-pdf-toolbar').textContent.includes('1 / 2')", 'manual scroll updates page number')
  await evaluate("const area = document.querySelector('[data-testid=\"resume-pdf-pages\"]'); area.scrollTop = area.scrollHeight")
  await waitFor("document.querySelector('.resume-pdf-toolbar').textContent.includes('2 / 2')", 'manual scroll selects last page')
  check('second page final edge is visible', await evaluate(`(() => {
    const a = document.querySelector('[data-testid="resume-pdf-pages"]').getBoundingClientRect();
    const c = document.querySelector('[data-resume-pdf-page="2"]').getBoundingClientRect();
    return c.bottom <= a.bottom + 1 && c.bottom > a.top && c.left >= a.left - 1 && c.right <= a.right + 1;
  })()`))
  await screenshot('06-two-page-bottom.png')
  await checkZoomAndPan('two-page zoom reaches right and bottom edges')
  await checkExpandedViewer('two-page expanded preview')
  await client.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await fitAndCheckViewer('mobile second page fits screen and viewer')
  check('mobile resize retains second page', await evaluate("document.querySelector('[data-testid=\"resume-pdf-preview\"]').dataset.currentPage === '2'"))
  await screenshot('07-mobile-second-page.png')
  await checkExpandedViewer('mobile two-page expanded preview')
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
}
async function download(format, testId) {
  await click(byId(testId), format + ' download')
  const deadline = Date.now() + 30000
  let file
  while (Date.now() < deadline) {
    file = fs.readdirSync(downloadDir).find(name => name.toLowerCase().endsWith('.' + format))
    if (file) break
    await sleep(250)
  }
  if (!file) throw new Error('Browser did not save ' + format + ' download')
  const bytes = fs.readFileSync(path.join(downloadDir, file))
  const signature = format === 'pdf' ? bytes.subarray(0,5).toString() === '%PDF-' : bytes[0] === 0x50 && bytes[1] === 0x4b
  check('browser downloaded valid ' + format, signature && bytes.length > 1000, { bytes: bytes.length, file })
}
async function screenshot(name) {
  const image = await client.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  fs.mkdirSync(artifactsDir, { recursive: true })
  fs.writeFileSync(path.join(artifactsDir, name), Buffer.from(image.data, 'base64'))
  report.screenshots.push(name)
}
async function navigate(route) {
  await client.send('Page.navigate', { url: `${baseUrl}${route}` })
  const target = new URL(route, baseUrl)
  const expectedParams = [...target.searchParams.entries()]
  await waitFor(`location.pathname === ${JSON.stringify(target.pathname)} && ${JSON.stringify(expectedParams)}.every(([key, value]) => new URLSearchParams(location.search).get(key) === value) && document.readyState === 'complete'`, route)
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
  const proc = spawn(browserPath, [...(process.env.E2E_HEADED === '1' ? [] : ['--headless=new']), '--disable-gpu', '--window-size=1440,980', `--remote-debugging-port=${port}`, `--user-data-dir=${userDataDir}`, 'about:blank'], { stdio: 'ignore' })
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
    if (message.method === 'Runtime.exceptionThrown') browserErrors.push(message.params.exceptionDetails.text)
    if (message.method === 'Log.entryAdded' && message.params.entry.level === 'error') browserErrors.push(message.params.entry.text)
    if (message.method === 'Network.requestWillBeSent' && message.params.request.url.startsWith('blob:')) pdfBlobRequests++
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
    await client.send('Log.enable')
    await client.send('Network.enable')
    fs.mkdirSync(downloadDir, { recursive: true })
    await client.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: downloadDir })
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
    await click(byId('resume-nav-profile'), 'start a fresh profile flow')
    await fill(inputById('resume-name'), '陈浏览测试')
    await fill(inputById('resume-phone'), '13800000001')
    await fill(inputById('resume-email'), 'browser@example.test')
    await click(byId('resume-nav-history'), 'leave unfinished master profile')
    await click(byId('resume-nav-profile'), 'return to unfinished master profile')
    check('master profile survives subpage navigation', await evaluate(`document.querySelector(${JSON.stringify(inputById('resume-name'))})?.value === '陈浏览测试'`))
    await navigate('/student/resume/profile')
    await waitFor(`document.querySelector(${JSON.stringify(inputById('resume-name'))})?.value === '陈浏览测试'`, 'unfinished master profile after refresh')
    check('master profile survives refresh before saving', true)
    await click(byId('resume-add-education'), 'add education')
    await fill(inputById('resume-school-0'), '合成测试大学')
    await click(byId('resume-profile-experience'), 'experience section')
    await click(byId('resume-add-skill'), 'add skill')
    await fill(inputById('resume-skill-0'), 'Java')
    await click(byId('resume-add-experience'), 'add project')
    await fill(inputById('resume-experience-title-0'), '课程选课项目')
    await fill(inputById('resume-experience-actions-0'), '个人使用 Java 实现课程查询接口，编写输入校验测试并提交课程作品。')
    await click(byId('resume-profile-confirm'), 'confirm actual source facts')
    await click(byId('resume-profile-continue'), 'save source and continue')
    await waitFor(`document.querySelector(${JSON.stringify(byId('resume-template-form'))})?.getClientRects().length > 0`, 'template step')
    const master = await api('/api/resumes/master-profile')
    check('actual profile input persisted', master.data.basics.name === '陈浏览测试' && master.data.experiences[0].title === '课程选课项目')
    await waitFor("document.querySelectorAll('.template-card').length === 8", 'eight adapted templates')
    check('eight adapted templates are available', true)
    await click(byId('resume-job-select'), 'target job selector')
    await waitFor("[...document.querySelectorAll('.el-select-dropdown__item')].some(e => e.getClientRects().length && e.textContent.includes('" + job.title.replace(/'/g, "\\'") + "'))", 'target job options')
    const selected = await evaluate(`(() => { const title = ${JSON.stringify(job.title)}; const option = [...document.querySelectorAll('.el-select-dropdown__item')].find(e => e.getClientRects().length && e.textContent.includes(title)); if (!option) return false; option.click(); return true })()`)
    check('target job option can be selected', selected)
    await waitFor(`document.querySelector('.selected-job')?.textContent.includes(${JSON.stringify(job.title)})`, 'selected target job')
    check('selected job is shown in workspace', true, { jobId: job.jobId, title: job.title })
    await click(byId('resume-template-T05'), 'Java template')
    await screenshot('01-template-selected.png')
    await click(byId('resume-generate'), 'generate actual resume')
    await waitCanvas('generated PDF preview contains rendered pixels')
    check('result stage only is visible', await evaluate(`document.querySelector(${JSON.stringify(byId('resume-result'))})?.getClientRects().length > 0 && !document.querySelector(${JSON.stringify(byId('resume-profile-form'))})?.getClientRects().length && !document.querySelector(${JSON.stringify(byId('resume-template-form'))})?.getClientRects().length`))
    check('preview uses canvas rather than external iframe', await evaluate("!document.querySelector('.resume-builder iframe')"))
    await fitAndCheckViewer('desktop whole-page preview fits all four edges')
    await checkZoomAndPan('desktop zoom reaches right and bottom edges')
    await fitAndCheckViewer('desktop fit restores complete page')
    await checkExpandedViewer('desktop expanded preview')
    await client.send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 768, deviceScaleFactor: 1, mobile: false })
    await fitAndCheckViewer('laptop whole-page preview fits all four edges')
    await screenshot('laptop-whole-page.png')
    await client.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
    await screenshot('02-generated-preview-desktop.png')
    await click(byId('resume-open-edit'), 'open separate content editor')
    await waitFor("location.pathname === '/student/resume/edit'", 'content editor route')
    await fill(inputById('resume-entry-title-0-0'), '教育背景核对')
    const editRoute = await evaluate('location.pathname + location.search')
    await click(byId('resume-nav-profile'), 'leave unsaved content editor')
    await navigate(editRoute)
    await waitFor(`document.querySelector(${JSON.stringify(inputById('resume-entry-title-0-0'))})?.value === '教育背景核对'`, 'unsaved content retained on deep-link reload')
    check('unsaved draft survives subpage navigation and refresh', true)
    await click(byId('resume-back-preview'), 'return with unsaved edit')
    await waitFor("location.pathname === '/student/resume'", 'return to preview route with unsaved edit')
    await waitFor(`(() => { const button = document.querySelector(${JSON.stringify(byId('resume-download-pdf'))}); return !button || button.disabled })()`, 'stale download disabled')
    check('old downloads disabled after editing', true)
    await click(byId('resume-update-preview'), 'save and refresh preview')
    await waitCanvas('edited resume PDF preview refreshed')
    const drafts = await api('/api/resumes/drafts')
    const draft = drafts.find(item => item.data.blocks.some(block => block.entries.some(entry => entry.title === '教育背景核对')))
    check('draft editing saved to server', Boolean(draft))
    report.draftId = draft.id
    report.draftRevision = draft.revision
    await screenshot('03-edited-preview.png')
    await download('docx', 'resume-download-word')
    await download('pdf', 'resume-download-pdf')
    if (process.env.E2E_ALL_TEMPLATES === '1') {
      for (const [templateId, title] of [['T01', '通用简洁'], ['T02', '应届生实践'], ['T03', '技术条纹'], ['T04', '前端项目'], ['T05', 'Java 项目'], ['T06', '运营双栏'], ['T07', '浅蓝详版'], ['T08', '橙色详版']]) {
        await click(byId('resume-open-edit'), 'open template editor')
        await click(byId('resume-draft-template'), 'switch template ' + templateId)
        await waitFor(`[...document.querySelectorAll('.el-select-dropdown__item')].some(e => e.getClientRects().length && e.textContent.includes(${JSON.stringify(title)}))`, 'template option')
        await evaluate(`(() => { [...document.querySelectorAll('.el-select-dropdown__item')].find(e => e.getClientRects().length && e.textContent.includes(${JSON.stringify(title)})).click() })()`)
        await click(byId('resume-back-preview'), 'return to final preview')
        await click(byId('resume-update-preview'), 'refresh ' + templateId)
        await waitCanvas(templateId + ' actual PDF canvas renders')
        await waitFor(`document.querySelector('[data-testid="resume-pdf-preview"]')?.dataset.templateId === ${JSON.stringify(templateId)}`, 'current template PDF')
        check(templateId + ' preview matches requested template', true)
        await fitAndCheckViewer(templateId + ' whole page fits preview')
        await screenshot('template-' + templateId + '.png')
      }
    }
    await navigate('/student/resume')
    await waitCanvas('saved draft automatically previews after page reload')
    await click(byId('resume-open-edit'), 'inspect saved content after reload')
    check('saved edit remains after reload', await evaluate(`document.querySelector(${JSON.stringify(inputById('resume-entry-title-0-0'))})?.value === '教育背景核对'`))
    await click(byId('resume-back-preview'), 'return to preview')
    await waitCanvas('preview remains available after editor navigation')
    await client.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
    await waitCanvas('mobile preview is visible and nonblank')
    await waitFor("document.querySelector('.side-nav').getBoundingClientRect().right <= 1", 'mobile navigation transition completed')
    check('mobile has no horizontal page overflow', await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'))
    await screenshot('04-mobile-preview.png')
    await fitAndCheckViewer('mobile whole-page preview fits all four edges')
    await checkZoomAndPan('mobile zoom reaches right and bottom edges')
    await checkExpandedViewer('mobile expanded preview')
    await client.send('Emulation.setDeviceMetricsOverride', { width: 360, height: 640, deviceScaleFactor: 1, mobile: true })
    await fitAndCheckViewer('small mobile whole page fits screen and viewer')
    await screenshot('small-mobile-whole-page.png')
    await client.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 980, deviceScaleFactor: 1, mobile: false })
    await click(byId('resume-open-diagnosis'), 'open separate diagnosis page')
    await waitFor("location.pathname === '/student/resume/diagnosis'", 'diagnosis page route')
    await click(byId('resume-diagnose'), 'diagnose actual draft')
    await waitFor(`document.querySelector(${JSON.stringify(byId('resume-diagnose'))})?.classList.contains('is-loading') === false`, 'diagnosis complete', 180000)
    check('diagnosis retains saved input', (await api(`/api/resumes/drafts/${draft.id}`)).data.blocks.some(block => block.entries.some(entry => entry.title === '教育背景核对')))
    await click(byId('resume-nav-profile'), 'return to source profile')
    await click(byId('resume-profile-basics'), 'basic source fields')
    await click('.import-panel > summary', 'open import')
    const documentNode = await client.send('DOM.getDocument')
    const inputNode = await client.send('DOM.querySelector', { nodeId: documentNode.root.nodeId, selector: byId('resume-import-file') })
    const docxFile = fs.readdirSync(downloadDir).find(name => name.endsWith('.docx'))
    await client.send('DOM.setFileInputFiles', { nodeId: inputNode.nodeId, files: [path.join(downloadDir, docxFile)] })
    await waitFor("document.querySelector('.import-review pre')?.textContent.includes('陈浏览测试')", 'uploaded Word text extracted', 180000)
    check('import candidate stays outside confirmed profile', (await api('/api/resumes/master-profile')).revision === master.revision)
    await screenshot('05-import-confirmation.png')
    await click(byId('resume-import-confirm'), 'confirm candidate merge')
    await click(byId('resume-import-accept'), 'merge candidate into editor')
    check('candidate merge still requires explicit source save', (await api('/api/resumes/master-profile')).revision === master.revision)
    await checkTwoPagePreview(draft)
    check('no browser JavaScript or CSP errors', browserErrors.length === 0, { errors: browserErrors })
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
