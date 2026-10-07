#!/usr/bin/env node
'use strict'
const fs=require('node:fs'), path=require('node:path'), crypto=require('node:crypto')
const {buildStudents,roles,label}=require('./data/student-scenarios.cjs')
const ROOT=path.resolve(__dirname,'..'), TAG='seed-realistic-20261007', VERSION='20261007-v3'
const hash=value=>crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex')
function options(args) {
  const out={students:180,activities:24,baseUrl:process.env.SEED_BASE_URL||'http://localhost:18080',timeoutMs:180000,dryRun:false,verifyOnly:false}
  const keys={'--students':'students','--activities':'activities','--base-url':'baseUrl','--timeout-ms':'timeoutMs'}
  for(let i=0;i<args.length;i++) {
    if(args[i]==='--dry-run')out.dryRun=true
    else if(args[i]==='--verify-only')out.verifyOnly=true
    else if(keys[args[i]]) { const key=keys[args[i]],value=args[++i];if(!value||value.startsWith('--'))throw Error('Missing value');out[key]=key==='baseUrl'?value:Number(value) }
    else throw Error('Unknown option: '+args[i])
  }
  if(!Number.isInteger(out.students)||out.students<1||out.students>180)throw Error('students must be 1..180')
  if(!Number.isInteger(out.activities)||out.activities<0||out.activities>out.students)throw Error('activities must be 0..students')
  if(!Number.isInteger(out.timeoutMs)||out.timeoutMs<1000||out.timeoutMs>300000)throw Error('timeout must be 1000..300000')
  const url=new URL(out.baseUrl);if(!['http:','https:'].includes(url.protocol)||url.username||url.password)throw Error('Invalid base URL')
  out.baseUrl=out.baseUrl.replace(/\/+$/,'');return out
}
function readJson(file,fallback) {return fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):fallback}
function writeJson(file,data) {fs.mkdirSync(path.dirname(file),{recursive:true});const temp=file+'.tmp';fs.writeFileSync(temp,JSON.stringify(data,null,2)+'\n');fs.renameSync(temp,file)}
function envConfig() {
  const config={}
  if(fs.existsSync(path.join(ROOT,'.env')))for(const line of fs.readFileSync(path.join(ROOT,'.env'),'utf8').split(/\r?\n/)) {
    const match=line.match(/^([A-Z_0-9]+)=(.*)$/);if(match)config[match[1]]=match[2].trim().replace(/^(['"])(.*)\1$/,'$2')
  }
  return {...config,...process.env}
}
// Minimal editable DOCX, stored without compression; no Node packages or downloaded assets.
function docx(text) {
  const xml=s=>s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')
  const entries={
    '[Content_Types].xml':'<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>',
    '_rels/.rels':'<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>',
    'word/document.xml':'<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'+text.split('\n').map(line=>'<w:p><w:r><w:rPr><w:rFonts w:eastAsia="Noto Sans CJK SC"/><w:sz w:val="22"/></w:rPr><w:t xml:space="preserve">'+xml(line)+'</w:t></w:r></w:p>').join('')+'<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="850" w:right="850" w:bottom="850" w:left="850"/></w:sectPr></w:body></w:document>'}
  const files=[],central=[];let offset=0
  for(const [name,content]of Object.entries(entries)) {
    const filename=Buffer.from(name),data=Buffer.from(content);let crc=0xffffffff
    for(const byte of data){crc^=byte;for(let i=0;i<8;i++)crc=(crc>>>1)^(crc&1?0xedb88320:0)}crc=(crc^0xffffffff)>>>0
    const head=Buffer.alloc(30);head.writeUInt32LE(0x04034b50);head.writeUInt16LE(20,4);head.writeUInt32LE(crc,14);head.writeUInt32LE(data.length,18);head.writeUInt32LE(data.length,22);head.writeUInt16LE(filename.length,26)
    const dir=Buffer.alloc(46);dir.writeUInt32LE(0x02014b50);dir.writeUInt16LE(20,4);dir.writeUInt16LE(20,6);dir.writeUInt32LE(crc,16);dir.writeUInt32LE(data.length,20);dir.writeUInt32LE(data.length,24);dir.writeUInt16LE(filename.length,28);dir.writeUInt32LE(offset,42)
    files.push(head,filename,data);central.push(dir,filename);offset+=30+filename.length+data.length
  }
  const directory=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(3,8);end.writeUInt16LE(3,10);end.writeUInt32LE(directory.length,12);end.writeUInt32LE(offset,16)
  return Buffer.concat([...files,directory,end])
}
function buildJobs() {
  const companies=['知序软件','南桥信息','青禾数字','微帆科技','澄光产品','云栈服务','拾光内容','方格互动','栖木科技','远川数据','禾木智能','星桥网络','松果互动','墨羽信息','云起软件','青藤咨询','澜图科技','启程数字','森屿服务','知行产品','谷雨内容','镜湖科技','微澜数据','向阳互动'],cities=['杭州','南京','成都','武汉','广州','上海','苏州','深圳','合肥','西安','厦门','福州']
  return companies.flatMap((name,i)=>Array.from({length:6},(_,j)=>{
    const role=roles[j%3],senior=j>=3,skills=role.skills.slice(0,senior?6:3),city=cities[(i+(senior?1:0))%cities.length]
    const task=role.key==='Java'?'参与内部业务接口开发、参数校验和测试，阅读已有代码并记录问题':role.key==='前端'?'参与表单与列表页面开发，处理加载、异常和移动端交互':'协助选题、活动执行和匿名数据整理，统一统计口径并完成复盘'
    const description=['【合成招聘教学样例，非真实招聘，不可实际投递】',task+'。',
      '明确必需：'+skills.slice(0,3).join('、')+'，能说明课程或实践中的个人工作。',
      senior?'优先：'+skills.slice(3).join('、')+'；有对应项目材料者优先。':'优先：愿意记录验证过程并根据反馈修改。',
      (i%4===0?'专科及以上':i%4===1?'本科在读': '本科及以上')+'，'+city+'线下，每周至少'+(senior?4:3)+'天，连续'+(senior?4:3)+'个月，预计2026年11月到岗。',
      '薪酬为合成测试范围，不代表市场报价。样例标记：'+TAG].join('\n')
    return {key:'synthetic-'+i+'-'+j,companyUsername:'seed_company_'+String(i+1).padStart(2,'0'),companyName:name+'（合成企业）',title:(senior?'专项'+role.title:role.title),city,salaryRange:senior?'180–240元/天（样例）':'120–180元/天（样例）',requiredSkills:skills,description,roleKey:role.key,synthetic:true}
  }))
}
function publicJob(row,index) {
  const company=row.company||row.companyName||'公开招聘资料',requirements=Array.isArray(row.requirements)?row.requirements.join('\n'):row.requirements||''
  return {key:'public-'+hash(row.sourceUrl||row.title).slice(0,12),sourceUrl:row.sourceUrl,companyUsername:'seed_public_jobs',companyName:company+'（公开岗位参考）',title:row.title,city:row.city||'原页未注明',salaryRange:row.salaryRange||'未公开',requiredSkills:row.skills||row.requiredSkills||[],roleKey:row.category||row.roleDirection||(['Java','前端','运营'][index%3]),synthetic:false,
    description:['【公开招聘资料镜像，学习与要求比较用途；招聘状态请以原网站为准】',row.recruitmentType||'公开岗位要求参考',row.recruitmentKind==='SOCIAL'?'工作经验：'+(row.workExperience||'原页未注明'):'适用范围：'+(row.eligibility||'以官方岗位页为准'),row.description||'',requirements,'来源：'+row.sourceUrl,'核对时间：'+(row.queriedAt||row.fetchedAt),'原页更新：'+(row.sourceUpdatedAt||'未注明'),row.recruitmentKind==='SOCIAL'?'保留原岗位年限和学历；社会招聘不能视为实习岗位。':'毕业年、地点和岗位要求以原招聘项目为准；未公开的出勤及薪酬不作推测。','样例标记：'+TAG].join('\n')}
}
class Client {
  constructor(opt){this.opt=opt}
  async api(route,token,method='GET',body){
    const form=body instanceof FormData,response=await fetch(this.opt.baseUrl+route,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(!form&&body?{'Content-Type':'application/json'}:{})},body:body?(form?body:JSON.stringify(body)):undefined,signal:AbortSignal.timeout(this.opt.timeoutMs)})
    const value=await response.json();if(!response.ok||value.code!==0){const error=new Error(method+' '+route+': '+String(value.message||response.status).slice(0,500));error.status=response.status;throw error}return value.data
  }
}
async function run(opt) {
  const catalog=readJson(path.join(__dirname,'data/verified-public-sources.json'),{knowledgeMaterials:[],recruitmentJobs:[]})
  const students=buildStudents(opt.students),jobs=[...buildJobs(),...catalog.recruitmentJobs.map(publicJob)]
  if(opt.dryRun){console.log(JSON.stringify({dryRun:true,students:students.length,syntheticJobs:144,publicJobs:catalog.recruitmentJobs.length,knowledge:catalog.knowledgeMaterials.length,activityStudents:opt.activities,roles:Object.fromEntries(roles.map(r=>[r.key,students.filter(s=>s.roleKey===r.key).length]))},null,2));return}
  if(!catalog.knowledgeMaterials.length)throw Error('Verified public source catalog is required before importing')
  const env=envConfig(),accessFile=path.join(ROOT,'logs/seed-realistic-access.json'),stateFile=path.join(ROOT,'logs/seed-realistic-state.json')
  let access=readJson(accessFile,null)
  if(!access){access={password:env.SEED_PASSWORD||crypto.randomBytes(18).toString('base64url')+'!aA1',legacyPassword:env.SEED_LEGACY_PASSWORD||null};writeJson(accessFile,access)}
  const state=readJson(stateFile,{baseUrl:opt.baseUrl,version:VERSION,students:{},jobs:{},documents:{}})
  if(state.baseUrl!==opt.baseUrl)throw Error('State belongs to a different API server; use a separate workspace')
  const report={version:VERSION,startedAt:new Date().toISOString(),counts:{},failures:[],sourceCatalog:'scripts/data/verified-public-sources.json',studentIds:[],jobIds:[],documentIds:[]},client=new Client(opt)
  const api=(...a)=>client.api(...a),bump=key=>{report.counts[key]=(report.counts[key]||0)+1},save=()=>writeJson(stateFile,state)
  async function account(username,displayName,role) {
    let result
    try{result=await api('/api/auth/login',null,'POST',{username,password:access.password})}
    catch(e){
      if(e.status!==401)throw e
      const adminAccounts=await api('/api/auth/admin/accounts',admin.token)
      if(adminAccounts.some(a=>a.username===username)) {
        if(!access.legacyPassword)throw Error('Existing seed account requires SEED_LEGACY_PASSWORD: '+username)
        result=await api('/api/auth/login',null,'POST',{username,password:access.legacyPassword})
      } else {result=await api('/api/auth/register',null,'POST',{username,password:access.password,displayName,role});bump('accountsCreated')}
    }
    if(result.role!==role)throw Error('Seed account has incompatible role: '+username)
    return result
  }
  const admin=await api('/api/auth/login',null,'POST',{username:env.SEED_ADMIN_USER||'admin',password:env.SEED_ADMIN_PASSWORD||env.BOOTSTRAP_ADMIN_PASSWORD})
  if(admin.role!=='ADMIN')throw Error('Knowledge import requires administrator')
  async function step(kind,name,fn){try{await fn();save()}catch(e){report.failures.push({kind,name,message:e.message});console.log('FAILED '+kind+' '+name+': '+e.message);save()} }
  if(opt.verifyOnly) {
    for(const s of students)await step('verify',s.username,async()=>{
      const auth=await account(s.username,s.studentProfile.displayName,'STUDENT'),record=state.students[s.username];if(!record?.resumeId)throw Error('No saved resume')
      const p=await api('/api/resumes/master-profile',auth.token),r=await api('/api/resumes/'+record.resumeId,auth.token)
      if(p.data.experiences.length!==s.profileData.experiences.length||r.studentId!==auth.userId)throw Error('Profile/resume persistence mismatch')
      if(record.planId)await api('/api/ai/learning/plans/'+record.planId,auth.token)
      if(record.sessionId)await api('/api/ai/interview/sessions/'+record.sessionId,auth.token)
      bump('studentsVerified')
    })
  } else {
    const companies=new Map()
    for(const row of jobs)await step('job',row.key,async()=>{
      if(!companies.has(row.companyUsername))companies.set(row.companyUsername,await account(row.companyUsername,row.synthetic?row.companyName:'公开招聘资料管理员（非企业官方账号）','COMPANY'))
      const owner=companies.get(row.companyUsername),list=await api('/api/jobs',owner.token)
      let current=state.jobs[row.key]&&list.find(j=>j.jobId===state.jobs[row.key].jobId)
      if(!current)current=list.find(j=>j.title===row.title&&j.city===row.city&&j.description.includes(TAG)&&(row.synthetic||j.description.split(/\r?\n/).includes('来源：'+row.sourceUrl)))
      const payload={companyId:owner.userId,companyName:row.companyName,title:row.title,city:row.city,salaryRange:row.salaryRange,requiredSkills:row.requiredSkills,description:row.description}
      if(!current){current=await api('/api/jobs',owner.token,'POST',payload);bump(row.synthetic?'syntheticJobsCreated':'publicJobsCreated')}
      else if(current.companyName!==row.companyName||current.description!==row.description){current=await api('/api/jobs/'+current.jobId,owner.token,'PUT',payload);bump('jobsUpdated')}else bump('jobsReused')
      state.jobs[row.key]={jobId:current.jobId,roleKey:row.roleKey,synthetic:row.synthetic,companyUsername:row.companyUsername};report.jobIds.push(current.jobId)
    })
    // Repair only documents from the interrupted v1 seed batch; never alter unrelated documents.
    const allDocs=await api('/api/ai/knowledge/documents?limit=100&keyword='+TAG,admin.token)
    const legacyDocs=allDocs.filter(d=>(d.tags||[]).includes(TAG))
    const used=new Set(Object.values(state.documents).map(d=>d.documentId))
    for(let i=0;i<catalog.knowledgeMaterials.length;i++) {
      const row=catalog.knowledgeMaterials[i],key=hash(row.title+'|'+row.sourceUrl).slice(0,16)
      await step('knowledge',row.title,async()=>{
        const content=row.content+'\n\n来源与核对\n'+row.sourceName+'\n'+row.sourceUrl+'\n读取核对时间：'+row.fetchedAt+'\n中文摘要为自编讲解；引用可定位不等同于全部事实已自动审核。'
        const payload={title:row.title,content,category:row.category,source:row.sourceName.slice(0,128),tags:[TAG,'official-source',...(row.skills||[])],roles:['STUDENT','COMPANY','ADMIN']}
        let id=state.documents[key]?.documentId
        if(!id){const old=legacyDocs.find(d=>!used.has(d.documentId));if(old){id=old.documentId;used.add(id)}}
        let doc=id?await api('/api/ai/knowledge/library/'+id,admin.token):null
        if(doc&&doc.content===content&&doc.title===row.title){bump('knowledgeReused')}
        else if(doc){doc=await api('/api/ai/knowledge/publications/'+id,admin.token,'PUT',{...payload,expectedRevision:doc.version});bump('knowledgeCorrected')}
        else{doc=await api('/api/ai/knowledge/publications',admin.token,'POST',payload);id=doc.documentId;bump('knowledgeCreated')}
        if(doc.status!=='PUBLISHED')await api('/api/ai/knowledge/publications/'+id+'/publish',admin.token,'POST',{})
        state.documents[key]={documentId:id,title:row.title,fingerprint:hash(payload)};report.documentIds.push(id)
      })
    }
    const templates=await api('/api/resumes/templates',(await account(students[0].username,students[0].studentProfile.displayName,'STUDENT')).token)
    for(let i=0;i<students.length;i++)await step('student',students[i].username,async()=>{
      const s=students[i],auth=await account(s.username,s.studentProfile.displayName,'STUDENT'),token=auth.token,record=state.students[s.username]||{}
      const activityBaseComplete=Boolean(record.deliveryId&&record.draftId&&record.planId&&record.sessionId&&record.answerSaved&&record.partialReportSaved)
      const activityComplete=Boolean(activityBaseComplete&&(
        i<3 || i>=9 || i>=3&&i<6&&record.finalReportSaved || i>=6&&i<9&&record.evidenceId&&record.reviewSaved))
      await api('/api/students/profile',token,'PUT',{...s.studentProfile,userId:auth.userId,role:'STUDENT'})
      const previous=await api('/api/resumes/master-profile',token)
      if(record.profileHash!==hash(s.profileData)) {
        const existing=previous.data?.experiences||[]
        if(existing.some(e=>!String(e.source?.assessment||'').includes('样例')))throw Error('Seed profile was manually edited; preserving current content')
        await api('/api/resumes/master-profile',token,'PUT',{expectedRevision:previous.revision,data:s.profileData,confirmed:true});record.profileHash=hash(s.profileData);bump('profilesSaved')
      }else bump('profilesReused')
      state.students[s.username]=record;save()
      const resumeList=await api('/api/resumes',token),filename=s.username+'-anonymous.docx'
      let resume=record.resumeId&&resumeList.find(r=>r.resumeId===record.resumeId)
      if(!resume)resume=resumeList.find(r=>r.fileName===filename)
      if(!resume){const file=new FormData();file.append('file',new Blob([docx(s.resumeText)]),filename);resume=await api('/api/resumes/upload',token,'POST',file);bump('resumesUploaded')}else bump('resumesReused')
      record.resumeId=resume.resumeId;save()
      // Upload extraction is conservative; preserve the explicitly provided facts via its existing profile editor.
      await api('/api/resumes/'+resume.resumeId+'/profile',token,'PATCH',{education:s.profileData.education.map(e=>e.school+' '+e.degree+' '+e.major).join('；'),skills:s.profileData.skills.map(x=>x.name),projects:s.profileData.experiences.map(e=>e.title+'：'+e.actions+'；'+e.methods+'；'+e.results)})
      const ownJobs=jobs.filter(j=>j.synthetic&&j.roleKey===s.roleKey),selected=[ownJobs[i%ownJobs.length],ownJobs[(i+5)%ownJobs.length],ownJobs[(i+9)%ownJobs.length]]
      const matches=[]
      for(const job of selected){const saved=state.jobs[job.key];if(!saved)throw Error('Job import missing: '+job.key);matches.push(await api('/api/matches/resume-job',token,'POST',{resumeId:resume.resumeId,jobId:saved.jobId}));bump('matchesReadOrCreated')}
      record.matchIds=matches.map(m=>m.matchId);record.jobId=state.jobs[selected[0].key].jobId;save()
      if(i<opt.activities && !activityComplete) {
        if(!record.deliveryId){const list=await api('/api/deliveries/my',token);let delivery=list.find(d=>d.resumeId===resume.resumeId&&d.jobId===record.jobId)
          if(!delivery){delivery=await api('/api/deliveries',token,'POST',{studentId:auth.userId,resumeId:resume.resumeId,jobId:record.jobId,resumeSourceFormat:resume.sourceFormat,resumeParseStatus:resume.parseStatus,resumeParsedTextLength:resume.parsedTextLength});bump('syntheticDeliveriesCreated')}
          record.deliveryId=delivery.deliveryId;save()}
        const campus=jobs.filter(j=>!j.synthetic&&j.roleKey===s.roleKey&&catalog.recruitmentJobs.some(r=>publicJob(r,0).key===j.key&&r.recruitmentKind!=='SOCIAL'))
        if(campus.length){const official=campus[i%campus.length],id=state.jobs[official.key]?.jobId
          if(id){const match=await api('/api/matches/resume-job',token,'POST',{resumeId:resume.resumeId,jobId:id});record.publicMatchId=match.matchId;bump('publicMatchesReadOrCreated');save()}}
        const context={resumeId:resume.resumeId,jobId:record.jobId,matchId:matches[0].matchId,targetRole:s.targetRole+'（匿名样例）'}
        if(!record.draftId){const list=await api('/api/resumes/drafts',token);let draft=list.find(d=>d.jobSnapshot?.jobId===record.jobId&&d.targetRole===context.targetRole)
          if(!draft){draft=await api('/api/resumes/drafts',token,'POST',{templateId:templates[i%templates.length].id,targetRole:context.targetRole,jobId:record.jobId});bump('draftsCreated')}
          if(!draft.confirmed)draft=await api('/api/resumes/drafts/'+draft.id,token,'PATCH',{expectedRevision:draft.revision,templateId:draft.templateId,data:draft.data,confirm:true})
          record.draftId=draft.id;save()
        }
        if(!record.planId){const list=await api('/api/ai/learning/plans',token);let plan=list.find(p=>p.targetRole===context.targetRole&&p.jobId===record.jobId)
          if(!plan){plan=await api('/api/ai/learning/plans',token,'POST',{...context,...s.studySettings});bump('plansCreated');bump(plan.mocked?'plansRuleFallback':'plansModel')}
          record.planId=plan.planId;save()
        }
        if(!record.planActivity){const plan=await api('/api/ai/learning/plans/'+record.planId,token),task=plan.tasks[0]
          if(task)await api('/api/ai/learning/plans/'+plan.planId+'/tasks/'+task.taskId,token,'PUT',{status:i%3===0?'IN_PROGRESS':i%3===1?'COMPLETED':'PENDING',actualMinutes:i%3===1?45:i%3===0?20:0,feedback:'合成教学行为记录：'+(i%3===1?'完成基础练习，未提交正式成果':i%3===0?'已阅读材料，需要补充验证':'本周课程较多，尚未开始')})
          record.planActivity=true;save()
        }
        if(!record.sessionId){const list=await api('/api/ai/interview/sessions',token);const mode=i%2?'MOCK':'COACHING';let session=list.find(x=>x.targetRole===context.targetRole&&x.jobId===record.jobId&&x.mode===mode)
          if(!session){session=await api('/api/ai/interview/sessions',token,'POST',{...context,mode,sourceType:'JOB',sourceId:record.jobId,questionCount:4,timerMinutes:mode==='MOCK'?20:null});bump('interviewsCreated');bump(session.mocked?'interviewsRuleFallback':'interviewsModel')}
          record.sessionId=session.sessionId;save()
        }
        if(!record.answerSaved){let session=await api('/api/ai/interview/sessions/'+record.sessionId,token);const q=session.questions[0]
          session=await api('/api/ai/interview/sessions/'+session.sessionId+'/questions/'+q.questionId+'/answer',token,'PUT',{questionId:q.questionId,answer:'【'+label+'】'+s.answer});record.answerSaved=true;save();bump('answersSaved')
        }
        if(!record.partialReportSaved){const current=await api('/api/ai/interview/sessions/'+record.sessionId,token)
          if(!current.partialReport){await api('/api/ai/interview/sessions/'+record.sessionId+'/partial-report',token,'POST',{});bump('partialReportsCreated')}
          record.partialReportSaved=true;save()}
        // Three students have complete reports; answers remain modest synthetic project explanations.
        if(i>=3&&i<6&&!record.finalReportSaved){let session=await api('/api/ai/interview/sessions/'+record.sessionId,token)
          if(session.pausedAt)session=await api('/api/ai/interview/sessions/'+session.sessionId+'/resume',token,'POST',{})
          const remaining=session.questions.filter(q=>!q.followUp&&!q.parentQuestionId&&!session.answers.some(a=>a.questionId===q.questionId))
          for(const q of remaining)await api('/api/ai/interview/sessions/'+session.sessionId+'/questions/'+q.questionId+'/answer',token,'PUT',{questionId:q.questionId,answer:'【'+label+'】针对这道题，我能提供的实践依据是：'+s.answer+' 超出已做课程实验的部分，尚不能给出经过验证的结论，需要查资料并补充实验。'})
          session=await api('/api/ai/interview/sessions/'+session.sessionId+'/finish',token,'POST',{});record.finalReportSaved=true;bump('finalReportsCreated');save()
        }
        if(i>=6&&i<9&&!record.evidenceId){const plan=await api('/api/ai/learning/plans/'+record.planId,token),task=plan.tasks[0]
          if(task){const evidence=await api('/api/ai/learning/plans/'+plan.planId+'/tasks/'+task.taskId+'/evidence',token,'POST',{description:'【'+label+'】本次尝试：'+task.title+'。目前只整理了阅读要点与问题清单，还未完成要求中的实际练习和完整测试。未提供作品链接，无法证明已掌握。下一步按验收标准补充实现过程与测试记录。',links:[]});record.evidenceId=evidence.evidenceId;bump('evidenceSubmitted');save()}
        }
        if(i>=6&&i<9&&!record.reviewSaved){const plan=await api('/api/ai/learning/plans/'+record.planId,token),weekTasks=plan.tasks.filter(t=>t.week===1)
          await api('/api/ai/learning/plans/'+plan.planId+'/reviews',token,'POST',{week:1,plannedMinutes:weekTasks.reduce((sum,t)=>sum+t.estimatedMinutes,0),actualMinutes:45,completedTasks:weekTasks.filter(t=>t.status==='COMPLETED').length,incompleteReason:'合成教学复盘：课程作业集中，练习没有完成验证',hardestTask:weekTasks[0]?.title||'基础练习',needsSplit:true,mastery:2,nextWeekMinutes:s.studySettings.weeklyHours*60,newProblems:['需要先补充一个可复现的小实验，再提交成果']});record.reviewSaved=true;bump('weeklyReviewsSaved');save()
        }
      }
      if(i<opt.activities && !activityComplete) { record.activitySeedVersion=VERSION; save() }
      const topics=await api('/api/ai/knowledge/topics?roleDirection='+encodeURIComponent(s.roleKey),token),topic=topics[i%topics.length]
      if(topic&&!record.knowledgeItemId){const items=await api('/api/ai/knowledge/me/items',token);let item=items.find(x=>x.topicId===topic.id&&x.kind==='BOOKMARK')
        if(!item)item=await api('/api/ai/knowledge/me/items',token,'POST',{topicId:topic.id,kind:'BOOKMARK',status:['TO_LEARN','LEARNING','TO_REVIEW'][Math.floor(i/3)%3],note:'匿名教学笔记：'+(s.roleKey==='Java'?'先做小实验，再记录异常路径和测试结果。':s.roleKey==='前端'?'先检查交互、空值和网络失败，再整理组件设计。':'先明确用户目标和数据口径，再做小范围活动复盘。')+'还没有形成可提交的正式实践成果。',intervalDays:[1,3,7,14],reviewEnabled:true,expectedRevision:0})
        record.knowledgeItemId=item.itemId;bump('knowledgeItemsSaved');save()}
      if(i<opt.activities&&!record.historyCreated){await api('/api/ai/knowledge/query',token,'POST',{query:s.roleKey==='Java'?'MySQL 索引如何通过 EXPLAIN 验证':s.roleKey==='前端'?'表单请求失败与重复提交怎么处理':'如何统一报名转化率的统计口径',roleDirection:s.roleKey,useAi:false,...{jobId:record.jobId,resumeId:record.resumeId}});record.historyCreated=true;bump('queryHistoriesSaved');save()}
      record.userId=auth.userId;report.studentIds.push(auth.userId);bump('studentsCompleted');save();console.log('完成 '+s.username+' '+s.roleKey)
    })
  }
  report.finishedAt=new Date().toISOString();report.savedTotals={students:Object.keys(state.students).length,jobs:Object.keys(state.jobs).length,documents:Object.keys(state.documents).length}
  writeJson(path.join(ROOT,'logs/seed-realistic-report.json'),report);console.log(JSON.stringify({counts:report.counts,savedTotals:report.savedTotals,failures:report.failures.length,report:'logs/seed-realistic-report.json'},null,2))
  if(report.failures.length)process.exitCode=1
}
module.exports={options,buildJobs,docx,Client,publicJob,envConfig,hash}
if(require.main===module)run(options(process.argv.slice(2))).catch(e=>{console.error(e.message);process.exitCode=1})
