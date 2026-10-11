const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { extractSkills, categoryOf, mapSocial, mapCampus } = require('./collect-public-jobs-expansion.cjs');
const current = require('./data/public-jobs-expansion-20261011.json');

test('exactly 126 additional official jobs with transparent actual classification', () => {
  assert.equal(current.recruitmentJobs.length, 126);
  const counts = current.recruitmentJobs.reduce((a, j) => (a[j.category] = (a[j.category] || 0) + 1, a), {});
  assert.deepEqual(counts, current.achieved);
  assert.deepEqual(counts, { Java: 51, 前端: 17, 运营: 58 });
  assert.ok(current.limitations.some(x => /不足42条/.test(x)));
  assert.match(current.generatedAt, /\+08:00$/);
});

test('no repeated ID, official position, URL, or original 42 positions', () => {
  for (const key of ['id', 'sourcePostId', 'sourceUrl']) {
    const values = current.recruitmentJobs.map(j => j[key]);
    assert.equal(new Set(values).size, values.length, key);
    // Compare with the recorded baseline; the master catalog may later include this expansion.
    const excluded = new Set(current.baselineJobs.map(j => j[key]));
    assert.ok(values.every(v => !excluded.has(v)), key);
  }
});

test('every skill quote and character offset points to exact original requirements', () => {
  for (const job of current.recruitmentJobs) {
    assert.equal(job.requirements, job.originalRequirements, job.id);
    assert.deepEqual(job.skills, job.skillsEvidence.map(e => e.skill), job.id);
    assert.ok(job.skills.length > 0, job.id);
    for (const e of job.skillsEvidence) {
      assert.equal(job.requirements.slice(e.start, e.end), e.excerpt, `${job.id}: ${e.skill}`);
      assert.ok(e.excerpt.includes(e.matchedText), `${job.id}: ${e.skill}`);
      assert.equal(e.matchedText.toLowerCase(), e.skill.toLowerCase(), `${job.id}: ${e.skill}`);
    }
  }
});

test('every job retains official verified response identity, integrity, and recruitment type', () => {
  const counts = {};
  for (const job of current.recruitmentJobs) {
    counts[job.recruitmentKind] = (counts[job.recruitmentKind] || 0) + 1;
    assert.ok(['SOCIAL', 'CAMPUS', 'INTERNSHIP'].includes(job.recruitmentKind));
    assert.match(job.queriedAt, /\+08:00$/);
    assert.equal(job.status, 'VERIFIED_PUBLIC');
    assert.equal(job.salary, '未公开');
    assert.equal(job.verification.httpStatus, 200);
    assert.ok(job.verification.contentBytes > 100);
    assert.match(job.verification.sha256, /^[a-f0-9]{64}$/);
    assert.equal(job.verification.readToEnd, true);
    assert.equal(new URL(job.sourceApiUrl).protocol, 'https:');
    assert.ok(['careers.tencent.com', 'join.qq.com'].includes(new URL(job.sourceApiUrl).hostname));
    const check = current.sourceChecks.find(c => c.sourceId === job.verification.sourceId);
    assert.ok(check, job.id);
    assert.equal(check.sha256, job.verification.sha256);
    if (job.recruitmentKind === 'SOCIAL') assert.match(job.recruitmentType, /社会招聘.*非校招/);
    else assert.match(job.recruitmentType, /校园招聘|实习/);
  }
  assert.deepEqual(counts, { SOCIAL: 108, CAMPUS: 6, INTERNSHIP: 12 });
});

test('saved source response bodies match hashes and verbatim title, description, requirements', t => {
  const files = current.recruitmentJobs.map(job => path.resolve(__dirname, '../logs/public-expansion', `${job.verification.sourceId}.json`));
  const missing = files.find(file => !fs.existsSync(file));
  if (missing) {
    if (process.env.REQUIRE_SOURCE_SNAPSHOTS === '1') assert.fail('missing source snapshot ' + missing);
    t.skip('source snapshots are not present; run with REQUIRE_SOURCE_SNAPSHOTS=1 after collection');
    return;
  }
  let checked = 0;
  for (const job of current.recruitmentJobs) {
    const file = path.resolve(__dirname, '../logs/public-expansion', `${job.verification.sourceId}.json`);
    const body = fs.readFileSync(file);
    assert.equal(body.length, job.verification.contentBytes, job.id);
    assert.equal(crypto.createHash('sha256').update(body).digest('hex'), job.verification.sha256, job.id);
    const json = JSON.parse(body.toString('utf8'));
    const data = job.recruitmentKind === 'SOCIAL' ? json.Data : json.data;
    assert.equal(job.title, data.RecruitPostName ?? data.title, job.id);
    assert.equal(job.description, data.Responsibility ?? data.desc, job.id);
    assert.equal(job.requirements, data.Requirement ?? data.request, job.id);
    if (job.recruitmentKind === 'SOCIAL') {
      assert.equal(job.company, data.ComName || '腾讯', job.id);
      assert.equal(job.city, data.LocationName || '未公开', job.id);
      assert.equal(job.workExperience, data.RequireWorkYearsName || '未公开', job.id);
    } else {
      assert.deepEqual(job.cities, data.workCityList || [], job.id);
      assert.equal(job.recruitmentKind, /实习/.test(data.recruitLabelName || data.projectName || '') ? 'INTERNSHIP' : 'CAMPUS', job.id);
    }
    checked++;
  }
  assert.equal(checked, 126);
});

test('JavaScript, MySQL, Django, or ordinary words do not create Java/SQL/Go aliases', () => {
  const skills = extractSkills('JavaScript；MySQL；Django；Google；Go语言，学习java，SQL，C++').map(e => e.skill);
  assert.ok(skills.includes('JavaScript'));
  assert.ok(skills.includes('Java'));
  assert.ok(skills.includes('MySQL'));
  assert.ok(skills.includes('SQL'));
  assert.ok(skills.includes('Go'));
  assert.deepEqual(extractSkills('JavaScript；MySQL；Django；Google').map(e => e.skill), ['MySQL', 'JavaScript']);
});

test('classification uses actual requirements and excludes infrastructure ops and browser algorithms', () => {
  assert.equal(categoryOf('前端开发工程师', '熟悉React与JavaScript'), '前端');
  assert.equal(categoryOf('后台开发', '熟悉Java、SQL'), 'Java');
  assert.equal(categoryOf('用户运营', '熟悉数据分析'), '运营');
  assert.equal(categoryOf('AI ⽹络集合通信库运营⼯程师', '熟悉Linux'), null);
  assert.equal(categoryOf('浏览器数据后处理算法工程师', '熟悉JavaScript'), null);
  assert.equal(categoryOf('后台开发', '熟悉JavaScript'), null);
  for (const job of current.recruitmentJobs) assert.equal(categoryOf(job.title, job.requirements), job.category, job.id);
});

test('social vacancies keep unknown salary, experience, and contract company', () => {
  const job = mapSocial({ PostId: '1', RecruitPostName: '后台开发', Requirement: 'Java', Responsibility: '服务研发', ComName: '', RequireWorkYearsName: '' }, { url: 'https://careers.tencent.com/details', queriedAt: '2026-10-11T12:00:00+08:00' });
  assert.equal(job.recruitmentKind, 'SOCIAL');
  assert.equal(job.company, '腾讯');
  assert.equal(job.workExperience, '未公开');
  assert.equal(job.salary, '未公开');
});

test('campus and internship are determined by official project labels without invented time or salary', () => {
  const data = { postId: '2', title: '前端开发', request: 'HTML CSS', desc: '网页开发', workCityList: ['北京', '深圳总部'], projectName: '日常实习', recruitLabelName: '日常实习' };
  const job = mapCampus(data, { url: 'https://join.qq.com/api/position/2', queriedAt: '2026-10-11T12:00:00+08:00' }, []);
  assert.equal(job.recruitmentKind, 'INTERNSHIP');
  assert.equal(job.city, '北京、深圳总部');
  assert.equal(job.workExperience, '未公开');
  assert.equal(job.salary, '未公开');
  assert.equal(job.requirements, data.request);
});
