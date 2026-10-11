#!/usr/bin/env node
'use strict'
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const {validatePublicCatalog} = require('./seed-realistic-data.cjs')

const ROOT = path.resolve(__dirname, '..')
const INPUTS = [
  'scripts/data/verified-public-sources.json',
  'scripts/data/public-jobs-expansion-20261011.json',
  'scripts/data/knowledge-expansion-20261011.json'
]
const OUTPUT = 'scripts/data/public-sources-expansion-20261011.json'
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex')

function unique(rows, fields, kind) {
  for (const field of fields) {
    const seen = new Set()
    for (const row of rows) {
      const value = typeof field === 'function' ? field(row) : row[field]
      if (!value) continue
      if (seen.has(value)) throw Error('Duplicate '+kind+' identity: '+value)
      seen.add(value)
    }
  }
  return rows
}

function build(root = ROOT, inputs = INPUTS) {
  const snapshots = inputs.map(file => {
    const bytes = fs.readFileSync(path.resolve(root, file))
    return {file, sha256: digest(bytes), contentBytes: bytes.length, data: JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/, ''))}
  })
  if (snapshots[2]?.data.reviewStatus !== 'READY') throw Error('Knowledge expansion is still a collection draft; finish source and content checks before merging')
  const recruitmentJobs = unique(snapshots.flatMap(s => s.data.recruitmentJobs || []), ['id', 'sourcePostId', 'sourceUrl'], 'job')
  const knowledgeMaterials = unique(snapshots.flatMap(s => s.data.knowledgeMaterials || []), ['id', 'sourceUrl', row => row.title+'|'+row.sourceUrl], 'knowledge')
  const catalog = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    description: '原公开批次加三倍新增公开资料。招聘事实保留官方原文；知识正文为依据官方资料自编的中文学习讲解，不包含真实学生个人资料。',
    inputSnapshots: snapshots.map(({data, ...manifest}) => ({...manifest, publicJobs: (data.recruitmentJobs || []).length, knowledge: (data.knowledgeMaterials || []).length})),
    recruitmentJobs,
    knowledgeMaterials
  }
  validatePublicCatalog(catalog)
  for (const row of [...recruitmentJobs, ...knowledgeMaterials]) {
    const check = row.verification
    if (check?.httpStatus !== 200 || check?.method !== 'GET' || !Number.isInteger(check?.contentBytes) || check.contentBytes <= 0 || !/^[a-f0-9]{64}$/.test(check?.sha256 || '')) {
      throw Error('Missing full GET source verification: '+row.title)
    }
  }
  const baseline = snapshots[0].data
  catalog.counts = {
    baselineJobs: baseline.recruitmentJobs.length,
    addedJobs: recruitmentJobs.length - baseline.recruitmentJobs.length,
    totalJobs: recruitmentJobs.length,
    baselineKnowledge: baseline.knowledgeMaterials.length,
    addedKnowledge: knowledgeMaterials.length - baseline.knowledgeMaterials.length,
    totalKnowledge: knowledgeMaterials.length,
    jobsByCategory: recruitmentJobs.reduce((out, row) => (out[row.category] = (out[row.category] || 0) + 1, out), {}),
    jobsByRecruitmentKind: recruitmentJobs.reduce((out, row) => (out[row.recruitmentKind] = (out[row.recruitmentKind] || 0) + 1, out), {}),
    knowledgeByCategory: knowledgeMaterials.reduce((out, row) => (out[row.category] = (out[row.category] || 0) + 1, out), {})
  }
  if (catalog.counts.addedJobs !== 126 || catalog.counts.addedKnowledge !== 102) throw Error('Expansion is incomplete: '+JSON.stringify(catalog.counts))
  return catalog
}

if (require.main === module) {
  try {
    const catalog = build()
    if (!process.argv.includes('--check')) fs.writeFileSync(path.resolve(ROOT, OUTPUT), JSON.stringify(catalog, null, 2)+'\n')
    console.log(JSON.stringify({file: OUTPUT, checkOnly: process.argv.includes('--check'), ...catalog.counts}, null, 2))
  } catch (error) {console.error(error.message); process.exitCode = 1}
}
module.exports = {build, unique, INPUTS, OUTPUT}
