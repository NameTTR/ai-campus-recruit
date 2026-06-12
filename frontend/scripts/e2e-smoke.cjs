const { spawn, spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const rootDir = path.resolve(__dirname, '..')
const explicitBaseUrl = Boolean(process.env.E2E_BASE_URL)
const localPort = process.env.E2E_PORT || '5174'
const rawBaseUrl = process.env.E2E_BASE_URL || `http://127.0.0.1:${localPort}`
const baseUrl = rawBaseUrl.replace(/\/+$/, '')
const artifactsDir = process.env.E2E_ARTIFACTS_DIR || path.join(rootDir, '.e2e-artifacts')
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

let devServer

async function main() {
  fs.mkdirSync(artifactsDir, { recursive: true })
  await ensureFrontend()
  const browser = await startBrowser()
  const client = await connect(browser.webSocketDebuggerUrl)
  try {
    await enablePage(client, 1440, 980)
    await loginAsStudent(client)
    await assertText(client, ['Campus Recruit'])
    await screenshot(client, '01-student-resume.png')

    await navigate(client, `${baseUrl}/student/plan`)
    await clickButton(client, '生成改写')
    await clickButton(client, '生成规划')
    await assertText(client, ['AI 求职规划', '优化摘要', '准备度', '生成历史'])
    await screenshot(client, '02-student-plan.png')
    await clickButton(client, '生成顾问建议')
    await assertText(client, ['优先行动', '风险提醒', '学习路径', '面试训练'])
    await screenshot(client, '02b-student-coach-advice.png')

    await navigate(client, `${baseUrl}/student/jobs`)
    await clickButton(client, '匹配')
    await clickButton(client, '投递')
    await assertText(client, ['匹配结果', '推荐岗位'])
    await screenshot(client, '03-student-delivery.png')

    await setSession(client, 'ADMIN', 'A001', 'admin')
    await navigate(client, `${baseUrl}/admin/overview`)
    await assertText(client, ['学校就业看板', '周投递趋势', '转化漏斗', '技能需求 Top', '风险告警'])
    await screenshot(client, '04-admin-overview.png')

    await setSession(client, 'COMPANY', 'C001', 'company')
    await navigate(client, `${baseUrl}/company/screening`)
    await assertText(client, ['AI 异步初筛'])
    await screenshot(client, '05-company-screening.png')

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

async function loginAsStudent(client) {
  await navigate(client, `${baseUrl}/login`)
  await clickButton(client, '登录')
  await waitForExpression(client, "localStorage.getItem('role') === 'STUDENT' && location.pathname.startsWith('/student')")
}

async function setSession(client, role, userId, displayName) {
  await navigate(client, `${baseUrl}/login`)
  await client.send('Runtime.evaluate', {
    expression: `
      localStorage.setItem('token', 'demo-${role.toLowerCase()}-token');
      localStorage.setItem('role', '${role}');
      localStorage.setItem('userId', '${userId}');
      localStorage.setItem('displayName', '${displayName}');
    `
  })
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

async function waitForButton(client, label) {
  for (let index = 0; index < 24; index += 1) {
    const box = await elementBox(client, `(() => {
      const buttons = [...document.querySelectorAll('button')];
      const button = buttons.find((item) => item.innerText.includes(${JSON.stringify(label)}));
      if (!button) return null;
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

async function waitForText(client, expected) {
  for (let index = 0; index < 20; index += 1) {
    if ((await bodyText(client)).includes(expected)) {
      return
    }
    await sleep(500)
  }
  throw new Error(`Timed out waiting for text: ${expected}`)
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
