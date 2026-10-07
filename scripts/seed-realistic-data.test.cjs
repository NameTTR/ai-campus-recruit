const {test}=require('node:test'),assert=require('node:assert/strict')
const {options,buildJobs,docx,publicJob,Client}=require('./seed-realistic-data.cjs')
const {buildStudents,label}=require('./data/student-scenarios.cjs')
test('catalog has sixty distinct people, three directions and varied material',()=>{
  const rows=buildStudents();assert.equal(rows.length,60);assert.equal(new Set(rows.map(x=>x.username)).size,60)
  for(const role of ['Java','前端','运营'])assert.equal(rows.filter(s=>s.roleKey===role).length,20)
  assert.equal(rows.filter(s=>!s.profileData.experiences.length).length,3)
  assert.ok(rows.some(s=>s.profileData.experiences.some(e=>e.type==='INTERNSHIP')))
  assert.equal(new Set(rows.map(s=>s.resumeText)).size,60)
  for(const s of rows){
    assert.ok(s.resumeText.includes(label));assert.ok(s.profileData.basics.email.endsWith('@example.invalid'))
    for(const e of s.profileData.education)assert.ok(e.startDate<e.endDate)
    for(const e of s.profileData.experiences){assert.ok(e.startDate<=e.endDate);assert.ok(e.endDate<'2026-10-07');assert.ok(e.skills.every(x=>s.studentProfile.skills.includes(x)));assert.ok(e.source.assessment.includes(label))}
    assert.ok(s.studySettings.weeklyHours*60<=s.studySettings.studyDays.length*s.studySettings.dailyMinutesCap)
  }
  assert.deepEqual(buildStudents(),rows)
})
test('synthetic vacancies never look like an actual public hiring offer',()=>{
  const jobs=buildJobs();assert.equal(jobs.length,48);assert.equal(new Set(jobs.map(j=>j.key)).size,48)
  assert.equal(new Set(jobs.map(j=>j.companyUsername)).size,8)
  for(const j of jobs){assert.ok(j.description.includes('非真实招聘'));assert.ok(j.companyName.includes('合成'));assert.ok(j.salaryRange.includes('样例'))}
})
test('public jobs retain source, experience requirements and unknown salary',()=>{
  const j=publicJob({title:'高级Java工程师',company:'腾讯',city:'深圳',recruitmentKind:'SOCIAL',requirements:['五年以上开发经验'],sourceUrl:'https://careers.tencent.com/jobdesc.html?postId=1',queriedAt:'2026-10-07'},0)
  assert.equal(j.title,'高级Java工程师');assert.equal(j.salaryRange,'未公开');assert.ok(j.description.includes('五年以上'));assert.ok(j.description.includes('postId=1'));assert.equal(j.synthetic,false)
})
test('public knowledge catalog only imports actually read official material',()=>{
  const c=require('./data/verified-public-sources.json');assert.ok(c.knowledgeMaterials.length>=30)
  for(const k of c.knowledgeMaterials){assert.equal(k.status,'VERIFIED');assert.equal(k.verification.httpStatus,200);assert.equal(k.verification.readToEnd,true);assert.ok(k.verification.contentBytes>1000);assert.ok(k.content.length>100);assert.match(k.sourceUrl,/^https:\/\//)}
  for(const role of ['Java','前端','运营'])assert.ok(c.knowledgeMaterials.filter(k=>k.category===role).length>=10)
  for(const kind of ['SOCIAL','CAMPUS','INTERNSHIP'])assert.ok(c.recruitmentJobs.some(j=>j.recruitmentKind===kind))
  for(const j of c.recruitmentJobs){assert.equal(j.status,'VERIFIED_PUBLIC');assert.ok(j.requirements.length>60);assert.ok(j.skills.length);assert.ok(j.sourceUrl);assert.ok(j.verification.readToEnd)}
})
test('arguments reject unsafe destinations and invalid scale before API writes',()=>{
  for(const args of [['--students','0'],['--students','61'],['--activities','61'],['--timeout-ms','NaN'],['--base-url','file:///tmp'],['--base-url','http://u:p@localhost'],['--oops'],['--students']])assert.throws(()=>options(args))
  assert.equal(options(['--students','3','--activities','0','--dry-run']).dryRun,true)
})
test('DOCX preserves Chinese and escapes XML',()=>{
  const file=docx('中文简历\nA < B & C');assert.equal(file.readUInt32LE(0),0x04034b50);assert.ok(file.includes(Buffer.from('中文简历')));assert.ok(file.includes(Buffer.from('A &lt; B &amp; C')));assert.ok(file.includes(Buffer.from('word/document.xml')))
})
test('API errors cannot be mistaken for empty lists and do not log token',async()=>{
  const original=global.fetch;global.fetch=async()=>({ok:false,status:403,json:async()=>({code:403,message:'permission denied'})})
  try{await assert.rejects(new Client({baseUrl:'http://localhost',timeoutMs:1000}).api('/api/jobs','secret-value'),e=>!e.message.includes('secret-value')&&e.message.includes('permission denied'))}finally{global.fetch=original}
})
