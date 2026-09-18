const { spawn, spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const rootDir = path.resolve(__dirname, '..')
const explicitBaseUrl = Boolean(process.env.E2E_BASE_URL)
const localPort = process.env.E2E_PORT || '5174'
const rawBaseUrl = process.env.E2E_BASE_URL || `http://127.0.0.1:${localPort}`
const baseUrl = rawBaseUrl.replace(/\/+$/, '')
const artifactsDir = process.env.E2E_ARTIFACTS_DIR || path.join(rootDir, '.e2e-artifacts')
const coreFixturePath = path.resolve(rootDir, '../logs/core-mvp-verification.json')
const coreFixturePassword = process.env.MVP_SMOKE_PASSWORD || 'Verification123!'
const persistedTaskFeedback = 'MVP-已完成并保存'
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

let devServer

async function main() {
  fs.mkdirSync(artifactsDir, { recursive: true })
  const coreFixture = readCoreFixture()
  await ensureFrontend()
  const browser = await startBrowser()
  const client = await connect(browser.webSocketDebuggerUrl)
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
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '01-student-resume.png')
    if (coreFixture || await elementBox(client, "Boolean(document.querySelector('.resume-diagnosis'))")) {
      await assertDetailsToggle(client, '.resume-diagnosis')
    }

    await verifyGlobalSearch(client)
    await navigate(client, `${baseUrl}/student/resume`)

    await navigate(client, `${baseUrl}/student/plan`)
    await assertText(client, ['学习路径', '学习计划'])
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '02-student-plan.png')

    await navigate(client, `${baseUrl}/student/jobs`)
    await assertText(client, ['岗位匹配', '岗位与匹配'])
    await assertNoHorizontalOverflow(client)
    await fillInput(client, '.job-search input', '__e2e_no_match__')
    await assertText(client, ['没有匹配的岗位'])
    await fillInput(client, '.job-search input', '')
    await assertText(client, ['岗位与匹配'])
    await screenshot(client, '03-student-jobs.png')

    await navigate(client, `${baseUrl}/student/interview`)
    await assertText(client, ['模拟面试', '模拟面试会话'])
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '04-student-interview.png')

    await navigate(client, `${baseUrl}/student/knowledge`)
    await assertText(client, ['知识库问答', 'RAG KNOWLEDGE BASE'])
    await assertNoHorizontalOverflow(client)
    await screenshot(client, '05-student-knowledge.png')

    await navigate(client, `${baseUrl}/student/history`)
    await waitForExpression(client, "location.pathname === '/student/interview' && new URLSearchParams(location.search).get('tab') === 'history'")
    await assertText(client, ['模拟面试', '面试记录'])
    await navigate(client, `${baseUrl}/student/deliveries`)
    await waitForExpression(client, "location.pathname === '/student/resume'")

    await setViewport(client, 1024, 900)
    await navigate(client, `${baseUrl}/student/plan`)
    await assertText(client, ['学习路径', '学习计划'])
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
      ['jobs', ['岗位匹配', '岗位与匹配']],
      ['plan', ['学习路径', '学习计划']],
      ['interview', ['模拟面试', '模拟面试会话']],
      ['knowledge', ['知识库问答', 'RAG KNOWLEDGE BASE']]
    ]) {
      await navigate(client, `${baseUrl}/student/${name}`)
      await assertText(client, expected)
      await assertNoHorizontalOverflow(client)
      await screenshot(client, `14-student-${name}-mobile.png`)
    }

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

    console.log(`E2E smoke passed. Screenshots: ${artifactsDir}`)
  } catch (error) {
    try {
      await screenshot(client, 'failure.png')
      fs.writeFileSync(path.join(artifactsDir, 'failure-text.txt'), await bodyText(client), 'utf8')
    } catch {
      // Preserve the original failure.
    }
    throw error
  } finally {
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
  for (const field of ['studentUsername', 'planId', 'sessionId']) {
    if (typeof fixture[field] !== 'string' || !fixture[field].trim()) {
      throw new Error(`Core MVP fixture is missing ${field}: ${coreFixturePath}`)
    }
  }
  return fixture
}

async function verifyCoreFixture(client, fixture) {
  console.log(`Core MVP fixture detected for ${fixture.studentUsername}; checking persisted student data.`)
  await loginAs(client, fixture.studentUsername, 'STUDENT', '/student/resume', coreFixturePassword)
  const plan = await fetchFixtureData(client, `/api/ai/learning/plans/${encodeURIComponent(fixture.planId)}`)
  const session = await fetchFixtureData(client, `/api/ai/interview/sessions/${encodeURIComponent(fixture.sessionId)}`)
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

  await navigate(client, `${baseUrl}/student/plan`)
  await assertText(client, ['学习路径', '学习计划', '任务进度', plan.targetRole, `V${plan.version}`, '当前可编辑版本'])
  await assertSelectDisplay(client, `V${plan.version}`)
  await assertPersistedTask(client, persistedTaskFeedback)
  await screenshot(client, '00-core-fixture-learning-plan.png')

  await navigate(client, `${baseUrl}/student/history`)
  await waitForExpression(client, "location.pathname === '/student/interview' && new URLSearchParams(location.search).get('tab') === 'history'")
  await assertText(client, ['模拟面试', '面试记录', 'COMPLETED'])
  await navigate(client, `${baseUrl}/student/interview`)
  await assertText(client, [
    '模拟面试',
    '面试报告',
    String(session.report.overallScore),
    session.report.recommendations[0]
  ])
  await screenshot(client, '00-core-fixture-interview-report.png')
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
    '--headless=new',
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
  let sequence = 0
  ws.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (!message.id || !pending.has(message.id)) {
      return
    }
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
    close() {
      ws.close()
    }
  }
}

async function enablePage(client, width, height) {
  await client.send('Page.enable')
  await client.send('Runtime.enable')
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
    const input = document.querySelector(${JSON.stringify(selector)});
    if (!input) return false;
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
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

async function assertNoInternalHorizontalOverflow(client, selector) {
  const overflowing = await elementBox(client, `(() => [...document.querySelectorAll(${JSON.stringify(selector)})]
    .map((element) => ({ clientWidth: element.clientWidth, scrollWidth: element.scrollWidth }))
    .find((dimensions) => dimensions.scrollWidth > dimensions.clientWidth + 1) || null)()`)
  if (overflowing) {
    throw new Error(`Internal horizontal overflow in ${selector}: client=${overflowing.clientWidth}, content=${overflowing.scrollWidth}`)
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
