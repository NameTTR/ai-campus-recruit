#!/usr/bin/env node
'use strict'
// Check the API-backed import without registering students or requesting generated AI answers.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict')
const {Client,envConfig,hash,publicJob,publicKnowledge,publicDocumentMatches,validatePublicCatalog,loadPublicCatalog}=require('./seed-realistic-data.cjs')
const ROOT=path.resolve(__dirname,'..')
function options(args) {
  const opt={baseUrl:process.env.SEED_BASE_URL||null,timeoutMs:180000,studentUser:process.env.SEED_VERIFY_STUDENT_USER||'seed_student_01',searchPerRole:3,expectIdempotent:false,publicCatalog:null}
  const keys={'--base-url':'baseUrl','--timeout-ms':'timeoutMs','--student-user':'studentUser','--search-per-role':'searchPerRole','--public-catalog':'publicCatalog'}
  for(let i=0;i<args.length;i++) {
    if(args[i]==='--expect-idempotent')opt.expectIdempotent=true
    else if(keys[args[i]]){const key=keys[args[i]],value=args[++i];if(!value||value.startsWith('--'))throw Error('Missing value');opt[key]=['timeoutMs','searchPerRole'].includes(key)?Number(value):value}
    else throw Error('Unknown option: '+args[i])
  }
  if(!Number.isInteger(opt.timeoutMs)||opt.timeoutMs<1000||opt.timeoutMs>300000)throw Error('timeout must be 1000..300000')
  if(!Number.isInteger(opt.searchPerRole)||opt.searchPerRole<1||opt.searchPerRole>1000)throw Error('search-per-role must be 1..1000')
  if(!opt.studentUser||opt.studentUser.length>100)throw Error('Invalid student username')
  if(opt.baseUrl){const url=new URL(opt.baseUrl);if(!['http:','https:'].includes(url.protocol)||url.username||url.password)throw Error('Invalid base URL');opt.baseUrl=opt.baseUrl.replace(/\/+$/,'')}
  return opt
}
function validateLocation(location,document) {
  assert.ok(Number.isInteger(location.startOffset)&&Number.isInteger(location.endOffset),'Missing source offsets')
  assert.ok(location.startOffset>=0&&location.startOffset<=location.endOffset&&location.endOffset<=document.content.length,'Source offsets out of range')
  assert.ok(typeof location.snippet==='string'&&location.snippet.length,'Missing source snippet')
  assert.equal(document.content.slice(location.startOffset,location.endOffset),location.snippet,'Source snippet differs from exact original span')
}
function validateIdempotentReport(report,catalog) {
  assert.equal(report.publicOnly,true,'Last import must use public-only mode')
  assert.equal(report.failures.length,0,'Last import had errors')
  assert.equal(report.counts.jobsReused,catalog.recruitmentJobs.length,'Expected every public job to be reused')
  assert.equal(report.counts.knowledgeReused,catalog.knowledgeMaterials.length,'Expected every public document to be reused')
  for(const name of ['accountsCreated','publicJobsCreated','syntheticJobsCreated','jobsUpdated','knowledgeCreated','knowledgeCorrected','profilesSaved','resumesUploaded','plansCreated','interviewsCreated'])assert.equal(report.counts[name]||0,0,'Import rerun mutated '+name)
  assert.equal(report.studentIds.length,0,'Public-only import must not operate on students')
}
async function run(opt,runtime={}) {
  const root=runtime.root||ROOT,read=(name)=>JSON.parse(fs.readFileSync(path.join(root,name),'utf8'))
  const state=runtime.state||read('logs/seed-realistic-state.json'),access=runtime.access||read('logs/seed-realistic-access.json')
  const source=runtime.catalog?{catalog:runtime.catalog,file:opt.publicCatalog?path.resolve(root,opt.publicCatalog):path.join(ROOT,'scripts/data/verified-public-sources.json')}:loadPublicCatalog(opt.publicCatalog,root)
  const catalog=source.catalog
  validatePublicCatalog(catalog)
  const env=runtime.env||envConfig(),baseUrl=opt.baseUrl||state.baseUrl
  const url=new URL(baseUrl);assert.ok(['http:','https:'].includes(url.protocol)&&!url.username&&!url.password,'Invalid API base URL')
  assert.equal(baseUrl,state.baseUrl,'State belongs to another API server')
  const client=runtime.client||new Client({baseUrl,timeoutMs:opt.timeoutMs}),api=(...args)=>client.api(...args)
  const report={verifiedAt:new Date().toISOString(),sourceCatalog:source.file,catalog:{publicJobs:catalog.recruitmentJobs.length,knowledge:catalog.knowledgeMaterials.length},checks:{jobs:0,documents:0,locations:0,searches:0,searchCitations:0,idempotentImport:false},searchModes:{},failures:[]}
  const secrets=[access.password,access.legacyPassword,env.SEED_ADMIN_PASSWORD,env.BOOTSTRAP_ADMIN_PASSWORD,env.SEED_VERIFY_STUDENT_PASSWORD].filter(Boolean)
  const message=error=>secrets.reduce((text,secret)=>text.split(secret).join('[redacted]'),String(error.message||error)).slice(0,1000)
  const check=async(kind,name,fn)=>{try{await fn()}catch(error){report.failures.push({kind,name,message:message(error)})}}
  const admin=await api('/api/auth/login',null,'POST',{username:env.SEED_ADMIN_USER||'admin',password:env.SEED_ADMIN_PASSWORD||env.BOOTSTRAP_ADMIN_PASSWORD})
  assert.equal(admin.role,'ADMIN');secrets.push(admin.token)
  let student
  try{student=await api('/api/auth/login',null,'POST',{username:opt.studentUser,password:env.SEED_VERIFY_STUDENT_PASSWORD||access.password})}
  catch(error){if(error.status!==401||!access.legacyPassword)throw error;student=await api('/api/auth/login',null,'POST',{username:opt.studentUser,password:access.legacyPassword})}
  assert.equal(student.role,'STUDENT');secrets.push(student.token)
  const jobs=await api('/api/jobs',student.token),jobIds=new Set(),documentIds=new Set(),documents=new Map()
  for(const [index,row]of catalog.recruitmentJobs.entries())await check('job',row.title,async()=>{
    const expected=publicJob(row,index),saved=state.jobs[expected.key];assert.ok(saved,'Public job missing from import state')
    assert.equal(saved.synthetic,false);assert.ok(!jobIds.has(saved.jobId),'One job ID reused for multiple sources');jobIds.add(saved.jobId)
    const sourceLine='来源：'+row.sourceUrl,matches=jobs.filter(job=>String(job.description||'').split(/\r?\n/).includes(sourceLine))
    assert.equal(matches.length,1,'Public source must have exactly one job')
    const job=matches[0];assert.equal(job.jobId,saved.jobId);assert.equal(job.title,expected.title);assert.equal(job.companyName,expected.companyName)
    assert.equal(job.city,expected.city);assert.equal(job.salaryRange,expected.salaryRange);assert.deepEqual(job.requiredSkills,expected.requiredSkills)
    assert.equal(job.description,expected.description,'Stored job must preserve source requirements and provenance')
    if(row.originalRequirements)assert.ok(job.description.includes(row.originalRequirements),'Original requirements were changed')
    if(row.sourceApiUrl)assert.ok(job.description.includes('来源接口：'+row.sourceApiUrl),'Original API URL missing')
    report.checks.jobs++
  })
  for(const row of catalog.knowledgeMaterials)await check('knowledge',row.title,async()=>{
    const saved=state.documents[hash(row.title+'|'+row.sourceUrl).slice(0,16)];assert.ok(saved,'Knowledge source missing from import state')
    assert.ok(!documentIds.has(saved.documentId),'One document ID reused for multiple sources');documentIds.add(saved.documentId)
    const matches=(await api('/api/ai/knowledge/documents?limit=100&keyword='+encodeURIComponent(row.title),admin.token)).filter(doc=>publicDocumentMatches(row,doc))
    assert.equal(matches.length,1,'Public knowledge source must have exactly one publication');assert.equal(matches[0].documentId,saved.documentId)
    const document=await api('/api/ai/knowledge/library/'+saved.documentId,student.token),expected=publicKnowledge(row)
    assert.ok(publicDocumentMatches(row,document),'Student read returned a different source')
    assert.equal(document.content,expected.content,'Stored summary changed or original source metadata missing')
    assert.equal(document.status,'PUBLISHED');assert.equal(document.category,expected.category);assert.deepEqual(document.tags,expected.tags)
    assert.ok(document.roles.includes('STUDENT'),'Student not included in publication permissions')
    assert.ok(document.locations.length,'Publication must have readable source positions')
    for(const location of document.locations){validateLocation(location,document);report.checks.locations++}
    documents.set(document.documentId,document);report.checks.documents++
  })
  const selected=[]
  for(const role of [...new Set(catalog.knowledgeMaterials.map(row=>row.category))])selected.push(...catalog.knowledgeMaterials.filter(row=>row.category===role).slice(-opt.searchPerRole))
  for(const row of selected)await check('search',row.title,async()=>{
    const expected=state.documents[hash(row.title+'|'+row.sourceUrl).slice(0,16)];assert.ok(expected,'Expected publication missing')
    // Search exposes retrieval only and does not create personal query-history entries or generated answers.
    const result=await api('/api/ai/knowledge/search',student.token,'POST',{query:row.title,limit:5})
    assert.ok(result.results.length>0&&result.results.length<=5,'Retrieval returned an invalid result count')
    assert.ok(result.results.some(item=>item.citation?.documentId===expected.documentId),'Exact public topic was not retrieved in top five')
    for(const item of result.results){const citation=item.citation;assert.ok(citation,'Missing retrieval source citation')
      let document=documents.get(citation.documentId)
      if(!document){document=await api('/api/ai/knowledge/library/'+citation.documentId,student.token);documents.set(document.documentId,document)}
      validateLocation(citation,document);assert.equal(citation.title,document.title);assert.equal(citation.source,document.source)
      assert.ok(citation.roles.some(role=>['STUDENT','ALL'].includes(role)),'Unpermitted document entered student retrieval')
      assert.ok(document.locations.some(loc=>loc.chunkId===citation.chunkId&&loc.startOffset===citation.startOffset&&loc.endOffset===citation.endOffset&&loc.snippet===citation.snippet),'Retrieval citation does not match document location')
      report.checks.searchCitations++
    }
    report.searchModes[result.retrievalMode]=(report.searchModes[result.retrievalMode]||0)+1;report.checks.searches++
  })
  if(opt.expectIdempotent)await check('idempotency','public-only rerun',async()=>{
    validateIdempotentReport(runtime.importReport||read('logs/seed-realistic-report.json'),catalog);report.checks.idempotentImport=true
  })
  report.finishedAt=new Date().toISOString()
  if(!runtime.noWrite){const file=path.join(root,'logs/public-expansion-verification.json');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,JSON.stringify(report,null,2)+'\n')}
  console.log(JSON.stringify(report,null,2));if(report.failures.length)process.exitCode=1;return report
}
module.exports={options,validateLocation,validateIdempotentReport,run}
if(require.main===module)run(options(process.argv.slice(2))).catch(error=>{console.error(error.message);process.exitCode=1})
