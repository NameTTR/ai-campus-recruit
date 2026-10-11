'use strict'
const {test} = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const crypto = require('node:crypto')
const {build} = require('./build-public-source-catalog.cjs')

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'public-catalog-'))
  t.after(() => fs.rmSync(root, {recursive: true, force: true}))
  const verification = {method: 'GET', httpStatus: 200, readToEnd: true, contentBytes: 1000, sha256: 'a'.repeat(64)}
  const jobs = Array.from({length: 168}, (_, i) => ({id: 'job-'+i, sourcePostId: String(i), title: '岗位'+i, requirements: '公开岗位原始要求'+i, category: 'Java', recruitmentKind: 'SOCIAL', sourceUrl: 'https://example.org/job/'+i, status: 'VERIFIED_PUBLIC', verification}))
  const knowledge = Array.from({length: 136}, (_, i) => ({id: 'document-'+i, title: '资料'+i, category: 'Java', content: '独立学习资料正文'+i, sourceName: '官方文档', sourceUrl: 'https://example.org/document/'+i, status: 'VERIFIED', verification}))
  const parts = [
    {recruitmentJobs: jobs.slice(0, 42), knowledgeMaterials: knowledge.slice(0, 34)},
    {recruitmentJobs: jobs.slice(42)},
    {reviewStatus: 'READY', knowledgeMaterials: knowledge.slice(34)}
  ]
  const inputs = ['baseline.json', 'jobs.json', 'knowledge.json']
  const write = () => parts.forEach((part, i) => fs.writeFileSync(path.join(root, inputs[i]), JSON.stringify(part)))
  write()
  return {root, inputs, parts, write}
}

test('merge preserves baseline and independent records, with exact source snapshot hashes', t => {
  const {root, inputs, parts} = fixture(t)
  const baseline = fs.readFileSync(path.join(root, inputs[0]))
  const result = build(root, inputs)
  assert.equal(result.counts.addedJobs, 126)
  assert.equal(result.counts.addedKnowledge, 102)
  assert.deepEqual(result.recruitmentJobs.slice(0, 42), parts[0].recruitmentJobs)
  assert.deepEqual(result.knowledgeMaterials.slice(0, 34), parts[0].knowledgeMaterials)
  assert.deepEqual(fs.readFileSync(path.join(root, inputs[0])), baseline)
  assert.equal(result.inputSnapshots[0].sha256, crypto.createHash('sha256').update(baseline).digest('hex'))
})

test('same official post under a different URL cannot inflate job count', t => {
  const f = fixture(t)
  f.parts[1].recruitmentJobs[0].sourcePostId = f.parts[0].recruitmentJobs[0].sourcePostId
  f.write()
  assert.throws(() => build(f.root, f.inputs), /Duplicate job identity/)
})

test('a copied knowledge title and URL cannot inflate the expansion', t => {
  const f = fixture(t)
  Object.assign(f.parts[2].knowledgeMaterials[0], {title: f.parts[0].knowledgeMaterials[0].title, sourceUrl: f.parts[0].knowledgeMaterials[0].sourceUrl})
  f.write()
  assert.throws(() => build(f.root, f.inputs), /Duplicate knowledge identity/)
})

test('partial reads, non-200 sources and missing records fail before any combined output', t => {
  const f = fixture(t)
  f.parts[2].knowledgeMaterials[0].verification = {...f.parts[2].knowledgeMaterials[0].verification, httpStatus: 403}
  f.write()
  assert.throws(() => build(f.root, f.inputs), /Unverified public source response/)
  f.parts[2].knowledgeMaterials[0].verification.httpStatus = 200
  f.parts[2].knowledgeMaterials.pop()
  f.write()
  assert.throws(() => build(f.root, f.inputs), /Expansion is incomplete/)
})

test('a collected draft cannot be merged before source and topic content checks finish', t => {
  const f = fixture(t)
  delete f.parts[2].reviewStatus
  f.write()
  assert.throws(() => build(f.root, f.inputs), /still a collection draft/)
})
