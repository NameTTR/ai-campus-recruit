const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const collector=require('./collect-knowledge-expansion.cjs');
const batch=require('./data/knowledge-expansion-20261011.json');
const original=require('./data/verified-public-sources.json');
const materials=batch.knowledgeMaterials;
const root=path.resolve(__dirname,'..');

test('the expansion is ready and has exactly 34 distinct materials per direction',()=>{
 assert.equal(batch.reviewStatus,'READY');
 assert.equal(materials.length,102);
 assert.match(batch.generatedAt,/\+08:00$/);
 for(const category of ['Java','前端','运营'])assert.equal(materials.filter(x=>x.category===category).length,34);
 for(const field of ['id','title','sourceUrl'])assert.equal(new Set(materials.map(x=>x[field])).size,102,field);
 assert.equal(collector.sources.length,102);
});

test('new sources and IDs are independent of the old published 34 documents',()=>{
 const oldUrls=new Set(original.knowledgeMaterials.map(x=>x.sourceUrl));
 const oldIds=new Set(original.knowledgeMaterials.map(x=>x.id));
 const oldTitles=new Set(original.knowledgeMaterials.map(x=>x.title));
 for(const x of materials){
  assert.ok(!oldIds.has(x.id),'reused old id: '+x.id);
  assert.ok(!oldTitles.has(x.title),x.title);
  assert.ok(!oldUrls.has(x.sourceUrl),x.sourceUrl);
 }
});

test('all importable materials preserve full GET verification and independent authored content',()=>{
 const officialHosts=new Set(['docs.oracle.com','docs.spring.io','redis.io','developer.mozilla.org','cn.vuejs.org','router.vuejs.org','react.dev','www.typescriptlang.org','learn.microsoft.com','support.microsoft.com','www.itl.nist.gov']);
 assert.equal(new Set(materials.map(x=>x.content)).size,102);
 for(const x of materials){
  const v=x.verification;
  assert.ok(officialHosts.has(new URL(x.sourceUrl).hostname),x.sourceUrl);
  assert.equal(x.status,'VERIFIED',x.id);assert.equal(v.httpStatus,200,x.id);assert.equal(v.method,'GET',x.id);assert.equal(v.readToEnd,true,x.id);
  assert.ok(Number.isInteger(v.contentBytes)&&v.contentBytes>1000,x.id);
  assert.match(v.sha256,/^[a-f0-9]{64}$/);
  assert.ok(v.pageTitle?.trim(),x.id);
  assert.ok(x.applicableVersion?.trim(),x.id);
  assert.match(x.fetchedAt,/\+08:00$/);
  assert.ok(x.content.length>=250&&x.content.length<=600,x.id+' '+x.content.length);
  assert.match(x.content,/易错点：/);assert.match(x.content,/小练习：/);assert.match(x.content,/验收标准：/);
  assert.doesNotMatch(x.content,/实际工作中应先确认输入|围绕.+展开。依据|资料页面开头的可核对片段/);
  const s=collector.sources.find(s=>s.id===x.id);
  assert.ok(s,x.id);assert.equal(x.content,collector.makeContent(s),x.id);
  assert.ok(v.evidenceLocations.length>0,x.id);
  assert.deepEqual(v.bodyEvidence,v.evidenceLocations.map(e=>e.quote),x.id);
 }
});

test('every quoted sentence locates verbatim in the stored normalized article',t=>{
 const requireSnapshots=process.env.REQUIRE_SOURCE_SNAPSHOTS==='1';
 for(const x of materials){
  const v=x.verification,txtPath=path.resolve(root,v.textFile),rawPath=path.resolve(root,v.rawFile);
  assert.ok(txtPath.startsWith(root+path.sep)&&rawPath.startsWith(root+path.sep));
  if(!fs.existsSync(txtPath)||!fs.existsSync(rawPath)){
   if(requireSnapshots) assert.fail('missing source snapshot '+x.id);
   t.skip('source snapshots are not present; run with REQUIRE_SOURCE_SNAPSHOTS=1 after collection');
   return;
  }
  const raw=fs.readFileSync(rawPath),text=fs.readFileSync(txtPath,'utf8');
  assert.equal(raw.length,v.contentBytes,x.id);
  assert.equal(crypto.createHash('sha256').update(raw).digest('hex'),v.sha256,x.id);
  assert.equal(text,collector.stripHtml(collector.extractArticle(raw.toString('utf8'),v.finalUrl)),x.id);
  assert.equal(text.length,v.bodyTextCharacters,x.id);
  for(const e of v.evidenceLocations){
   assert.ok(Number.isInteger(e.startOffset)&&e.startOffset>=0);
   assert.equal(e.endOffset-e.startOffset,e.quote.length,x.id);
   assert.equal(text.slice(e.startOffset,e.endOffset),e.quote,x.id);
   assert.ok(e.quote.toLowerCase().includes(e.term.toLowerCase()),x.id);
   assert.doesNotMatch(e.quote,/^Java SE >|^Skip to|^Table of Contents|^Applies to/);
  }
 }
});

test('source checks correspond one-to-one and preserve failed discovery or retry evidence',()=>{
 for(const x of materials){
  const check=batch.sourceChecks.find(c=>c.id===x.id&&c.url===x.sourceUrl);
  assert.ok(check,x.id);assert.equal(check.ok,true,x.id);
  assert.equal(check.verification.sha256,x.verification.sha256,x.id);
 }
 const failures=batch.sourceChecks.filter(x=>!x.ok);
 for(const fail of failures)assert.ok(!materials.some(x=>x.sourceUrl===fail.url));
});

test('HTML extraction ignores script navigation and sponsored articles',()=>{
 assert.equal(collector.stripHtml('<script>ignore();</script><p>A&nbsp; B &amp; C</p>'),'A B & C');
 const html='<article>Sponsored unrelated</article><main><p>Vue Router is the official router. Navigation can update the view.</p></main>';
 const extracted=collector.extractArticle(html,'https://router.vuejs.org/guide/');
 assert.ok(extracted.includes('Vue Router'));
 assert.ok(!extracted.includes('Sponsored'));
 const result=collector.evidenceFrom(extracted,['Vue Router']);
 assert.equal(result.selected.length,1);
 assert.equal(result.text.slice(result.selected[0].startOffset,result.selected[0].endOffset),result.selected[0].quote);
 assert.match(collector.isoShanghai(),/\+08:00$/);
});
