const {test}=require('node:test'),assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),os=require('node:os')
const {options,buildJobs,docx,publicJob,publicKnowledge,publicDocumentMatches,validatePublicCatalog,loadPublicCatalog,Client,hash,run}=require('./seed-realistic-data.cjs')
const {buildStudents,label}=require('./data/student-scenarios.cjs')
const publicVerifier=require('./verify-public-expansion.cjs')
const verification=()=>({method:'GET',httpStatus:200,readToEnd:true,contentBytes:1234,sha256:'a'.repeat(64)})
test('catalog has one hundred eighty distinct people, three directions and varied material',()=>{
  const rows=buildStudents();assert.equal(rows.length,180);assert.equal(new Set(rows.map(x=>x.username)).size,180)
  for(const role of ['Java','前端','运营'])assert.equal(rows.filter(s=>s.roleKey===role).length,60)
  assert.equal(rows.filter(s=>!s.profileData.experiences.length).length,3)
  assert.ok(rows.some(s=>s.profileData.experiences.some(e=>e.type==='INTERNSHIP')))
  assert.equal(new Set(rows.map(s=>s.resumeText)).size,180)
  for(const s of rows){
    assert.ok(s.resumeText.includes(label));assert.ok(s.profileData.basics.email.endsWith('@example.invalid'))
    for(const e of s.profileData.education)assert.ok(e.startDate<e.endDate)
    for(const e of s.profileData.experiences){assert.ok(e.startDate<=e.endDate);assert.ok(e.endDate<'2026-10-07');assert.ok(e.skills.every(x=>s.studentProfile.skills.includes(x)));assert.ok(e.source.assessment.includes(label))}
    assert.ok(s.studySettings.weeklyHours*60<=s.studySettings.studyDays.length*s.studySettings.dailyMinutesCap)
  }
  assert.deepEqual(buildStudents(),rows)
})
test('synthetic vacancies never look like an actual public hiring offer',()=>{
  const jobs=buildJobs();assert.equal(jobs.length,144);assert.equal(new Set(jobs.map(j=>j.key)).size,144)
  assert.equal(new Set(jobs.map(j=>j.companyUsername)).size,24)
  for(const j of jobs){assert.ok(j.description.includes('非真实招聘'));assert.ok(j.companyName.includes('合成'));assert.ok(j.salaryRange.includes('样例'))}
})
test('public jobs retain source, experience requirements and unknown salary',()=>{
  const j=publicJob({title:'高级Java工程师',company:'腾讯',city:'深圳',recruitmentKind:'SOCIAL',requirements:['五年以上开发经验'],sourceUrl:'https://careers.tencent.com/jobdesc.html?postId=1',queriedAt:'2026-10-07'},0)
  assert.equal(j.title,'高级Java工程师');assert.equal(j.salaryRange,'未公开');assert.ok(j.description.includes('五年以上'));assert.ok(j.description.includes('postId=1'));assert.equal(j.synthetic,false)
})
test('public job title uses the API whitespace normalization while requirements stay verbatim',()=>{
  const row={title:' 大数据运维工程师(深圳) ',sourceUrl:'https://example.test/jobs/trim',requirements:'  熟悉 Java\r\n保留原始要求  ',queriedAt:'2026-10-11'}
  const job=publicJob(row,0)
  assert.equal(job.title,'大数据运维工程师(深圳)')
  assert.ok(job.description.includes(row.requirements))
  assert.equal(row.title,' 大数据运维工程师(深圳) ')
})
test('public jobs preserve source API without mistaking catalog salary for a verified range',()=>{
  const row={title:'Java 实习',sourceUrl:'https://example.test/jobs/1',sourceApiUrl:'https://example.test/api/jobs/1',salary:'200元/天',queriedAt:'2026-10-11'}
  const job=publicJob(row,0);assert.equal(job.salaryRange,'未公开');assert.ok(job.description.includes('来源接口：'+row.sourceApiUrl))
  assert.equal(publicJob({...row,salaryRange:'200元/天（官方公开）'},0).salaryRange,'200元/天（官方公开）')
})
test('public-only dry run cannot prepare synthetic students, jobs or activities',async()=>{
  const opt=options(['--public-only','--dry-run']);assert.equal(opt.publicOnly,true)
  const summary=await run(opt,{catalog:{knowledgeMaterials:[{title:'资料',content:'已读取资料',sourceName:'官方',sourceUrl:'https://example.test/docs/1',status:'VERIFIED',verification:verification()}],recruitmentJobs:[{title:'岗位',sourceUrl:'https://example.test/jobs/1',requirements:'官方岗位要求',status:'VERIFIED_PUBLIC',verification:verification()}]}})
  assert.equal(summary.students,0);assert.equal(summary.syntheticJobs,0);assert.equal(summary.activityStudents,0)
  assert.equal(summary.publicJobs,1);assert.equal(summary.knowledge,1)
  assert.equal(options(['--dry-run']).publicOnly,false);assert.equal(options(['--public-only','--verify-only']).verifyOnly,true)
})
test('custom public source catalog resolves relative and absolute paths and rejects malformed structure',async()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'campus-catalog-'))
  try{
    const catalog={recruitmentJobs:[],knowledgeMaterials:[]},file=path.join(root,'expanded public.json')
    fs.writeFileSync(file,'\uFEFF'+JSON.stringify(catalog))
    assert.deepEqual(loadPublicCatalog('expanded public.json',root),{catalog,file});assert.deepEqual(loadPublicCatalog(file,root),{catalog,file})
    assert.equal(options(['--public-catalog',file]).publicCatalog,file);assert.equal(publicVerifier.options(['--public-catalog',file]).publicCatalog,file)
    const summary=await run(options(['--public-only','--dry-run','--public-catalog','expanded public.json']),{root});assert.equal(summary.sourceCatalog,file)
    const invalid=path.join(root,'invalid.json')
    for(const value of [null,[],{}, {recruitmentJobs:{},knowledgeMaterials:[]},{recruitmentJobs:[],knowledgeMaterials:[null]},{recruitmentJobs:[[]],knowledgeMaterials:[]}]){
      fs.writeFileSync(invalid,JSON.stringify(value));assert.throws(()=>loadPublicCatalog(invalid,root),/Public catalog/)
    }
    fs.writeFileSync(invalid,'{bad');assert.throws(()=>loadPublicCatalog(invalid,root),/invalid JSON/)
    assert.throws(()=>loadPublicCatalog('missing.json',root),/ENOENT/)
    assert.throws(()=>options(['--public-catalog']),/Missing value/);assert.throws(()=>publicVerifier.options(['--public-catalog']),/Missing value/)
  }finally{fs.rmSync(root,{recursive:true,force:true})}
})
test('public-only rejects unverified and duplicate source identities before API writes',()=>{
  const row={title:'岗位',sourceUrl:'https://example.test/jobs/1',requirements:'官方岗位要求',status:'VERIFIED_PUBLIC',verification:verification()}
  validatePublicCatalog({recruitmentJobs:[row],knowledgeMaterials:[]})
  assert.throws(()=>validatePublicCatalog({recruitmentJobs:[{...row,status:'UNVERIFIED'}],knowledgeMaterials:[]}),/Unverified/)
  assert.throws(()=>validatePublicCatalog({recruitmentJobs:[row,row],knowledgeMaterials:[]}),/Duplicate/)
  const material={title:'资料',content:'内容',sourceName:'官方',sourceUrl:'https://example.test/docs/1',status:'VERIFIED',verification:verification()}
  assert.throws(()=>validatePublicCatalog({recruitmentJobs:[],knowledgeMaterials:[material,material]}),/Duplicate/)
})
test('public source verification rejects failed or incomplete HTTP responses even with a verified label',()=>{
  const job={title:'岗位',sourceUrl:'https://example.test/jobs/1',requirements:'官方要求',status:'VERIFIED_PUBLIC',verification:verification()}
  const material={title:'资料',content:'已读取正文',sourceName:'官方',sourceUrl:'https://example.test/docs/1',status:'VERIFIED',verification:verification()}
  const invalid=[undefined,null,[],{readToEnd:true},...[
    {method:'POST'},{method:'get'},{httpStatus:403},{httpStatus:'200'},{readToEnd:false},
    {contentBytes:0},{contentBytes:-1},{contentBytes:1.5},{contentBytes:'1234'},{contentBytes:NaN},{contentBytes:Number.MAX_SAFE_INTEGER+1},
    {sha256:'a'.repeat(63)},{sha256:'g'.repeat(64)},{sha256:123},{sha256:' '+ 'a'.repeat(64)},
    {finalUrl:'http://example.test/docs/1'}
  ].map(change=>({...verification(),...change}))]
  for(const value of invalid){
    assert.throws(()=>validatePublicCatalog({recruitmentJobs:[{...job,verification:value}],knowledgeMaterials:[]}),/Unverified|Invalid/)
    assert.throws(()=>validatePublicCatalog({recruitmentJobs:[],knowledgeMaterials:[{...material,verification:value}]}),/Unverified|Invalid/)
  }
  validatePublicCatalog({recruitmentJobs:[{...job,verification:{...verification(),sha256:'A'.repeat(64)}}],knowledgeMaterials:[material]})
})
test('public source identity and body checks reject URL tricks, malformed strings and relabelled duplicates',()=>{
  const job={id:'public-one',sourcePostId:'12345',title:'岗位',sourceUrl:'https://example.test/jobs/1',requirements:'官方要求',status:'VERIFIED_PUBLIC',verification:verification()}
  const material={title:'资料',content:'完全相同的正文',sourceName:'官方',sourceUrl:'https://example.test/docs/1',status:'VERIFIED',verification:verification()}
  for(const sourceUrl of ['https://','https://:443','http://example.test/docs','ftp://example.test/docs','https://u:p@example.test/docs','https://example.test/docs\n',' https://example.test/docs','https://exa mple.test/docs']){
    assert.throws(()=>validatePublicCatalog({recruitmentJobs:[{...job,sourceUrl}],knowledgeMaterials:[]}),/Invalid/)
    assert.throws(()=>validatePublicCatalog({recruitmentJobs:[],knowledgeMaterials:[{...material,sourceUrl}]}),/Invalid/)
  }
  for(const title of [null,undefined,42,{},[], '', '   ', 'x'.repeat(256),'title\0']){
    assert.throws(()=>validatePublicCatalog({recruitmentJobs:[{...job,title}],knowledgeMaterials:[]}),/Invalid/)
    assert.throws(()=>validatePublicCatalog({recruitmentJobs:[],knowledgeMaterials:[{...material,title}]}),/Invalid/)
  }
  for(const change of [{requirements:null},{requirements:[]},{requirements:[1]},{company:12},{skills:'Java'},{skills:['']},{id:123},{sourcePostId:''},{sourceApiUrl:'https://'},{eligibilitySourceUrl:'file:///tmp'}])assert.throws(()=>validatePublicCatalog({recruitmentJobs:[{...job,...change}],knowledgeMaterials:[]}),/Invalid/)
  for(const change of [{content:42},{content:' '},{sourceName:{}},{category:12},{fetchedAt:[]},{skills:[null]}])assert.throws(()=>validatePublicCatalog({recruitmentJobs:[],knowledgeMaterials:[{...material,...change}]}),/Invalid/)
  assert.throws(()=>validatePublicCatalog({recruitmentJobs:[job,{...job,id:'different',sourceUrl:'https://example.test/jobs/changed'}],knowledgeMaterials:[]}),/Duplicate public job sourcePostId/)
  assert.throws(()=>validatePublicCatalog({recruitmentJobs:[job,{...job,sourcePostId:'different',sourceUrl:'https://example.test/jobs/changed'}],knowledgeMaterials:[]}),/Duplicate public job id/)
  assert.throws(()=>validatePublicCatalog({recruitmentJobs:[],knowledgeMaterials:[material,{...material,title:'改标题凑数',sourceUrl:'https://example.test/docs/different'}]}),/Duplicate public knowledge content/)
  for(const catalog of [null,[],{}, {recruitmentJobs:[],knowledgeMaterials:{}},{recruitmentJobs:[null],knowledgeMaterials:[]}])assert.throws(()=>validatePublicCatalog(catalog),/Public catalog/)
})
test('tagged documents are reusable only for the exact title and source',()=>{
  const row={title:'集合',sourceName:'官方',sourceUrl:'https://example.test/docs/1',content:'真实资料',category:'Java',skills:[],fetchedAt:'2026-10-11'}
  const doc={documentId:'D1',...publicKnowledge(row)}
  assert.ok(publicDocumentMatches(row,doc))
  for(const change of [{title:'其他专题'},{source:'其他来源'},{content:'没有来源地址'},{tags:['official-source']}])assert.equal(publicDocumentMatches(row,{...doc,...change}),false)
})
test('public-only imports all new sources, safely recovers beyond list limit and reruns without writes',async()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'campus-public-import-')),calls=[]
  const material=(title,id)=>({title,sourceName:'官方',sourceUrl:'https://example.test/docs/'+id,content:'关于 '+title+' 的完整公开资料。',category:'Java',skills:['Java'],fetchedAt:'2026-10-11',status:'VERIFIED',verification:verification()})
  const recovered=material('旧资料修订','old'),added=material('新增资料','new')
  const catalog={knowledgeMaterials:[recovered,added],recruitmentJobs:[{title:'公开 Java 岗位',sourceUrl:'https://example.test/jobs/1',sourceApiUrl:'https://example.test/api/jobs/1',salary:'999元/天',skills:['Java'],description:'官方工作职责',requirements:'官方任职要求',queriedAt:'2026-10-11',status:'VERIFIED_PUBLIC',verification:verification()}]}
  const tagged=Array.from({length:100},(_,i)=>({documentId:'untouched-'+i,...publicKnowledge(material('现有专题 '+i,'keep-'+i)),version:1,status:'PUBLISHED'}))
  const existing={documentId:'recover',...publicKnowledge(recovered),content:'旧内容\n'+recovered.sourceUrl,version:1,status:'PUBLISHED'}
  const docs=[...tagged,existing],jobs=[],before=JSON.stringify(tagged),initial={baseUrl:'http://localhost:18080',students:{existing:{resumeId:'R-existing',planId:'P-existing'}},jobs:{'synthetic-keep':{jobId:'J-keep',synthetic:true}},documents:{[hash(added.title+'|'+added.sourceUrl).slice(0,16)]:{documentId:tagged[0].documentId}}}
  fs.mkdirSync(path.join(root,'logs'));fs.writeFileSync(path.join(root,'logs/seed-realistic-state.json'),JSON.stringify(initial))
  const client={async api(route,token,method='GET',body){
    calls.push({route,method,body});const url=new URL(route,'http://localhost')
    if(route==='/api/auth/login')return {token:'never-print-token',userId:body.username==='admin'?'ADMIN':body.username.startsWith('seed_student_')?'S-existing':'C-public',role:body.username==='admin'?'ADMIN':body.username.startsWith('seed_student_')?'STUDENT':'COMPANY'}
    if(route==='/api/jobs'&&method==='GET')return jobs
    if(route==='/api/jobs'&&method==='POST'){const job={jobId:'J-'+jobs.length,...body};jobs.push(job);return job}
    if(route.startsWith('/api/jobs/')&&method==='PUT'){const job=jobs.find(j=>j.jobId===route.split('/').at(-1));Object.assign(job,body);return job}
    if(url.pathname==='/api/ai/knowledge/documents'){const keyword=url.searchParams.get('keyword');return docs.filter(d=>(d.title+d.content+d.tags.join(' ')).includes(keyword)).slice(0,100)}
    if(route.startsWith('/api/ai/knowledge/library/')){const d=docs.find(d=>d.documentId===route.split('/').at(-1));return {...d,locations:[{chunkId:d.documentId+'-chunk',startOffset:0,endOffset:d.content.length,snippet:d.content}]}}
    if(route==='/api/ai/knowledge/search'){
      const d=docs.find(d=>d.title===body.query);assert.ok(d);return {retrievalMode:'KEYWORD_ONLY',results:[{citation:{documentId:d.documentId,chunkId:d.documentId+'-chunk',title:d.title,source:d.source,roles:d.roles,startOffset:0,endOffset:d.content.length,snippet:d.content}}]}
    }
    if(route==='/api/ai/knowledge/publications'&&method==='POST'){const doc={documentId:'D-new-'+docs.length,...body,version:1,status:'DRAFT'};docs.push(doc);return doc}
    if(route.startsWith('/api/ai/knowledge/publications/')){const id=route.split('/')[5],doc=docs.find(d=>d.documentId===id);assert.ok(doc)
      if(route.endsWith('/publish')){doc.status='PUBLISHED';return doc}
      assert.equal(method,'PUT');assert.equal(body.expectedRevision,doc.version);Object.assign(doc,body,{version:doc.version+1,status:'DRAFT'});return doc
    }
    assert.fail('Public import called a forbidden API: '+route)
  }}
  try{
    const opt=options(['--public-only']),runtime={root,catalog,client,env:{SEED_ADMIN_PASSWORD:'never-print-password'}}
    const first=await run(opt,runtime);assert.equal(first.failures.length,0);assert.equal(first.counts.publicJobsCreated,1);assert.equal(first.counts.knowledgeCreated,1);assert.equal(first.counts.knowledgeCorrected,1)
    assert.equal(jobs[0].salaryRange,'未公开');assert.equal(JSON.stringify(tagged),before)
    assert.ok(calls.some(c=>c.route.includes('keyword='+encodeURIComponent(recovered.title))))
    const state=JSON.parse(fs.readFileSync(path.join(root,'logs/seed-realistic-state.json'),'utf8'));assert.deepEqual(state.students,initial.students);assert.deepEqual(state.jobs['synthetic-keep'],initial.jobs['synthetic-keep']);assert.equal(state.documents[hash(recovered.title+'|'+recovered.sourceUrl).slice(0,16)].documentId,existing.documentId)
    calls.length=0;const second=await run(opt,runtime);assert.equal(second.failures.length,0);assert.equal(second.counts.jobsReused,1);assert.equal(second.counts.knowledgeReused,2)
    assert.ok(calls.every(c=>c.method==='GET'||c.route==='/api/auth/login'));assert.equal(docs.length,102);assert.equal(jobs.length,1)
    calls.length=0;const verification=await run(options(['--public-only','--verify-only']),runtime);assert.equal(verification.failures.length,0);assert.equal(verification.counts.publicJobsVerified,1);assert.equal(verification.counts.publicKnowledgeVerified,2)
    assert.ok(calls.every(c=>c.method==='GET'||c.route==='/api/auth/login'))
    const report=fs.readFileSync(path.join(root,'logs/seed-realistic-report.json'),'utf8');assert.ok(!report.includes('never-print-password'));assert.ok(!report.includes('never-print-token'))
    calls.length=0
    const publicCheck=await publicVerifier.run(publicVerifier.options(['--expect-idempotent']),{...runtime,importReport:second,noWrite:true})
    assert.equal(publicCheck.failures.length,0);assert.equal(publicCheck.checks.jobs,1);assert.equal(publicCheck.checks.documents,2);assert.equal(publicCheck.checks.searches,2);assert.equal(publicCheck.checks.idempotentImport,true)
    assert.ok(calls.every(c=>c.method==='GET'||['/api/auth/login','/api/ai/knowledge/search'].includes(c.route)))
  }finally{fs.rmSync(root,{recursive:true,force:true})}
})
test('public verification validates exact citation offsets and idempotency evidence',()=>{
  const document={content:'中文原文'};publicVerifier.validateLocation({startOffset:0,endOffset:4,snippet:'中文原文'},document)
  assert.throws(()=>publicVerifier.validateLocation({startOffset:0,endOffset:2,snippet:'错误'},document),/differs/)
  assert.throws(()=>publicVerifier.validateLocation({startOffset:-1,endOffset:4,snippet:'中文原文'},document),/range/)
  for(const args of [['--search-per-role','0'],['--timeout-ms','NaN'],['--base-url','http://u:p@localhost'],['--unknown']])assert.throws(()=>publicVerifier.options(args))
  assert.throws(()=>publicVerifier.validateIdempotentReport({publicOnly:true,failures:[],counts:{jobsReused:1,knowledgeReused:1,jobsUpdated:1},studentIds:[]},{recruitmentJobs:[{}],knowledgeMaterials:[{}]}),/mutated/)
})
test('public knowledge catalog only imports actually read official material',()=>{
  const c=require('./data/verified-public-sources.json');validatePublicCatalog(c);assert.ok(c.knowledgeMaterials.length>=30)
  for(const k of c.knowledgeMaterials){assert.equal(k.status,'VERIFIED');assert.equal(k.verification.httpStatus,200);assert.equal(k.verification.readToEnd,true);assert.ok(k.verification.contentBytes>1000);assert.ok(k.content.length>100);assert.match(k.sourceUrl,/^https:\/\//)}
  for(const role of ['Java','前端','运营'])assert.ok(c.knowledgeMaterials.filter(k=>k.category===role).length>=10)
  for(const kind of ['SOCIAL','CAMPUS','INTERNSHIP'])assert.ok(c.recruitmentJobs.some(j=>j.recruitmentKind===kind))
  for(const j of c.recruitmentJobs){assert.equal(j.status,'VERIFIED_PUBLIC');assert.ok(j.requirements.length>60);assert.ok(j.skills.length);assert.ok(j.sourceUrl);assert.ok(j.verification.readToEnd)}
})
test('arguments reject unsafe destinations and invalid scale before API writes',()=>{
  for(const args of [['--students','0'],['--students','181'],['--activities','181'],['--timeout-ms','NaN'],['--base-url','file:///tmp'],['--base-url','http://u:p@localhost'],['--oops'],['--students']])assert.throws(()=>options(args))
  assert.equal(options(['--students','3','--activities','0','--dry-run']).dryRun,true)
})
test('DOCX preserves Chinese and escapes XML',()=>{
  const file=docx('中文简历\nA < B & C');assert.equal(file.readUInt32LE(0),0x04034b50);assert.ok(file.includes(Buffer.from('中文简历')));assert.ok(file.includes(Buffer.from('A &lt; B &amp; C')));assert.ok(file.includes(Buffer.from('word/document.xml')))
})
test('API errors cannot be mistaken for empty lists and do not log token',async()=>{
  const original=global.fetch;global.fetch=async()=>({ok:false,status:403,json:async()=>({code:403,message:'permission denied'})})
  try{await assert.rejects(new Client({baseUrl:'http://localhost',timeoutMs:1000}).api('/api/jobs','secret-value'),e=>!e.message.includes('secret-value')&&e.message.includes('permission denied'))}finally{global.fetch=original}
})
test('API error messages redact reflected credentials from upstream errors',async()=>{
  const original=global.fetch;global.fetch=async()=>({ok:false,status:401,json:async()=>({code:401,message:'secret-token and secret-password were rejected'})})
  try{await assert.rejects(new Client({baseUrl:'http://localhost',timeoutMs:1000}).api('/api/auth/login','secret-token','POST',{password:'secret-password'}),e=>!e.message.includes('secret-token')&&!e.message.includes('secret-password')&&e.message.includes('[redacted]'))}finally{global.fetch=original}
})
