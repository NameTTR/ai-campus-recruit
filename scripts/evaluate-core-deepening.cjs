// Fixed synthetic fixtures + actual Java implementation + real authenticated RAG APIs.
// Reports contain fixture/business IDs and metrics, never credentials, headers or provider response bodies.
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { execFile } = require('node:child_process')
const { promisify } = require('node:util')
const run = promisify(execFile)
const root = path.resolve(__dirname, '..')
const evaluation = path.join(root, 'evaluation')
const options = new Map()
for (let i = 2; i < process.argv.length; i++) {
  const name = process.argv[i]
  if (!name.startsWith('--')) throw new Error('Expected a named option')
  options.set(name.slice(2), process.argv[i + 1] && !process.argv[i + 1].startsWith('--') ? process.argv[++i] : true)
}
const mode = options.get('mode') || 'all'
const reportPath = path.resolve(root, options.get('output') || 'evaluation/reports/core-deepening.json')
const read = name => JSON.parse(fs.readFileSync(path.join(evaluation, 'fixtures', name), 'utf8'))
const fixtures = { resume: read('resume-job.json'), interview: read('interview-answers.json'), rag: read('knowledge-queries.json') }
// Small, synthetic edge fixtures exercise the evidence boundary itself. They
// are deliberately separate from the scored datasets and remain pending owner
// review; they must never be treated as production facts or model labels.
const evidenceEdgeCases = [
  { id: 'EDGE-CONTEXT-CURRENT', kind: 'context', value: { evidenceContext: { status: 'CURRENT' } }, expected: 'CURRENT' },
  { id: 'EDGE-CONTEXT-SOURCE', kind: 'context', value: { contextStatus: 'CURRENT', sourceAvailable: false }, expected: 'SOURCE_UNAVAILABLE' },
  { id: 'EDGE-CONTEXT-STALE', kind: 'context', value: { contextStatus: 'CURRENT', stale: true }, expected: 'STALE' },
  { id: 'EDGE-NEGATION', kind: 'resume', text: '未使用 Redis，计划学习 Redis', expected: 'NO_EVIDENCE' },
  { id: 'EDGE-UNKNOWN-CONDITION', kind: 'match', condition: { status: 'UNKNOWN' }, expected: 'UNKNOWN' },
  { id: 'EDGE-CONFLICT', kind: 'knowledge', sources: [{ version: 'v1', claim: '超时为 3 秒' }, { version: 'v2', claim: '超时为 5 秒' }], expected: 'SHOW_BOTH' }
]
function validateEvidenceEdgeCases() {
  requireThat(evidenceEdgeCases.every(item => item.id && item.kind && item.expected), 'Evidence edge fixture incomplete')
  requireThat(evidenceEdgeCases.filter(item => item.kind === 'context').length >= 3, 'Context states need current/stale/source cases')
  requireThat(evidenceEdgeCases.some(item => item.kind === 'resume' && /未使用|计划/.test(item.text)), 'Negation/planned evidence boundary missing')
  requireThat(evidenceEdgeCases.some(item => item.kind === 'match' && item.expected === 'UNKNOWN'), 'Unknown condition boundary missing')
  requireThat(evidenceEdgeCases.some(item => item.kind === 'knowledge' && item.sources.length > 1), 'Conflicting source boundary missing')
  return { total: evidenceEdgeCases.length, reviewStatus: 'OWNER_REVIEW_PENDING', origin: 'SYNTHETIC_ANONYMOUS' }
}
function requireThat(value, reason) { if (!value) throw new Error(reason) }
function validateFixtures() {
  const manifest = JSON.parse(fs.readFileSync(path.join(evaluation, 'manifest.json'), 'utf8'))
  const ids = new Set()
  const counts = {}
  for (const item of manifest.datasets) {
    const raw = fs.readFileSync(path.join(evaluation, 'fixtures', item.file))
    const dataset = JSON.parse(raw.toString('utf8'))
    requireThat(crypto.createHash('sha256').update(raw).digest('hex') === item.sha256, 'Fixture hash mismatch: ' + item.file)
    requireThat(dataset.metadata.origin === 'SYNTHETIC_ANONYMOUS', 'Fixture origin must be explicit')
    requireThat(dataset.metadata.reviewStatus === 'OWNER_REVIEW_PENDING', 'Update review provenance explicitly before changing review status')
    requireThat(dataset.cases.length === item.expectedCount, 'Fixture count mismatch: ' + item.file)
    counts[item.file] = { total: dataset.cases.length, calibration: 0, test: 0, tracks: {} }
    for (const row of dataset.cases) {
      requireThat(!ids.has(row.id), 'Duplicate fixture ID'); ids.add(row.id)
      requireThat(['calibration', 'test'].includes(row.split), 'Unknown fixture split')
      requireThat(['java', 'frontend', 'operations'].includes(row.track), 'Unknown fixture role')
      counts[item.file][row.split]++
      counts[item.file].tracks[row.track] = (counts[item.file].tracks[row.track] || 0) + 1
    }
  }
  const documents = new Map(fixtures.rag.documents.map(doc => [doc.fixtureId, doc]))
  requireThat(documents.size === fixtures.rag.documents.length, 'Duplicate knowledge document ID')
  for (const row of fixtures.resume.cases) {
    const allowed = new Set(row.job.requiredSkills)
    for (const name of [...row.expected.declaredSkills, ...row.expected.supportedSkills]) requireThat(allowed.has(name), 'Unknown expected skill')
  }
  for (const row of fixtures.interview.cases) {
    requireThat(row.answer && row.question && row.referencePoints.length, 'Incomplete fixed interview material')
  }
  for (const row of fixtures.rag.cases) {
    for (const id of [...row.expectedDocumentIds, ...(row.forbiddenDocumentIds || [])]) {
      requireThat(documents.has(id), 'Unknown document reference')
      requireThat(documents.get(id).split === row.split, 'Cross-split knowledge document reference')
    }
    if (row.kind === 'answerable') requireThat(row.expectedDocumentIds.length, 'Answerable case needs a relevant document')
    if (row.kind === 'permission') for (const id of row.forbiddenDocumentIds) {
      requireThat(documents.get(id).roles.join(',') === 'ADMIN', 'Restricted document must be administrator-only')
      requireThat(!row.query.includes(documents.get(id).restrictedFactMarker), 'Query must not already reveal the restricted fact')
    }
  }
  requireThat(positionCheck({ chunkId: 'c', chunkIndex: 1, startOffset: 0, endOffset: 3, snippet: 'abc', roles: ['STUDENT'] }, { content: 'abc', roles: ['STUDENT'] }).length === 0, 'Citation validator positive case')
  requireThat(positionCheck({ chunkId: 'c', chunkIndex: 1, startOffset: 0, endOffset: 4, snippet: 'abc', roles: ['STUDENT'] }, { content: 'abc', roles: ['STUDENT'] }).length > 0, 'Citation validator must reject invalid offsets')
  requireThat(positionCheck({ chunkId: 'c', chunkIndex: 1, startOffset: 0, endOffset: 3, snippet: 'abc', roles: ['ADMIN'] }, { content: 'abc', roles: ['ADMIN'] }).includes('not readable to student'), 'Citation validator must reject restricted evidence')
  counts.evidenceEdgeCases = validateEvidenceEdgeCases()
  return counts
}
function positionCheck(citation, document) {
  const failures = []
  if (!citation || !document) return ['missing citation or original document']
  if (!Number.isInteger(citation.chunkIndex) || citation.chunkIndex < 1) failures.push('invalid chunk index')
  if (!Number.isInteger(citation.startOffset) || !Number.isInteger(citation.endOffset) || citation.startOffset < 0 || citation.endOffset < citation.startOffset || citation.endOffset > document.content.length) failures.push('invalid original offsets')
  else if (document.content.slice(citation.startOffset, citation.endOffset) !== citation.snippet) failures.push('citation differs from original substring')
  if (!(document.roles || []).some(role => ['ALL', 'STUDENT'].includes(role))) failures.push('not readable to student')
  if (!(citation.roles || []).some(role => ['ALL', 'STUDENT'].includes(role))) failures.push('citation permission mismatch')
  return failures
}
function calibrate(rows) {
  // Never receive test rows here. Returned scores are censored by the deployed retrieval thresholds.
  requireThat(rows.every(row => row.split === 'calibration'), 'Calibration must not read test labels')
  const positives = rows.filter(row => row.kind === 'answerable')
  const negatives = rows.filter(row => row.kind === 'unanswerable')
  const scored = positives.filter(row => row.retrievalMode.includes('RERANK'))
  if (!scored.length || !negatives.length) return { status: 'INSUFFICIENT_OBSERVATION', proposedConfiguration: {}, scope: 'calibration only' }
  const candidates = [...new Set([0.10, ...scored.map(row => row.relevantScore / 100), ...negatives.map(row => Math.min(1, (row.highestScore + 1) / 100))])].filter(value => value >= 0.10 && value <= 1).sort((a, b) => a - b)
  const choices = candidates.map(threshold => ({ threshold,
    recall: positives.filter(row => row.hit && row.relevantScore / 100 >= threshold).length / positives.length,
    falsePositiveRate: negatives.filter(row => row.highestScore / 100 >= threshold).length / negatives.length }))
  const selected = choices.filter(choice => choice.recall >= 0.85).sort((a, b) => a.falsePositiveRate - b.falsePositiveRate || a.threshold - b.threshold)[0]
  return { status: selected ? 'SUGGESTION_ONLY_NOT_APPLIED' : 'CALIBRATION_TARGET_NOT_REACHED', scope: 'calibration only', positiveCount: positives.length, negativeCount: negatives.length,
    selected: selected || null, proposedConfiguration: selected ? { AI_KNOWLEDGE_MIN_RERANK_SCORE: Number(selected.threshold.toFixed(2)) } : {},
    limitation: 'Only returned rerank scores are observable; no vector-similarity threshold is calibrated. Do not use test results to select another threshold.' }
}
function loadEnvFile(filename) {
  if (!filename) return
  const content = fs.readFileSync(path.resolve(root, filename), 'utf8')
  for (const line of content.split(/\r?\n/)) {
    const item = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$/)
    if (!item || process.env[item[1]] !== undefined) continue
    let value = item[2]
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1)
    process.env[item[1]] = value
  }
}
async function javaEvaluate(kind) {
  const runtime = path.join(evaluation, 'runtime'); fs.mkdirSync(runtime, { recursive: true })
  let externalClasspath = process.env.CORE_EVAL_JAVA_CLASSPATH
  if (!externalClasspath) {
    const jar = path.join(root, 'backend/ai-service/target/ai-service-0.1.0-SNAPSHOT.jar')
    requireThat(fs.existsSync(jar), 'Package the current backend first, or set CORE_EVAL_JAVA_CLASSPATH')
    const stat = fs.statSync(jar)
    const libraries = path.join(runtime, 'boot-libraries-' + stat.size + '-' + Math.floor(stat.mtimeMs))
    const libraryDir = path.join(libraries, 'BOOT-INF/lib')
    if (!fs.existsSync(libraryDir)) {
      fs.mkdirSync(libraries, { recursive: true })
      await run('jar', ['xf', jar, 'BOOT-INF/lib'], { cwd: libraries, timeout: 120000 })
    }
    externalClasspath = path.join(libraryDir, '*')
  }
  const classpath = ['common', 'match-service', 'ai-service'].map(module => path.join(root, 'backend', module, 'target/classes')).concat(externalClasspath).join(path.delimiter)
  const classes = path.join(runtime, 'classes'); fs.mkdirSync(classes, { recursive: true })
  const source = path.join(evaluation, 'java/CoreFixtureEvaluator.java')
  try { await run('javac', ['-encoding', 'UTF-8', '-cp', classpath, '-d', classes, source], { timeout: 120000 }) }
  catch { throw new Error('Cannot compile fixture runner against the current backend; rebuild/install the backend or provide its dependency classpath') }
  const resultFile = path.join(evaluation, 'reports', kind + '-implementation.json'); fs.mkdirSync(path.dirname(resultFile), { recursive: true })
  const args = ['-Dfile.encoding=UTF-8', '-cp', classes + path.delimiter + classpath, 'CoreFixtureEvaluator', kind,
    path.join(evaluation, 'fixtures', kind === 'resume' ? 'resume-job.json' : 'interview-answers.json'), resultFile]
  if (kind === 'interview' && options.has('interview-ai')) args.push('--ai')
  let invocationFailed = false
  try { await run('java', args, { env: process.env, timeout: options.has('interview-ai') ? 7200000 : 120000, maxBuffer: 5 * 1024 * 1024 }) }
  catch { invocationFailed = true }
  requireThat(fs.existsSync(resultFile), 'The Java evaluation did not produce a report; provider/configuration details were withheld')
  const result = JSON.parse(fs.readFileSync(resultFile, 'utf8')); result.invocationFailed = invocationFailed
  return result
}
async function ragEvaluate() {
  const base = String(options.get('base') || process.env.MVP_BASE_URL || 'http://127.0.0.1').replace(/\/+$/, '')
  const adminPassword = process.env.MVP_ADMIN_PASSWORD || process.env.BOOTSTRAP_ADMIN_PASSWORD
  requireThat(adminPassword, 'Set MVP_ADMIN_PASSWORD or BOOTSTRAP_ADMIN_PASSWORD; credentials are never written to the report')
  async function api(route, session, method = 'GET', body) {
    const response = await fetch(base + route, { method, headers: { ...(session ? { Authorization: 'Bearer ' + session } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(160000) })
    let result
    try { result = await response.json() } catch { throw new Error('API returned an unreadable result for ' + method + ' ' + route.split('?')[0]) }
    requireThat(response.ok && result.code === 0, 'API evaluation failed for ' + method + ' ' + route.split('?')[0] + ' (response details withheld)')
    return result.data
  }
  const admin = await api('/api/auth/login', null, 'POST', { username: process.env.MVP_ADMIN_USER || 'admin', password: adminPassword })
  const runId = 'eval_' + Date.now().toString(36) + '_' + crypto.randomBytes(3).toString('hex')
  const student = await api('/api/auth/register', null, 'POST', { username: runId, password: crypto.randomBytes(24).toString('hex'), displayName: '匿名固定集评估', role: 'STUDENT' })
  const byFixture = new Map(), documents = new Map(), created = []
  const result = { runId, reviewStatus: 'OWNER_REVIEW_PENDING', datasetOrigin: 'SYNTHETIC_ANONYMOUS', rows: [], citationChecks: 0, citationFailures: 0, cleanupFailures: 0, operationalErrors: 0 }
  async function resolveDocument(citation) {
    if (!documents.has(citation.documentId)) {
      const candidates = await api('/api/ai/knowledge/documents?limit=100&keyword=' + encodeURIComponent(citation.title), admin.token)
      for (const doc of candidates) documents.set(doc.documentId, doc)
    }
    return documents.get(citation.documentId)
  }
  async function validateCitation(cite) {
    result.citationChecks++
    const errors = positionCheck(cite, await resolveDocument(cite))
    if (errors.length) result.citationFailures++
    return errors
  }
  try {
    for (const fixture of fixtures.rag.documents) {
      const doc = await api('/api/ai/knowledge/documents', admin.token, 'POST', { title: fixture.title + ' · ' + runId,
        content: fixture.content, category: fixture.category, source: 'synthetic-evaluation:' + runId, tags: fixture.tags, roles: fixture.roles })
      await api('/api/ai/knowledge/publications/' + encodeURIComponent(doc.documentId) + '/publish', admin.token, 'POST')
      byFixture.set(fixture.fixtureId, doc.documentId); documents.set(doc.documentId, doc); created.push(doc.documentId)
    }
    for (const split of ['calibration', 'test']) {
      for (const fixture of fixtures.rag.cases.filter(row => row.split === split)) {
        const row = { id: fixture.id, track: fixture.track, split, kind: fixture.kind, hit: false, relevantScore: 0, highestScore: 0, citationFailures: [], forbiddenHits: 0 }
        try {
          const retrieval = await api('/api/ai/knowledge/search', student.token, 'POST', { query: fixture.query, role: 'ADMIN', limit: 5 })
          row.retrievalMode = retrieval.retrievalMode || 'LEGACY'
          const expected = new Set(fixture.expectedDocumentIds.map(id => byFixture.get(id)))
          const forbidden = new Set((fixture.forbiddenDocumentIds || []).map(id => byFixture.get(id)))
          row.resultDocumentIds = []
          for (const item of retrieval.results) {
            requireThat(item.citation && item.id === item.citation.chunkId, 'Search results need traceable chunk citations')
            row.resultDocumentIds.push(item.citation.documentId)
            row.highestScore = Math.max(row.highestScore, item.score)
            if (expected.has(item.citation.documentId)) { row.hit = true; row.relevantScore = Math.max(row.relevantScore, item.score) }
            if (forbidden.has(item.citation.documentId)) row.forbiddenHits++
            row.citationFailures.push(...await validateCitation(item.citation))
          }
          if (fixture.kind !== 'answerable' || options.has('answer-all')) {
            const answer = await api('/api/ai/knowledge/answer', student.token, 'POST', { query: fixture.query, role: 'ADMIN', limit: 5, useAi: true })
            row.generationMode = answer.generationMode; row.evidenceStatus = answer.evidenceStatus
            for (const citation of answer.citations || []) {
              row.citationFailures.push(...await validateCitation(citation))
              if (forbidden.has(citation.documentId)) row.forbiddenHits++
            }
            for (const claim of answer.claims || []) for (const id of claim.citationIds || []) {
              const cite = (answer.citations || []).find(item => item.chunkId === id)
              requireThat(cite && cite.snippet.includes(claim.supportQuote) && claim.text === claim.supportQuote, 'Generated claim needs original evidence')
            }
            if (fixture.kind === 'unanswerable') row.abstained = ['NO_EVIDENCE', 'INSUFFICIENT'].includes(answer.evidenceStatus) && !(answer.claims || []).length
            if (fixture.kind === 'permission') for (const id of fixture.forbiddenDocumentIds) {
              const marker = fixtures.rag.documents.find(doc => doc.fixtureId === id).restrictedFactMarker
              if (answer.answer.includes(marker)) row.forbiddenHits++
            }
          }
        } catch { row.error = 'API or evidence validation failed; response details withheld'; result.operationalErrors++ }
        result.rows.push(row)
      }
      if (split === 'calibration') result.calibration = calibrate(result.rows.filter(row => row.split === 'calibration' && !row.error))
    }
  } finally {
    result.createdDocumentIds = created
    if (options.has('keep-documents')) result.cleanup = 'KEPT_BY_REQUEST'
    else {
      for (const id of created) try { await api('/api/ai/knowledge/documents/' + encodeURIComponent(id), admin.token, 'DELETE') } catch { result.cleanupFailures++ }
      result.cleanup = result.cleanupFailures ? 'PARTIAL_FAILURE' : 'DELETED_ONLY_DOCUMENTS_CREATED_BY_THIS_RUN'
    }
  }
  const test = result.rows.filter(row => row.split === 'test')
  const answerable = test.filter(row => row.kind === 'answerable')
  const noAnswer = test.filter(row => row.kind === 'unanswerable')
  const permission = test.filter(row => row.kind === 'permission')
  result.test = { answerableCount: answerable.length, hitsAt5: answerable.filter(row => row.hit).length,
    recallAt5: answerable.length ? answerable.filter(row => row.hit).length / answerable.length : 0,
    target: 0.85, unanswerableCount: noAnswer.length, correctAbstentions: noAnswer.filter(row => row.abstained).length,
    permissionCount: permission.length, permissionLeaks: permission.reduce((sum, row) => sum + row.forbiddenHits, 0),
    rerankedAnswerableCount: answerable.filter(row => row.retrievalMode && row.retrievalMode.includes('RERANK')).length }
  result.targetReached = result.test.answerableCount === 30 && result.test.recallAt5 >= 0.85
  result.validationPassed = result.targetReached && result.operationalErrors === 0 && result.citationFailures === 0 && result.test.permissionLeaks === 0 && result.test.correctAbstentions === result.test.unanswerableCount
  result.thresholdApplied = false
  return result
}
async function main() {
  requireThat(['all', 'validate', 'resume', 'interview', 'rag'].includes(mode), 'Unknown evaluation mode')
  const report = { datasetVersion: 'core-deepening-eval-v1', evaluatedAt: new Date().toISOString(), fixtureValidation: validateFixtures(), reviewStatus: 'OWNER_REVIEW_PENDING' }
  if (mode !== 'validate') loadEnvFile(options.get('env-file'))
  if (['all', 'resume'].includes(mode)) report.resume = await javaEvaluate('resume')
  if (['all', 'interview'].includes(mode)) report.interview = await javaEvaluate('interview')
  if (['all', 'rag'].includes(mode)) report.rag = await ragEvaluate()
  fs.mkdirSync(path.dirname(reportPath), { recursive: true }); fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n')
  console.log('Evaluation report: ' + path.relative(root, reportPath))
  console.log(JSON.stringify({ fixtures: report.fixtureValidation, resumeInvariantFailures: report.resume?.invariantFailures, interviewInvariantFailures: report.interview?.invariantFailures,
    interviewProposedLabelDisagreements: report.interview?.proposedLabelDisagreements, ragTest: report.rag?.test, reviewStatus: report.reviewStatus }))
  if (report.resume?.invariantFailures || report.interview?.invariantFailures || (report.rag && !report.rag.validationPassed)) process.exitCode = 1
}
module.exports = { validateFixtures, positionCheck, calibrate }
if (require.main === module) main().catch(() => { console.error('Evaluation could not finish; check build, credentials and service availability. Response details were withheld.'); process.exitCode = 1 })
