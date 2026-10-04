#!/usr/bin/env python3
"""Adapt the eight supplied templates without copying sample facts or photographs.

Only source vector decorations are retained. Personal body text is reflowed by the
Java renderer. Conversion is a build-time operation; runtime never reads jianli.
Use --decoration-pdf-dir to reuse sanitized source PDFs, or --soffice to convert.
The optional --write-inventory refreshes the large archived-source documentation.
"""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, subprocess, tempfile, zipfile
from copy import deepcopy
from pathlib import Path
import fitz
from lxml import etree
from docx import Document
from docx.shared import Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / "jianli" / "5.高品质简历 400+"
OUT = ROOT / "backend/resume-service/src/main/resources/resume-templates"
DOC_OUT = OUT / "base"
CAT_OUT = ROOT / "docs/resume-template-catalog.json"
SPECS = [
    ("T01", "通用简洁", "单页", "1 (24).docx", "通用岗位", ["通用", "校园招聘"], 1, "556F7C"),
    ("T02", "应届生实践", "应届生简历", "1 (17).docx", "校园与实践", ["通用", "应届生"], 1, "474747"),
    ("T03", "技术条纹", "按职业分类简历/软件", "1 (1).docx", "技术岗位", ["Java", "后端", "技术"], 1, "556F7C"),
    ("T04", "前端项目", "按职业分类简历/软件", "1 (3).docx", "前端项目", ["前端", "Web", "技术"], 1, "3B8CB8"),
    ("T05", "Java 项目", "按职业分类简历/软件", "1 (5).docx", "Java 项目", ["Java", "后端", "技术"], 1, "1F4E79"),
    ("T06", "运营双栏", "按职业分类简历/网络 网管 运营", "1 (10).docx", "运营与数据", ["运营", "内容运营", "产品运营"], 1, "9DC393"),
    ("T07", "浅蓝详版", "双页", "1 (15).docx", "多段经历", ["通用", "技术", "运营"], 2, "B4C7E7"),
    ("T08", "橙色详版", "双页", "1 (23).docx", "详细项目与实践", ["通用", "技术", "运营"], 2, "C3692A"),
]
NS = {'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main',
      'a':'http://schemas.openxmlformats.org/drawingml/2006/main',
      'v':'urn:schemas-microsoft-com:vml',
      'r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}


def sha256(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''): h.update(chunk)
    return h.hexdigest()


def sanitize_for_vector_conversion(src, dst):
    """Never package source DOCX internals: this copy is temporary and only vectors are extracted."""
    with zipfile.ZipFile(src) as source, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as target:
        for name in source.namelist():
            data = source.read(name)
            if name.endswith('.xml'):
                try:
                    root = etree.fromstring(data)
                    for text in root.xpath('//w:t|//a:t', namespaces=NS): text.text = ''
                    # Pictures (including example photos) and all numbering disappear.
                    for element in list(root.xpath('//a:blip|//v:imagedata|//w:numPr', namespaces=NS)):
                        element.getparent().remove(element)
                    data = etree.tostring(root, xml_declaration=True, encoding='UTF-8')
                except etree.XMLSyntaxError: pass
            target.writestr(name, data)


def replay(page, drawings):
    """Copy source PDF path commands and exact colors; no recreated approximation."""
    for d in drawings:
        shape = page.new_shape()
        for item in d['items']:
            if item[0] == 'l': shape.draw_line(item[1], item[2])
            elif item[0] == 'c': shape.draw_bezier(item[1], item[2], item[3], item[4])
            elif item[0] == 're': shape.draw_rect(item[1])
            elif item[0] == 'qu': shape.draw_quad(item[1])
            else: raise ValueError('unsupported source vector operation: ' + item[0])
        shape.finish(width=d.get('width') or 1, color=d.get('color'), fill=d.get('fill'),
                     lineCap=max(d.get('lineCap') or (0,)), lineJoin=d.get('lineJoin') or 0,
                     dashes=d.get('dashes'), closePath=d.get('closePath', False),
                     even_odd=d.get('even_odd', False),
                     fill_opacity=d.get('fill_opacity') if d.get('fill_opacity') is not None else 1, stroke_opacity=d.get('stroke_opacity') if d.get('stroke_opacity') is not None else 1)
        shape.commit()


def save_vectors(drawings, path, clip=None):
    with fitz.open() as pdf:
        page = pdf.new_page(width=595.3, height=841.89)
        replay(page, drawings)
        page.get_pixmap(matrix=fitz.Matrix(2.5, 2.5), clip=clip, alpha=True).save(path)


def source_decorations(tid, pdf_file, target):
    target.mkdir(parents=True, exist_ok=True)
    with fitz.open(pdf_file) as pdf:
        all_drawings = pdf[0].get_drawings()
        if tid == 'T01': chosen = [d for d in all_drawings if d['rect'].y1 < 110]
        elif tid == 'T02': chosen = [d for d in all_drawings if d['rect'].x1 < 30]
        elif tid == 'T03': chosen = all_drawings
        elif tid == 'T04': chosen = [d for d in all_drawings if d['rect'].width > 590 and d['rect'].y1 < 150]
        elif tid == 'T05': chosen = [d for d in all_drawings if d['rect'].width > 590 and d['rect'].y1 < 120]
        elif tid == 'T06': chosen = [d for d in all_drawings if (d['rect'].height > 600 and 170 < d['rect'].width < 185) or (d['rect'].y0 < 1 and d['rect'].y1 < 230 and d['rect'].width > 175)]
        elif tid == 'T07': chosen = [d for d in all_drawings if (d['rect'].width > 250 and d['rect'].y1 < 180) or (d['rect'].y1 < 35 and 15 < d['rect'].width < 25) or (d['rect'].width < 1 and d['rect'].height > 700)]
        else: chosen = [d for d in all_drawings if d['rect'].y1 < 25]
        if tid in ('T07', 'T08'):
            chosen += [d for d in pdf[-1].get_drawings() if d['rect'].y0 > 805]
        save_vectors(chosen, target / 'background.png')
        clips = {}
        if tid == 'T01': clips['section'] = fitz.Rect(89, 251, 523, 264)
        if tid == 'T02':
            for label, y in [('education', 171.8), ('experience', 276.6), ('skills', 491.3), ('credential', 631.9)]:
                clips[label] = fitz.Rect(34, y - 1, 68, y + 25)
        if tid == 'T05':
            for label, y in [('education', 144), ('experience', 269.2), ('skills', 494.8), ('credential', 746.1)]:
                clips[label] = fitz.Rect(30.5, y - 1, 65, y + 34)
        if tid == 'T06': clips['section'] = fitz.Rect(252.5, 229.3, 533, 257.1)
        if tid == 'T07': clips['section'] = fitz.Rect(297, 68.1, 374.5, 76.8)
        if tid == 'T08':
            clips['section'] = fitz.Rect(29, 181.6, 141.5, 186.4)
            clips['header-accent'] = fitz.Rect(0, 213.8, 595.3, 230)
        for key, clip in clips.items():
            subset = [d for d in all_drawings if d['rect'].intersects(clip) and d['rect'].width < 590]
            save_vectors(subset, target / (key + '.png'), clip)
        return {'method':'source-pdf-vector-paths', 'sourcePdfSha256':sha256(pdf_file),
                'backgroundPathCount':len(chosen), 'decorations':[p.name for p in sorted(target.glob('*.png'))]}


def anchor_background(paragraph, image):
    inline = paragraph.add_run().add_picture(str(image), width=Pt(595.3), height=Pt(841.89))._inline
    anchor = OxmlElement('wp:anchor')
    for key, value in {'distT':'0','distB':'0','distL':'0','distR':'0','simplePos':'0','relativeHeight':'0',
                       'behindDoc':'1','locked':'0','layoutInCell':'0','allowOverlap':'1'}.items(): anchor.set(key, value)
    simple = OxmlElement('wp:simplePos'); simple.set('x','0'); simple.set('y','0'); anchor.append(simple)
    for tag in ('positionH', 'positionV'):
        pos = OxmlElement('wp:'+tag); pos.set('relativeFrom', 'page')
        offset = OxmlElement('wp:posOffset'); offset.text = '0'; pos.append(offset); anchor.append(pos)
    anchor.append(deepcopy(inline.find(qn('wp:extent'))))
    anchor.append(OxmlElement('wp:wrapNone'))
    for tag in ('docPr', 'cNvGraphicFramePr'):
        element = inline.find(qn('wp:'+tag))
        if element is not None: anchor.append(deepcopy(element))
    docpr = anchor.find(qn('wp:docPr')); docpr.set('descr','Source-derived decoration; no personal data')
    anchor.append(deepcopy(inline.find(qn('a:graphic'))))
    inline.getparent().replace(inline, anchor)


def make_doc(path, spec, decoration):
    d = Document(); sec = d.sections[0]
    sec.page_width = Pt(595.3); sec.page_height = Pt(841.89)
    sec.top_margin = Pt(40); sec.bottom_margin = Pt(35); sec.left_margin = Pt(40); sec.right_margin = Pt(40)
    sec.header_distance = Pt(0); sec.footer_distance = Pt(0)
    normal = d.styles['Normal']; normal.font.name = 'Noto Sans CJK SC'; normal.font.size = Pt(10)
    normal._element.rPr.rFonts.set(qn('w:eastAsia'),'Noto Sans CJK SC')
    normal.paragraph_format.space_after = Pt(0)
    anchor_background(sec.header.paragraphs[0], decoration / 'background.png')
    for paragraph in sec.header.paragraphs:
        paragraph.paragraph_format.space_after = Pt(0); paragraph.paragraph_format.space_before = Pt(0)
    props = d.core_properties
    props.author = 'AI Campus Recruit'; props.last_modified_by = 'AI Campus Recruit'
    props.title = 'Adapted resume ' + spec[0]; props.subject = 'Source decorations with editable reflowing body'
    props.keywords = 'resume, source-derived'; props.comments = ''; props.category = ''
    d.save(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--decoration-pdf-dir', type=Path)
    parser.add_argument('--soffice', default=os.environ.get('RESUME_SOFFICE_PATH') or shutil.which('soffice') or 'C:/Program Files/LibreOffice/program/soffice.com')
    parser.add_argument('--write-inventory', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True); DOC_OUT.mkdir(exist_ok=True)
    entries = []; provenance = []
    with tempfile.TemporaryDirectory(prefix='resume-source-') as temp:
        work = Path(temp); pdf_dir = args.decoration_pdf_dir or work
        if args.decoration_pdf_dir is None:
            for spec in SPECS: sanitize_for_vector_conversion(ARCHIVE/spec[2]/spec[3], work/(spec[0]+'.docx'))
            subprocess.run([args.soffice, '-env:UserInstallation='+ (work/'lo-profile').as_uri(), '--headless', '--convert-to','pdf','--outdir',str(work),*[str(work/(s[0]+'.docx')) for s in SPECS]],check=True,timeout=180)
        for spec in SPECS:
            tid,name,cat,source,target,roles,max_pages,color = spec
            src=ARCHIVE/cat/source; dst=DOC_OUT/(tid+'.docx'); decorations=OUT/'decorations'/tid
            extracted=source_decorations(tid,pdf_dir/(tid+'.pdf'),decorations); make_doc(dst,spec,decorations)
            entries.append({'id':tid,'name':name,'category':cat,'target':target,'roleHints':roles,'maxPages':max_pages,'version':'1.2.0','sourceId':f'{cat}/{source}','sourceSha256':sha256(src),'adaptedSha256':sha256(dst),'status':'ENABLED','previewUrl':f'/resume-templates/{tid}.png','font':'Microsoft YaHei','sampleDataRemoved':True,'photoDefault':'hidden'})
            provenance.append({'id':tid,'sourceId':f'{cat}/{source}','sourceSha256':sha256(src),'exactSourceColor':color,**extracted})
    (OUT/'catalog.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (OUT/'decoration-provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if args.write_inventory:
        selected={e['sourceId'] for e in entries}; inventory=[]
        for f in sorted(ARCHIVE.rglob('*')):
            if f.is_file():
                rel=f.relative_to(ARCHIVE).as_posix()
                inventory.append({'path':rel,'category':f.parent.relative_to(ARCHIVE).as_posix(),'extension':f.suffix.lower().lstrip('.'),'bytes':f.stat().st_size,'sha256':sha256(f),'status':'ENABLED_SOURCE' if rel in selected else 'ARCHIVED_SOURCE'})
        catalog={'archive':'jianli/5.高品质简历 400+','archivePreserved':True,'archiveTracked':False,'archiveBytes':sum(x['bytes'] for x in inventory),'archiveFileCount':len(inventory),'totalEnabled':len(entries),'templates':entries,'archiveInventory':inventory}
        CAT_OUT.parent.mkdir(parents=True,exist_ok=True); CAT_OUT.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Generated {len(entries)} source-derived, personal-data-free adapted bases')
if __name__=='__main__': main()
