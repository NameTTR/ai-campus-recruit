'use strict'
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict')
const {Client,envConfig,hash}=require('./seed-realistic-data.cjs')
const ROOT=path.resolve(__dirname,'..'),state=JSON.parse(fs.readFileSync(path.join(ROOT,'logs/seed-realistic-state.json'),'utf8'))
const access=JSON.parse(fs.readFileSync(path.join(ROOT,'logs/seed-realistic-access.json'),'utf8')),client=new Client({baseUrl:state.baseUrl,timeoutMs:180000})
const api=(...args)=>client.api(...args)
async function login(username){
  try{return await api('/api/auth/login',null,'POST',{username,password:access.password})}
  catch(e){if(e.status!==401||!access.legacyPassword)throw e;return api('/api/auth/login',null,'POST',{username,password:access.legacyPassword})}
}
async function main(){
  if(process.argv[2]==='--browser'){
    const username=process.argv[3];assert.match(username,/^seed_student_\d{2}$/);assert.ok(state.students[username])
    const auth=await login(username),localStorage=['token','userId','role','displayName'].map(name=>({name,value:auth[name]}))
    const file=path.join(ROOT,'logs/seed-browser-auth.json');fs.writeFileSync(file,JSON.stringify({cookies:[],origins:[{origin:'http://localhost',localStorage}]}));console.log('Browser authentication prepared for '+username);return
  }
  const report={verifiedAt:new Date().toISOString(),students:0,resumes:0,drafts:0,matches:0,plans:0,tasks:0,evidence:0,reviews:0,deliveries:0,interviews:0,answers:0,finalReports:0,partialReports:0,documents:0,jobs:0,bookmarks:0,histories:0,analysisModes:{modelPlans:0,rulePlans:0,modelInterviews:0,ruleInterviews:0},failures:[],checks:[]}
  const first=await login('seed_student_01'),second=await login('seed_student_02')
  for(const [username,row]of Object.entries(state.students))try{
    const auth=await login(username),profile=await api('/api/resumes/master-profile',auth.token),resume=await api('/api/resumes/'+row.resumeId,auth.token)
    assert.equal(profile.userId,auth.userId);assert.equal(resume.studentId,auth.userId);assert.ok(resume.parsedTextLength>80);assert.ok(profile.data.education.length);report.resumes++
    assert.equal(new Set(row.matchIds).size,3);report.matches+=row.matchIds.length+(row.publicMatchId?1:0)
    if(row.draftId){const draft=await api('/api/resumes/drafts/'+row.draftId,auth.token);assert.equal(draft.userId,auth.userId);assert.equal(draft.confirmed,true);report.drafts++}
    if(row.deliveryId){const records=await api('/api/deliveries/my',auth.token);assert.ok(records.some(d=>d.deliveryId===row.deliveryId&&d.resumeId===row.resumeId&&d.studentId===auth.userId));report.deliveries++}
    const items=await api('/api/ai/knowledge/me/items',auth.token);assert.ok(items.some(x=>x.itemId===row.knowledgeItemId));report.bookmarks++
    if(row.historyCreated){const history=await api('/api/ai/knowledge/me/history',auth.token);assert.ok(history.length);report.histories+=history.length}
    if(row.planId){const plan=await api('/api/ai/learning/plans/'+row.planId,auth.token);assert.equal(plan.studentId,auth.userId)
      const weeks={},days={};for(const task of plan.tasks){weeks[task.week]=(weeks[task.week]||0)+task.estimatedMinutes;days[task.taskDate]=(days[task.taskDate]||0)+task.estimatedMinutes;assert.ok(task.acceptanceCriteria);assert.ok(task.practiceDeliverable)}
      assert.ok(Object.values(weeks).every(m=>m<=plan.weeklyHours*60));assert.ok(Object.values(days).every(m=>m<=plan.dailyMinutesCap));report.plans++;report.tasks+=plan.tasks.length;report.analysisModes[plan.mocked?'rulePlans':'modelPlans']++
      if(row.evidenceId){assert.ok(plan.tasks.some(t=>t.evidence.some(e=>e.evidenceId===row.evidenceId)));const evidence=plan.tasks.flatMap(t=>t.evidence).find(e=>e.evidenceId===row.evidenceId);assert.equal(evidence.confirmed,false);report.evidence++}
      if(row.reviewSaved){const reviews=await api('/api/ai/learning/plans/'+row.planId+'/reviews',auth.token);assert.ok(reviews.length);report.reviews+=reviews.length}
    }
    if(row.sessionId){const session=await api('/api/ai/interview/sessions/'+row.sessionId,auth.token);assert.equal(session.studentId,auth.userId);assert.ok(session.attempts.length);assert.ok(session.attempts.every(a=>a.answer.includes('匿名合成教学样例')));report.interviews++;report.answers+=session.attempts.length
      if(session.partialReport)report.partialReports++;if(session.report?.reportType==='FINAL')report.finalReports++;report.analysisModes[session.mocked?'ruleInterviews':'modelInterviews']++
    }
    report.students++
  }catch(e){report.failures.push({username,message:e.message})}
  const jobs=await api('/api/jobs',first.token)
  for(const row of Object.values(state.jobs)){const job=jobs.find(j=>j.jobId===row.jobId);assert.ok(job);assert.ok(!job.companyName.startsWith('C'));assert.ok(job.description.includes('seed-realistic-20261007'));report.jobs++}
  const catalog=JSON.parse(fs.readFileSync(path.join(__dirname,'data/verified-public-sources.json'),'utf8'))
  for(const material of catalog.knowledgeMaterials){const row=state.documents[hash(material.title+'|'+material.sourceUrl).slice(0,16)];assert.ok(row,'Missing document '+material.title)
    const doc=await api('/api/ai/knowledge/library/'+row.documentId,first.token);assert.equal(doc.title,material.title);assert.ok(doc.content.includes(material.sourceUrl));assert.equal(doc.status,'PUBLISHED')
    for(const loc of doc.locations){assert.ok(loc.startOffset>=0&&loc.endOffset<=doc.content.length);if(loc.snippet)assert.ok(doc.content.includes(loc.snippet),'Unlocatable snippet')}
    report.documents++
  }
  const other=state.students.seed_student_02
  await assert.rejects(api('/api/resumes/'+other.resumeId,first.token));report.checks.push('Other student resume denied')
  await assert.rejects(api('/api/ai/learning/plans/'+other.planId,first.token));report.checks.push('Other student plan denied')
  await assert.rejects(api('/api/ai/interview/sessions/'+other.sessionId,first.token));report.checks.push('Other student interview denied')
  const ownItems=await api('/api/ai/knowledge/me/items',first.token),otherItems=await api('/api/ai/knowledge/me/items',second.token)
  assert.ok(ownItems.every(x=>!otherItems.some(y=>y.itemId===x.itemId)));report.checks.push('Personal items isolated')
  const r=state.students.seed_student_01,before=await api('/api/matches/resume-job',first.token,'POST',{resumeId:r.resumeId,jobId:r.jobId}),again=await api('/api/matches/resume-job',first.token,'POST',{resumeId:r.resumeId,jobId:r.jobId})
  assert.equal(before.matchId,again.matchId);assert.deepEqual(before.details,again.details);report.checks.push('Repeated matching preserves ID and deterministic coverage')
  const file=path.join(ROOT,'logs/seed-data-verification.json');fs.writeFileSync(file,JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));if(report.failures.length)process.exitCode=1
}
main().catch(e=>{console.error(e.message);process.exitCode=1})
