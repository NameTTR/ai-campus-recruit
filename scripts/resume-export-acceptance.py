"""Live resume workspace acceptance against the existing Docker gateway.
Requires requests, python-docx, pypdf, Pillow, PyMuPDF. Uses dedicated synthetic accounts.
Never logs tokens, signed URLs or original personal resume material.
"""
from __future__ import annotations
import argparse, hashlib, io, json, os, re, subprocess, time, uuid, zipfile
from pathlib import Path
import requests
from PIL import Image, ImageDraw
import fitz
from pypdf import PdfReader
from docx import Document

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output/playwright/resume-acceptance"
OUT.mkdir(parents=True, exist_ok=True)
BASE = os.getenv("RESUME_ACCEPTANCE_API", "http://localhost:18080").rstrip("/")
PASSWORD = os.getenv("RESUME_ACCEPTANCE_PASSWORD", "ResumeVerify123!")
REPORT = {"cases": [], "checks": [], "baseUrl": BASE}
PHOTO_SHA = None

def verify_pdf_geometry(path):
    pages=[]
    with fitz.open(path) as pdf:
        for page in pdf:
            lines=[];chars=[];white_spans=[]
            for block in page.get_text('rawdict')['blocks']:
                for line in block.get('lines',[]):
                    nonempty=[]
                    for span in line['spans']:
                        span_chars=[c for c in span['chars'] if not c['c'].isspace()]
                        chars.extend(span_chars);nonempty.extend(span_chars)
                        if span['color']==0xFFFFFF and span_chars:white_spans.append(span)
                    if nonempty:lines.append(nonempty)
            assert chars, f'{path.name}: blank page {page.number+1}'
            for char in chars:
                x0,y0,x1,y1=char['bbox']
                assert x0>=38 and x1<=page.rect.width-38 and y0>=30 and y1<=page.rect.height-35, f'{path.name}: text clips page margins on page {page.number+1}'
            # Font ascent/descent boxes slightly overlap within one line. Only
            # compare different baselines, using non-space character geometry.
            for i,first in enumerate(lines):
                for second in lines[i+1:]:
                    for a in first:
                        for b in second:
                            if abs(a['origin'][1]-b['origin'][1])<2:continue
                            x_overlap=min(a['bbox'][2],b['bbox'][2])-max(a['bbox'][0],b['bbox'][0])
                            y_overlap=min(a['bbox'][3],b['bbox'][3])-max(a['bbox'][1],b['bbox'][1])
                            assert not (x_overlap>1 and y_overlap>1), f'{path.name}: text lines overlap on page {page.number+1}'
            images=page.get_image_info()
            for image in images:
                image_box=fitz.Rect(image['bbox'])
                for char in chars:
                    intersection=image_box & fitz.Rect(char['bbox'])
                    assert intersection.is_empty or intersection.width<=1 or intersection.height<=1, f'{path.name}: image overlaps text on page {page.number+1}'
            if path.name.startswith('T05-') and page.number==0:
                assert white_spans, f'{path.name}: navy header lost its white text'
                pix=page.get_pixmap(matrix=fitz.Matrix(2,2),alpha=False)
                image=Image.frombytes('RGB',(pix.width,pix.height),pix.samples)
                for span in white_spans:
                    x0,y0,x1,y1=span['bbox']
                    rgb=image.getpixel((max(0,int((x0-2)*2)),int((y0+y1))))
                    assert max(rgb)<190, f'{path.name}: white header text is outside its navy background'
            pages.append({'page':page.number+1,'characters':len(chars),'lines':len(lines),'images':len(images),
                          'bounds':[round(min(c['bbox'][0] for c in chars),2),round(min(c['bbox'][1] for c in chars),2),round(max(c['bbox'][2] for c in chars),2),round(max(c['bbox'][3] for c in chars),2)],
                          'whiteHeaderSpans':len(white_spans),'overlapCount':0,'clippedCharacters':0})
    return {'file':path.name,'pages':pages,'passed':True}

def verify_existing_exports():
    acceptance=json.loads((ROOT/'logs/resume-export-acceptance.json').read_text(encoding='utf-8'))
    expected=[case['label'] for case in acceptance['cases'] if case['status']=='SUCCEEDED']
    if 'Docker重启主资料/草稿/未完成导出恢复' in acceptance.get('checks',[]):expected.append('restart-recovered')
    report={'source':'Docker LibreOffice exports','cases':[],'passed':False}
    report_path=ROOT/'output/playwright/resume-fix-export-geometry.json'
    try:
        for label in expected:
            path=OUT/(label+'.pdf')
            assert path.is_file(),f'Missing exported PDF: {path.name}'
            report['cases'].append(verify_pdf_geometry(path))
        # All eight themes plus both pages of the two detailed templates.
        selected=[(f'T{i:02d}-short-photo',0) for i in range(1,9)]
        selected += [(f'{tid}-long-photo',page) for tid in ('T07','T08') for page in (0,1)]
        contact=Image.new('RGB',(1440,1620),'#e5e7eb');draw=ImageDraw.Draw(contact)
        render_out=ROOT/'output/playwright/resume-fix-export-pages';render_out.mkdir(exist_ok=True)
        for index,(label,page_number) in enumerate(selected):
            with fitz.open(OUT/(label+'.pdf')) as pdf:
                page=pdf[page_number];pix=page.get_pixmap(matrix=fitz.Matrix(1.5,1.5),alpha=False)
                pix.save(render_out/(label+f'-page-{page_number+1}.png'))
                image=Image.frombytes('RGB',(pix.width,pix.height),pix.samples)
                image.thumbnail((340,500))
                x=(index%4)*360+10;y=(index//4)*540+25
                contact.paste(image,(x,y));draw.text((x,y-18),f'{label} / page {page_number+1}',fill='#111827')
        contact_path=ROOT/'output/playwright/resume-fix-export-contact.png';contact.save(contact_path)
        report['contactSheet']=str(contact_path.relative_to(ROOT)).replace('\\','/')
        report['passed']=True
    finally:
        report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'Geometry verified: {len(report["cases"])} PDFs; report: {report_path}',flush=True)

def call(method, path, token=None, body=None, files=None, expected=200):
    headers = {"Authorization": "Bearer " + token} if token else {}
    r = requests.request(method, BASE + path, headers=headers, json=body if files is None else None, files=files, timeout=190)
    if r.status_code != expected: raise AssertionError(f"{method} {path}: expected HTTP {expected}, got {r.status_code}: {r.text[:200]}")
    data = r.json()
    if expected >= 400: return data
    if data.get("code") != 0: raise AssertionError(f"{method} {path}: {data.get('message')}")
    return data.get("data")

def account(role):
    username = "resume_" + role.lower() + "_" + uuid.uuid4().hex[:10]
    auth = call("POST", "/api/auth/register", body={"username": username, "password": PASSWORD, "displayName": "简历验收" + role, "role": role})
    return username, auth

def source(id, quote): return {"kind": "STUDENT", "sourceId": id, "quote": quote, "confirmed": True, "assessment": "合成验收资料，学生确认"}

def profile(photo=None):
    experiences=[]
    for i in range(4):
        title=["课程选课接口", "前端课程作品", "校园活动复盘", "课程项目测试"][i]
        action=["使用 Java 实现课程列表、选课和取消接口，个人负责请求校验与接口测试。", "使用 Vue 和 JavaScript 实现课程查询页面，个人负责筛选交互和表单校验。", "使用 Excel 整理校园活动报名记录，个人负责报名表检查和活动复盘。", "使用 Java 编写课程接口测试，个人负责输入边界和重复请求场景验证。"][i]
        method="逐项核对输入、处理过程和实际结果，记录异常情况并修正已有实现。" + "通过课程要求和现有材料对照检查，保留过程说明及验证记录。" * 3
        result="完成课程验收并提交作品说明，实际结果和个人职责可在提交记录中核对。" + "复盘发现的问题并说明处理方式，没有测得的数据不写提升比例。" * 2
        experiences.append({"id":f"exp{i}","type":"CAMPUS" if i==2 else "PROJECT","title":title,"organization":"校内课程与社团","startDate":"2025-09","endDate":"2026-06","role":"学生成员","actions":action,"methods":method,"results":result,"skills":[["Java"],["Vue","JavaScript"],["Excel"],["Java"]][i],"links":["https://example.test/作品"],"source":source(f"exp{i}",action + method + result),"confirmed":True})
    return {"basics":{"name":"李验收","phone":"13800000000","email":"resume@example.test","city":"上海","portfolioUrl":"https://example.test/portfolio","photoObjectKey":photo},
            "education":[{"id":"edu1","school":"合成样例大学","major":"计算机科学","degree":"本科","startDate":"2023-09","endDate":"2027-06","graduationDate":"2027-06-30","courses":["数据结构","软件工程"],"notes":"完成课程项目与校园实践","source":source("edu1","合成样例大学 计算机科学 本科")},
                         {"id":"edu2","school":"合成样例交流课程","major":"运营实践","degree":"课程学习","startDate":"2025-09","endDate":"2025-12","graduationDate":"","courses":[],"notes":"完成校园活动复盘","source":source("edu2","合成样例交流课程 运营实践")}],
            "skills":[{"id":"skill"+str(i),"name":v,"source":source("skill"+str(i),v)} for i,v in enumerate(["Java","Vue","JavaScript","Excel"])],
            "experiences":experiences,"credentials":[{"id":"cert1","title":"校园课程实践证明","date":"2026-06","description":"完成课程要求并提交作品","source":source("cert1","校园课程实践证明")}],
            "availability":{"cities":["上海"],"earliestStartDate":"2026-11-01","daysPerWeek":4,"continuousMonths":6,"graduationDate":"2027-06-30"}}

def update(token, draft, data, template=None):
    return call("PATCH",f"/api/resumes/drafts/{draft['id']}",token,{"expectedRevision":draft['revision'],"templateId":template or draft['templateId'],"data":data,"confirm":True})

def export(token,draft):
    req={"expectedRevision":draft['revision']}
    task=call("POST",f"/api/resumes/drafts/{draft['id']}/exports",token,req)
    again=call("POST",f"/api/resumes/drafts/{draft['id']}/exports",token,req)
    assert task['id']==again['id'], "Duplicate export must reuse task"
    deadline=time.monotonic()+130
    while task['status'] in ('QUEUED','RUNNING') and time.monotonic()<deadline:
        time.sleep(.5);task=call("GET",f"/api/resumes/exports/{task['id']}",token)
    assert task['status'] in ('SUCCEEDED','NEEDS_EDIT'), f"Export failed: {task.get('error')}"
    return task

def verify_files(task,label,photo):
    paths={}
    for ext in ('docx','pdf'):
        file=task[ext];r=requests.get(file['url'],timeout=45);r.raise_for_status()
        assert hashlib.sha256(r.content).hexdigest()==file['sha256'], "Hash mismatch"
        assert 'minio:' not in file['url'], "Docker-only host in browser URL"
        path=OUT/f"{label}.{ext}";path.write_bytes(r.content);paths[ext]=path
    pdf=PdfReader(paths['pdf']);text='\n'.join(p.extract_text() or '' for p in pdf.pages)
    assert '李验收' in text and '合成样例大学' in text, "Chinese PDF text missing"
    assert all((p.extract_text() or '').strip() for p in pdf.pages), "Blank PDF page"
    assert not any(v in text for v in ['待填写','张三','李四','????????','工作经验三年','99%']), "Placeholder/sample facts in export"
    with zipfile.ZipFile(paths['docx']) as z:
        xml=z.read('word/document.xml').decode('utf-8')
        assert '李验收' in xml
        sizes=[int(v) for v in re.findall(r'<w:sz(?:Cs)? w:val="(\d+)"',xml)]
        assert all(v>=20 for v in sizes), "Body font below 10pt"
        uploaded_photo=any(hashlib.sha256(z.read(name)).hexdigest()==PHOTO_SHA for name in z.namelist() if name.startswith('word/media/'))
        assert uploaded_photo==photo, 'Uploaded photo presence mismatch (decorations are separate)'
    geometry=verify_pdf_geometry(paths['pdf'])
    return {"pages":len(pdf.pages),"docxBytes":paths['docx'].stat().st_size,"pdfBytes":paths['pdf'].stat().st_size,"textLength":len(text),'geometryPassed':geometry['passed']}

def main(restart):
    global PHOTO_SHA
    username,student=account('STUDENT');_,other=account('STUDENT');_,company=account('COMPANY')
    token=student['token'];foreign=other['token']
    REPORT['studentUsername']=username
    roles=[('Java 开发实习生',['Java','Redis'],'要求 Java；Redis 实践优先'),('前端开发实习生',['Vue','JavaScript','TypeScript'],'要求 Vue 和 JavaScript；TypeScript 优先'),('运营实习生',['Excel','数据分析'],'要求 Excel；数据分析优先')]
    jobs=[]
    for title,skills,desc in roles:
        jobs.append(call('POST','/api/jobs',company['token'],{'companyId':company['userId'],'title':title,'city':'上海','salaryRange':'面议','requiredSkills':skills,'description':desc+'；本科及以上；2027届毕业生；每周至少4天；连续6个月；2026-11-15到岗'}))
    master=call('GET','/api/resumes/master-profile',token)
    empty=call('POST','/api/resumes/drafts',token,{'templateId':'T01','targetRole':'通用岗位','profileRevision':master['revision']})
    assert not empty['data']['blocks'];REPORT['checks'].append('空资料无虚假内容')
    all_drafts=[];ai_sources=[]
    for photo in (False,True):
        photoKey=None
        if photo:
            image=Image.new('RGB',(160,210),'#dbeafe');draw=ImageDraw.Draw(image);draw.ellipse((50,25,110,90),fill='#475569');draw.rectangle((35,105,125,205),fill='#64748b');b=io.BytesIO();image.save(b,format='PNG')
            PHOTO_SHA=hashlib.sha256(b.getvalue()).hexdigest()
            asset=call('POST','/api/resumes/master-profile/photo',token,files={'file':('student.png',b.getvalue(),'image/png')});photoKey=asset['objectKey']
        old=call('GET','/api/resumes/master-profile',token)
        master=call('PUT','/api/resumes/master-profile',token,{'expectedRevision':old['revision'],'data':profile(photoKey),'confirmed':True})
        call('PUT','/api/resumes/master-profile',token,{'expectedRevision':old['revision'],'data':profile(photoKey),'confirmed':True},expected=409)
        assert len(master['data']['education'])==2 and len(master['data']['experiences'])==4
        for ti in range(1,9):
            tid=f'T{ti:02d}'
            draft=call('POST','/api/resumes/drafts',token,{'templateId':tid,'jobId':jobs[0]['jobId'],'targetRole':'untrusted-title','profileRevision':master['revision']})
            assert draft['jobSnapshot']['jobId']==jobs[0]['jobId']
            assert draft['targetRole']==jobs[0]['title']
            ai_sources.append(draft['data']['generationSource'])
            full=json.loads(json.dumps(draft['data']))
            short=json.loads(json.dumps(full))
            for block in short['blocks']:
                if block['type'] in ('PROJECT','CAMPUS','INTERNSHIP','EXPERIENCE'):
                    for i,entry in enumerate(block['entries']):
                        entry['visible']=i==0 and block['type']=='PROJECT'
                        entry['bullets']=entry['bullets'][:1]
            for length,data in [('short',short),('long',full)]:
                draft=update(token,draft,data)
                task=export(token,draft);label=f"{tid}-{length}-{'photo' if photo else 'no-photo'}"
                result={'label':label,'status':task['status'],'pages':task['pageCount']}
                if task['status']=='SUCCEEDED':result.update(verify_files(task,label,photo))
                else:
                    assert task['layoutIssues'] and not task.get('docx') and not task.get('pdf')
                    assert ti<=6 or length=='long', 'Short two-page template should render'
                REPORT['cases'].append(result);print(label,task['status'],task['pageCount'],flush=True)
                call('GET',f"/api/resumes/exports/{task['id']}",foreign,expected=404)
            all_drafts.append(draft)
    # Actual role-specific AI organization; template switches and exports above do not request extra model calls.
    for job in jobs[1:]:
        roleDraft=call('POST','/api/resumes/drafts',token,{'templateId':'T01','jobId':job['jobId'],'profileRevision':master['revision']})
        ai_sources.append(roleDraft['data']['generationSource'])
    assert all(s.startswith('AI_DASHSCOPE:') for s in ai_sources), f"Real provider not used: {sorted(set(ai_sources))}"
    REPORT['checks'].append('Java/前端/运营真实DashScope组织')
    chosen=all_drafts[-1]
    compared=call('POST','/api/matches/compare',token,{'resumeId':chosen['resumeId'],'jobIds':[j['jobId'] for j in jobs]})
    assert len(compared['jobs'])==3 and all(j['match']['details'] for j in compared['jobs'])
    for j in compared['jobs']:
        assert all(c['status'] in ('SATISFIED','NOT_SATISFIED','UNKNOWN') for c in j['conditions'])
    REPORT['checks'].append('三岗位同一简历规则比较')
    call('GET',f"/api/resumes/drafts/{chosen['id']}",foreign,expected=404)
    revisions=call('GET',f"/api/resumes/drafts/{chosen['id']}/revisions",token)
    restored=call('POST',f"/api/resumes/drafts/{chosen['id']}/restore",token,{'expectedRevision':chosen['revision'],'revision':revisions[-2]['revision']})
    assert restored['revision']>chosen['revision']
    assert len(call('GET',f"/api/resumes/drafts/{chosen['id']}/revisions",token))>len(revisions)
    REPORT['checks'].append('所有权/冲突/历史恢复/重复导出')
    if restart:
        pending=call('POST',f"/api/resumes/drafts/{restored['id']}/exports",token,{'expectedRevision':restored['revision']})
        subprocess.run(['docker','restart','recruit-resume-service'],check=True,stdout=subprocess.DEVNULL)
        end=time.monotonic()+120
        while time.monotonic()<end:
            try:
                latest=call('GET',f"/api/resumes/exports/{pending['id']}",token)
                if latest['status'] not in ('QUEUED','RUNNING'):break
            except (requests.RequestException,AssertionError):pass
            time.sleep(1)
        assert latest['status']=='SUCCEEDED';verify_files(latest,'restart-recovered',True)
        retained=call('GET','/api/resumes/master-profile',token)
        assert retained['revision']==master['revision'] and len(retained['data']['experiences'])==4
        REPORT['checks'].append('Docker重启主资料/草稿/未完成导出恢复')
    REPORT['aiSources']=sorted(set(ai_sources))
    REPORT['fixture']={'studentUsername':username,'resumeId':restored['resumeId'],'draftId':restored['id'],'jobIds':[j['jobId'] for j in jobs]}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--restart',action='store_true');parser.add_argument('--verify-existing',action='store_true');args=parser.parse_args()
    if args.verify_existing:
        verify_existing_exports();raise SystemExit(0)
    try:main(args.restart);REPORT['passed']=True
    except Exception as ex:REPORT['passed']=False;REPORT['failure']=str(ex);raise
    finally:
        path=ROOT/'logs/resume-export-acceptance.json';path.write_text(json.dumps(REPORT,ensure_ascii=False,indent=2),encoding='utf-8')
        print(f"Report: {path}",flush=True)
