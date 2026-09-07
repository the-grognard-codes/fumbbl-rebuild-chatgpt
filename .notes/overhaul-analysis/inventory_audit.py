"""Read-only tracked-source/asset audit. Outputs only into this report directory."""
from pathlib import Path
from collections import Counter, defaultdict
import csv, hashlib, io, json, re, subprocess, zipfile, xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ROOT = OUT.parent.parent
FILES = [p for p in subprocess.check_output(['git','ls-files','-z'], cwd=ROOT).decode('utf-8').split('\0') if p and not p.startswith('.notes/')]
MEDIA = {'.png','.gif','.jpg','.jpeg','.webp','.bmp','.svg','.ico','.ttf','.otf','.woff','.woff2','.ogg','.wav','.mp3','.flac','.zip'}
RASTER = {'.png','.gif','.jpg','.jpeg','.webp','.bmp','.ico'}
TEXT = {'.java','.ini','.xml','.properties','.md','.txt','.yml','.yaml','.jnlp'}
(OUT/'assets').mkdir(parents=True, exist_ok=True)
(OUT/'evidence').mkdir(exist_ok=True)

def writecsv(name, rows, fields=None):
    if fields is None: fields = list(rows[0]) if rows else ['empty']
    with (OUT/name).open('w', newline='', encoding='utf-8') as f:
        w=csv.DictWriter(f, fieldnames=fields); w.writeheader(); w.writerows(rows)

texts = {}
for p in FILES:
    if Path(p).suffix.lower() in TEXT and '/repo/' not in p:
        texts[p] = (ROOT/p).read_text(encoding='utf-8', errors='replace')

def category(p):
    q=p.lower()
    if '!' in q: return 'pitches-archive' if 'pitch' in q else 'archive-other'
    if '/cached/players/iconsets/' in q: return 'player-sheets'
    if '/cached/players/portraits/' in q: return 'portraits'
    if '/cached/' in q: return 'cached-other'
    if '/icons/' in q: return q.split('/icons/',1)[1].split('/')[0]
    if '/sounds/' in q: return 'sounds'
    return 'other'

rows=[]; blobs={}; archive_entries=[]
def inspect(p, data, storage='file', compressed_bytes=''):
    ext=Path(p).suffix.lower()
    row=dict(path=p,storage=storage,category=category(p),extension=ext,bytes=len(data),compressed_bytes=compressed_bytes,
             sha256=hashlib.sha256(data).hexdigest(),format='',width='',height='',mode='',has_alpha_channel='',has_transparent_pixels='',frames='',duration_ms='',error='')
    if ext in RASTER:
        try:
            with Image.open(io.BytesIO(data)) as im:
                row.update(format=im.format,width=im.width,height=im.height,mode=im.mode,has_alpha_channel=('A' in im.getbands() or 'transparency' in im.info),frames=getattr(im,'n_frames',1))
                alpha=im.convert('RGBA').getchannel('A'); row['has_transparent_pixels']=alpha.getextrema()[0]<255
                duration=0
                for n in range(row['frames']): im.seek(n); duration += im.info.get('duration',0)
                row['duration_ms']=duration
            blobs[p]=data
        except Exception as e: row['error']=str(e)
    rows.append(row)

for p in FILES:
    ext=Path(p).suffix.lower()
    if ext not in MEDIA or '/repo/' in p: continue
    data=(ROOT/p).read_bytes(); inspect(p,data)
    if ext=='.zip':
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for info in z.infolist():
                if info.is_dir(): continue
                member=p+'!'+info.filename
                archive_entries.append(dict(archive=p,member=info.filename,bytes=info.file_size,compressed_bytes=info.compress_size))
                inspect(member,z.read(info), 'archive-member', info.compress_size)

byhash=defaultdict(list)
for r in rows: byhash[r['sha256']].append(r['path'])
duplicates=[dict(sha256=h,count=len(paths),paths=' | '.join(paths)) for h,paths in byhash.items() if len(paths)>1]
writecsv('assets/inventory.csv',rows)
writecsv('assets/archive-members.csv',archive_entries)
writecsv('assets/exact-duplicates.csv',duplicates)

refs=[]; remotes=[]
asset_token=re.compile(r'[^\s"<>;=]+\.(?:png|gif|jpe?g|webp|svg|ogg|wav|zip|ttf|otf)(?:\?[^\s"<>]*)?',re.I)
for p,content in texts.items():
    for lineno,line in enumerate(content.splitlines(),1):
        for m in asset_token.finditer(line):
            token=m.group(0).strip("'(),")
            item=dict(source=p,line=lineno,reference=token)
            refs.append(item)
            if 'http' in token: remotes.append(item)
writecsv('assets/loading-references.csv',refs)
writecsv('assets/remote-asset-references.csv',remotes)
ref_by_name=defaultdict(list)
for ref in refs:
    name=ref['reference'].split('?')[0].replace('\\','/').split('/')[-1]
    ref_by_name[name].append(f"{ref['source']}:{ref['line']}")
for r in rows:
    r['reference_candidates']=' | '.join(sorted(set(ref_by_name[Path(r['path']).name])))
    r['reference_method']='literal basename candidates; dynamic loading and aliases require code review'
    r['provenance_status']='root MIT notice; per-asset author/source/license not established'
    if r['category']=='emoji': r['provenance_status']='NotoEmoji-OFL notice present; individual file mapping not established'
    r['source_hint']='bundled cached external artwork' if r['category'] in {'player-sheets','portraits','cached-other','pitches-archive'} else 'bundled resource'
writecsv('assets/inventory.csv',rows)

variants=defaultdict(list)
for r in rows:
    stem=Path(r['path']).stem
    family=re.sub(r'(?:[_-](?:home|away|red|blue|selected|active|disabled|\d+x\d+|\d+))+$','',stem,flags=re.I)
    variants[(r['category'],family)].append(r['path'])
writecsv('assets/variant-candidates.csv',[dict(category=k[0],family=k[1],count=len(v),paths=' | '.join(v)) for k,v in variants.items() if len(v)>1])

modules=[]; deps=[]; ns={'m':'http://maven.apache.org/POM/4.0.0'}
rootpom=ET.parse(ROOT/'pom.xml')
managed={}
props={e.tag.split('}')[-1]:e.text for e in rootpom.find('m:properties',ns)}
for d in rootpom.findall('./m:dependencyManagement/m:dependencies/m:dependency',ns):
    managed[(d.findtext('m:groupId',namespaces=ns),d.findtext('m:artifactId',namespaces=ns))]=d
def resolve(value):
    for k,v in props.items(): value=value.replace('${'+k+'}',v)
    return value
for mod in ['ffb-common','ffb-tools','ffb-server','ffb-client-logic','ffb-resources','ffb-client','ffb-statetest']:
    java=[p for p in texts if p.startswith(mod+'/') and p.endswith('.java')]
    prod=[p for p in java if '/src/main/' in p]; tests=[p for p in java if '/src/test/' in p]
    awt=[p for p in prod if re.search(r'import (?:java\.awt|javax\.swing)',texts[p])]
    modules.append(dict(module=mod,tracked_files=sum(p.startswith(mod+'/') for p in FILES),production_java=len(prod),test_java=len(tests),production_lines=sum(len(texts[p].splitlines()) for p in prod),awt_swing_files=len(awt),test_annotations=sum(len(re.findall(r'@(?:Test|ParameterizedTest|RepeatedTest)\b',texts[p])) for p in tests)))
    tree=ET.parse(ROOT/mod/'pom.xml')
    for d in tree.findall('./m:dependencies/m:dependency',ns):
        def v(n): return d.findtext('m:'+n,default='',namespaces=ns)
        md=managed.get((v('groupId'),v('artifactId')))
        managed_version=md.findtext('m:version',default='',namespaces=ns) if md is not None else ''
        managed_scope=md.findtext('m:scope',default='',namespaces=ns) if md is not None else ''
        deps.append(dict(module=mod,group=v('groupId'),artifact=v('artifactId'),declared_version=v('version'),baseline_java8_version=resolve(v('version') or managed_version),scope=v('scope') or managed_scope or 'compile'))
writecsv('evidence/modules.csv',modules); writecsv('evidence/dependencies.csv',deps)
hotspots=sorted([dict(path=p,lines=len(t.splitlines()),branch_tokens=len(re.findall(r'\b(?:if|switch|case|catch|for|while)\b',t)),awt_swing=bool(re.search(r'import (?:java\.awt|javax\.swing)',t))) for p,t in texts.items() if p.endswith('.java') and '/src/main/' in p],key=lambda r:r['lines'],reverse=True)
writecsv('evidence/source-size-hotspots.csv',hotspots)
writecsv('evidence/test-catalog.csv',[dict(path=p,test_annotations=len(re.findall(r'@(?:Test|ParameterizedTest|RepeatedTest)\b',t)),methods=' | '.join(re.findall(r'\bvoid\s+(\w+)\s*\(',t))) for p,t in texts.items() if '/src/test/' in p and p.endswith('.java')])

font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',13)
titlefont=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',22)
groups=defaultdict(list)
for r in rows:
    if r['path'] in blobs: groups[r['category']].append(r)
samples=[]
for cat,items in sorted(groups.items()):
    items=sorted(items,key=lambda r:r['path'])
    # Deterministic spread across filenames, supplemented by size extremes.
    n=min(16,len(items)); picked=[items[round(i*(len(items)-1)/max(1,n-1))] for i in range(n)]
    for extra in sorted(items,key=lambda r:r['width']*r['height'])[:1]+sorted(items,key=lambda r:r['width']*r['height'])[-1:]:
        if extra not in picked: picked.append(extra)
    cols=4; cellw=310; cellh=220; header=65
    sheet=Image.new('RGB',(cols*cellw,header+((len(picked)+cols-1)//cols)*cellh),(23,29,39)); draw=ImageDraw.Draw(sheet)
    draw.text((18,14),f'{cat} | {len(items)} images | {len(picked)} samples',font=titlefont,fill='white')
    draw.text((18,43),'First frame; nearest-neighbor scaling for inspection. Source paths recorded in samples.csv.',font=font,fill=(190,200,214))
    for i,r in enumerate(picked):
        x=(i%cols)*cellw;y=header+(i//cols)*cellh
        for xx in range(x+8,x+cellw-8,12):
            for yy in range(y+8,y+155,12):
                draw.rectangle((xx,yy,min(xx+11,x+cellw-9),min(yy+11,y+154)),fill=(66,72,80) if ((xx-x)//12+(yy-y)//12)%2 else (91,96,103))
        im=Image.open(io.BytesIO(blobs[r['path']])).convert('RGBA')
        factor=min((cellw-24)/im.width,140/im.height,4)
        size=(max(1,round(im.width*factor)),max(1,round(im.height*factor)))
        im=im.resize(size,Image.Resampling.NEAREST);sheet.paste(im,(x+(cellw-im.width)//2,y+10+(140-im.height)//2),im)
        name=r['path'].split('!')[-1].split('/')[-1]
        draw.text((x+10,y+162),name[:43],font=font,fill='white')
        draw.text((x+10,y+180),f"{r['width']} x {r['height']} | {r['frames']} frame(s) | {r['format']}",font=font,fill=(190,200,214))
        draw.text((x+10,y+198),f"sample {i+1} | scale {factor:.2f}x",font=font,fill=(190,200,214))
        samples.append(dict(sheet=cat+'.png',sample=i+1,path=r['path'],scale=round(factor,3)))
    sheet.save(OUT/'assets'/f'{cat}.png')
writecsv('assets/samples.csv',samples)
summary=dict(revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),inventory_rows=len(rows),file_assets=sum(r['storage']=='file' for r in rows),archive_members=len(archive_entries),bytes_files=sum(r['bytes'] for r in rows if r['storage']=='file'),bytes_unpacked_members=sum(r['bytes'] for r in rows if r['storage']=='archive-member'),extensions=dict(Counter(r['extension'] for r in rows if r['storage']=='file')),image_categories=dict(Counter(r['category'] for r in rows if r['path'] in blobs)),duplicate_groups=len(duplicates),duplicate_entries=sum(d['count']-1 for d in duplicates),animated_images=sum(isinstance(r['frames'],int) and r['frames']>1 for r in rows),image_errors=[r for r in rows if r['error']],loading_reference_count=len(refs),remote_reference_count=len(remotes),sample_count=len(samples),contact_sheets=len(groups),modules=modules)
(OUT/'evidence'/'inventory-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
print(json.dumps(summary,indent=2))
