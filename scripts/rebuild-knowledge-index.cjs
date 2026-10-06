const fs = require('node:fs')
const path = require('node:path')
const base = (process.env.KNOWLEDGE_BASE_URL || 'http://localhost').replace(/\/+$/, '')
const reportPath = path.resolve(__dirname, '../logs/knowledge-index-rebuild.json')

async function api(route, token, method = 'GET', body) {
  const response = await fetch(base + route, { method, headers: {
    ...(token ? { Authorization: 'Bearer ' + token } : {}),
    ...(body ? { 'Content-Type': 'application/json' } : {})
  }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(160000) })
  const result = await response.json()
  if (!response.ok || result.code !== 0) throw new Error('Index operation failed: ' + route)
  return result.data
}

async function main() {
  const password = process.env.MVP_ADMIN_PASSWORD
  if (!password) throw new Error('Set MVP_ADMIN_PASSWORD; credentials are never saved in the report')
  const auth = await api('/api/auth/login', null, 'POST', { username: process.env.MVP_ADMIN_USER || 'admin', password })
  let job = await api('/api/ai/knowledge/index/rebuild', auth.token, 'POST')
  for (let poll = 0; poll < 600 && ['RUNNING', 'PENDING'].includes(job.status); poll++) {
    await new Promise(resolve => setTimeout(resolve, 1000))
    job = await api('/api/ai/knowledge/index/rebuild/' + encodeURIComponent(job.jobId), auth.token)
  }
  fs.mkdirSync(path.dirname(reportPath), { recursive: true })
  fs.writeFileSync(reportPath, JSON.stringify(job, null, 2))
  console.log(JSON.stringify({ status: job.status, documents: job.completedDocuments,
    chunks: job.indexedChunks, model: job.model, dimension: job.dimension, reportPath }))
  if (job.status !== 'SUCCEEDED') throw new Error('Index did not complete; previous index and documents remain available')
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
