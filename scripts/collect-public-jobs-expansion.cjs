/* Public, read-only recruitment API collection. No generated job facts. */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const ROOT = path.resolve(__dirname, '..');
const OUTPUT = path.join(__dirname, 'data/public-jobs-expansion-20261011.json');
const LOG = path.join(ROOT, 'logs/public-expansion');
const OLD = path.join(__dirname, 'data/verified-public-sources.json');
const CATEGORIES = ['Java', '前端', '运营'];
const TARGET = 42;
const TERMS = ['Java', 'Spring Boot', 'Spring Cloud', 'Spring', 'MySQL', 'Redis', 'SQL', 'Python', 'Shell', 'C++', 'Go', 'HTML', 'CSS', 'JavaScript', 'TypeScript', 'React', 'Vue', 'Node.js', 'Webpack', 'Vite', 'Electron', 'TCP', 'UDP', 'Linux', 'AI', 'NoSQL', '数据结构', '设计模式', '算法', '分布式', '消息队列', '数据分析', '用户运营', '内容运营', '产品运营', '社群运营', '活动策划', '文案', 'Excel', 'A/B', '用户研究', '沟通', '电商', '直播', '短视频', '指标', '数据', '复盘', '增长', '营销'];
const now = () => new Date(Date.now() + 8 * 3600e3).toISOString().replace('Z', '+08:00');
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const sourceChecks = [];
const RESUME = process.argv.includes('--resume');
const prior = RESUME && fs.existsSync(OUTPUT) ? JSON.parse(fs.readFileSync(OUTPUT, 'utf8')) : null;
const priorChecks = new Map((prior?.sourceChecks || []).map(c => [c.sourceId, c]));

function matchingOccurrence(text, term) {
  const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = /^[A-Za-z]/.test(term) ? new RegExp(`(?<![A-Za-z])${escaped}(?![A-Za-z])`, 'i') : new RegExp(escaped);
  return re.exec(text);
}

function extractSkills(requirements) {
  return TERMS.map(skill => {
    const m = matchingOccurrence(requirements, skill);
    if (!m) return null;
    const start = Math.max(0, m.index - 25);
    const end = Math.min(requirements.length, m.index + m[0].length + 45);
    return { skill, excerpt: requirements.slice(start, end), start, end, matchedText: m[0] };
  }).filter(Boolean);
}

function categoryOf(title, requirements) {
  // Normalize only for classifying, retaining the exact official title in records.
  const normalizedTitle = title.normalize('NFKC');
  if (/运营|增长|营销|内容|社群|活动/.test(normalizedTitle) && !/运营开发|运营工程师|运维|算法|开发工程师|研发工程师|技术美术/.test(normalizedTitle)) return '运营';
  if (!/算法/.test(normalizedTitle) && /前端|全栈|Web\b|网页|浏览器|桌面端|Electron/i.test(normalizedTitle) && /JavaScript|TypeScript|React|Vue|HTML|CSS|Electron|前端/i.test(requirements)) return '前端';
  if (matchingOccurrence(requirements, 'Java') && /后台|后端|后臺|全栈|Java|平台开发|开发工程师|研发工程师|数据工程|云原生|软件开发|工程研发/i.test(title)) return 'Java';
  return null;
}

async function request(url, id, options = {}) {
  fs.mkdirSync(LOG, { recursive: true });
  const method = options.method || 'GET';
  const saved = priorChecks.get(id);
  const cacheFile = path.join(LOG, `${id}.json`);
  if (RESUME && saved?.url === url && saved.method === method && fs.existsSync(cacheFile)
    && JSON.stringify(saved.requestBody) === (options.body ? JSON.stringify(JSON.parse(options.body)) : undefined)) {
    const body = fs.readFileSync(cacheFile);
    if (hash(body) === saved.sha256 && body.length === saved.contentBytes) {
      sourceChecks.push(saved);
      return { json: JSON.parse(body.toString('utf8')), verification: saved };
    }
  }
  let last;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const r = await fetch(url, { ...options, signal: AbortSignal.timeout(30000), headers: { 'User-Agent': 'CampusRecruit-OfficialSourceVerification/1.0', ...options.headers } });
      const b = Buffer.from(await r.arrayBuffer());
      const record = { sourceId: id, url, finalUrl: r.url, queriedAt: now(), method, httpStatus: r.status, readToEnd: true, contentBytes: b.length, sha256: hash(b) };
      if (options.body) record.requestBody = JSON.parse(options.body);
      const json = JSON.parse(b.toString('utf8'));
      record.apiCode = json.Code ?? json.status;
      record.status = r.ok && (record.apiCode === 200 || record.apiCode === 0) ? 'VERIFIED_READ' : 'API_ERROR';
      sourceChecks.push(record);
      fs.writeFileSync(path.join(LOG, `${id}.json`), b);
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return { json, verification: record };
    } catch (e) {
      last = e;
      await delay(350 * (attempt + 1));
    }
  }
  sourceChecks.push({ sourceId: id, url, method, queriedAt: now(), status: 'FETCH_FAILED', error: last.message });
  return null;
}

function mapSocial(data, verification) {
  const evidence = extractSkills(data.Requirement || '');
  return {
    id: `tencent-${data.PostId}`, title: data.RecruitPostName, company: data.ComName || '腾讯', city: data.LocationName || '未公开',
    category: categoryOf(data.RecruitPostName, data.Requirement || ''), skills: evidence.map(x => x.skill),
    requirements: data.Requirement, originalRequirements: data.Requirement, description: data.Responsibility,
    workExperience: data.RequireWorkYearsName || '未公开', recruitmentType: '社会招聘（岗位要求参考，非校招）', recruitmentKind: 'SOCIAL', salary: '未公开',
    sourceName: '腾讯官方招聘', sourceUrl: `https://careers.tencent.com/jobdesc.html?postId=${data.PostId}`, sourceApiUrl: verification.url,
    sourcePostId: data.PostId, queriedAt: verification.queriedAt, sourceUpdatedAt: data.LastUpdateTime || '未公开', status: 'VERIFIED_PUBLIC',
    verification: { ...verification, requirementsCopiedFrom: 'Requirement', skillsBasis: '仅抽取官方Requirement中明确出现的词；英文词使用字母边界，不把JavaScript误记Java', companyBasis: 'ComName；为空时用腾讯招聘站名称，不推断具体签约法人', salaryBasis: '官方详情未提供薪资' }, skillsEvidence: evidence,
  };
}

function mapCampus(data, verification, projects) {
  const evidence = extractSkills(data.request || '');
  const internship = /实习/.test(data.recruitLabelName || data.projectName || '');
  const project = projects.find(p => String(p.projectId ?? p.id) === String(data.projectId));
  return {
    id: `tencent-campus-${data.postId}`, title: data.title, company: '腾讯', city: (data.workCityList || []).join('、') || '未公开', cities: data.workCityList || [],
    category: categoryOf(data.title, data.request || ''), skills: evidence.map(x => x.skill), requirements: data.request, originalRequirements: data.request, description: data.desc,
    bonusRequirements: internship ? data.internBonus : data.graduateBonus, workExperience: '未公开', recruitmentType: internship ? `实习（${data.recruitLabelName || data.projectName}）` : `校园招聘（${data.recruitLabelName || data.projectName}）`, recruitmentKind: internship ? 'INTERNSHIP' : 'CAMPUS',
    recruitmentProject: data.projectName, eligibility: project?.range || project?.scope || '以官方当前招聘项目范围为准', salary: '未公开',
    sourceName: '腾讯官方校招', sourceUrl: verification.url, sourcePageUrl: 'https://join.qq.com/post.html', sourceApiUrl: verification.url, sourcePostId: data.postId,
    queriedAt: verification.queriedAt, sourceUpdatedAt: '未公开', status: 'VERIFIED_PUBLIC',
    verification: { ...verification, requirementsCopiedFrom: 'request', skillsBasis: '仅抽取官方request中明确出现的词', salaryBasis: '官方详情未提供薪资', sourceUrlBasis: '官方旧jobdesc.html路由当前跳转404；使用可读取的官方职位详情JSON为核对地址' }, skillsEvidence: evidence,
  };
}

async function collect() {
  const old = JSON.parse(fs.readFileSync(OLD, 'utf8'));
  const baselineJobs = prior?.baselineJobs || old.recruitmentJobs.map(j => ({ id: j.id, sourcePostId: j.sourcePostId, sourceUrl: j.sourceUrl }));
  const used = new Set(baselineJobs.flatMap(j => [j.id, j.sourceUrl, j.sourcePostId]));
  const groups = Object.fromEntries(CATEGORIES.map(c => [c, []]));
  const accept = (job, limits = Object.fromEntries(CATEGORIES.map(c => [c, TARGET]))) => {
    if (!job?.category || !job.requirements || !job.description || !job.skills.length) return false;
    if (used.has(job.id) || used.has(job.sourceUrl) || used.has(job.sourcePostId) || groups[job.category].length >= limits[job.category]) return false;
    used.add(job.id); used.add(job.sourceUrl); used.add(job.sourcePostId);
    groups[job.category].push(job);
    console.log(`Accepted ${job.category} ${groups[job.category].length}/${TARGET}: ${job.title}`);
    return true;
  };
  for (const job of prior?.recruitmentJobs || []) if (categoryOf(job.title, job.requirements) === job.category) accept(job, { Java: 126, 前端: 126, 运营: 126 });

  const projectResponse = await request('https://join.qq.com/api/v1/position/getProjectMapping', 'tencent-campus-projects');
  const projects = projectResponse?.json?.data || [];
  const campusPosts = new Map();
  for (const keyword of ['后台', 'Java', '前端', '全栈', '运营', '营销', '增长']) {
    const r = await request('https://join.qq.com/api/v1/position/searchPosition', `tencent-campus-list-${keyword}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ keyword, pageIndex: 1, pageSize: 100 }) });
    for (const post of r?.json?.data?.positionList || []) if (post.postId && !String(post.postId).startsWith('-')) campusPosts.set(post.postId, post);
    await delay(100);
  }
  for (const post of campusPosts.values()) {
    if (used.has(post.postId)) continue;
    // These title checks merely avoid unrelated detail requests; category requires actual detail requirements.
    if (!/后台|后端|前端|全栈|运营|营销|增长/.test(post.positionTitle)) continue;
    const r = await request(`https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=${post.postId}`, `tencent-campus-detail-${post.postId}`);
    if (r?.json?.status === 0 && r.json.data?.postId) accept(mapCampus(r.json.data, r.verification, Array.isArray(projects) ? projects : []));
    await delay(100);
  }

  const socialPosts = new Map();
  for (const keyword of ['Java', '前端', '前端开发', 'JavaScript', 'TypeScript', 'React', 'Vue', 'Node.js', 'HTML', 'CSS', 'Web', '网页', '小程序', 'H5', '全栈', 'Electron', '运营', '内容运营', '用户运营', '产品运营']) {
    for (let pageIndex = 1; pageIndex <= (keyword === '运营' ? 2 : 3); pageIndex++) {
      const url = `https://careers.tencent.com/tencentcareer/api/post/Query?countryId=&cityId=&bgIds=&productId=&categoryId=&parentCategoryId=&attrId=&pageIndex=${pageIndex}&pageSize=100&language=zh-cn&area=cn&keyword=${encodeURIComponent(keyword)}`;
      const r = await request(url, `tencent-social-list-${keyword}-${pageIndex}`);
      const posts = r?.json?.Data?.Posts || [];
      for (const post of posts) if (post.SourceID === 1 && post.PostId) socialPosts.set(post.PostId, post);
      if (posts.length < 100) break;
      await delay(100);
    }
  }
  for (const post of socialPosts.values()) {
    if (used.has(post.PostId)) continue;
    const likely = /运营|增长|营销|内容|社群|活动/.test(post.RecruitPostName) && !/开发|研发|算法/.test(post.RecruitPostName) ? '运营' : /前端|全栈|浏览器|桌面端|Electron/i.test(post.RecruitPostName) ? '前端' : 'Java';
    if (groups[likely]?.length >= TARGET) continue;
    const r = await request(`https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=${post.PostId}&language=zh-cn`, `tencent-social-detail-${post.PostId}`);
    if (r?.json?.Code === 200 && r.json.Data?.PostId) accept(mapSocial(r.json.Data, r.verification));
    await delay(100);
    if (CATEGORIES.every(c => groups[c].length >= TARGET)) break;
  }
  // A shortage in an actual official category is reported, never filled with fabricated jobs.
  // Distribute the shortfall across categories with verified candidates while keeping total 126.
  if (CATEGORIES.reduce((n, c) => n + groups[c].length, 0) < 126) {
    const shortfall = 126 - CATEGORIES.reduce((n, c) => n + groups[c].length, 0);
    const limits = { Java: TARGET + Math.ceil(shortfall / 2), 前端: TARGET, 运营: TARGET + Math.floor(shortfall / 2) };
    for (const post of socialPosts.values()) {
      if (used.has(post.PostId)) continue;
      const r = await request(`https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=${post.PostId}&language=zh-cn`, `tencent-social-detail-${post.PostId}`);
      if (r?.json?.Code === 200 && r.json.Data?.PostId) accept(mapSocial(r.json.Data, r.verification), limits);
      if (CATEGORIES.reduce((n, c) => n + groups[c].length, 0) >= 126) break;
      await delay(100);
    }
  }
  if (CATEGORIES.reduce((n, c) => n + groups[c].length, 0) < 126) {
    for (const post of socialPosts.values()) {
      if (used.has(post.PostId)) continue;
      const r = await request(`https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=${post.PostId}&language=zh-cn`, `tencent-social-detail-${post.PostId}`);
      if (r?.json?.Code === 200 && r.json.Data?.PostId) accept(mapSocial(r.json.Data, r.verification), { Java: 126, 前端: 126, 运营: 126 });
      if (CATEGORIES.reduce((n, c) => n + groups[c].length, 0) >= 126) break;
      await delay(100);
    }
  }
  const recruitmentJobs = CATEGORIES.flatMap(c => groups[c]);
  const uniqueChecks = [...new Map([...(prior?.sourceChecks || []), ...sourceChecks].map(c => [c.sourceId, c])).values()];
  for (const job of recruitmentJobs) if (!uniqueChecks.some(c => c.sourceId === job.verification.sourceId)) uniqueChecks.push(job.verification);
  const output = { schemaVersion: 1, generatedAt: now(), description: '新增官方公开岗位：原始详情核对，不改写单位、职位、任职要求或薪资；社会招聘仅作为岗位要求参考。', baselineJobs: prior?.baselineJobs || old.recruitmentJobs.map(j => ({ id: j.id, sourcePostId: j.sourcePostId, sourceUrl: j.sourceUrl })), target: { addedJobs: 126, byCategory: { Java: 42, 前端: 42, 运营: 42 } }, achieved: Object.fromEntries(CATEGORIES.map(c => [c, groups[c].length])), limitations: ['腾讯官方前端/全栈可核对且不与既有目录重复的岗位不足42条；保留实际分类，缺额以其他方向官方岗位补足，不将其他技术职位伪装成前端。', '社会招聘仅作岗位要求参考，未改写为校园招聘或实习。', '岗位原文中同时涉及多种语言或技术时，仅按原文记录技能，不表示Java为唯一或强制要求。', '腾讯校招旧职位HTML路由当前跳转404，新增校招采用可读取的官方详情JSON作为核对地址。', '公开职位状态可能随招聘平台变化；核对时间和原始响应hash保留于各记录。'], recruitmentJobs, sourceChecks: uniqueChecks };
  fs.writeFileSync(OUTPUT, JSON.stringify(output, null, 2) + '\n');
  console.log(JSON.stringify({ file: OUTPUT, count: recruitmentJobs.length, achieved: output.achieved, kinds: recruitmentJobs.reduce((a, j) => ({ ...a, [j.recruitmentKind]: (a[j.recruitmentKind] || 0) + 1 }), {}) }));
  if (recruitmentJobs.length !== 126) process.exitCode = 2;
}

if (require.main === module) collect().catch(e => { console.error(e); process.exitCode = 1; });
module.exports = { extractSkills, categoryOf, matchingOccurrence, mapSocial, mapCampus };
