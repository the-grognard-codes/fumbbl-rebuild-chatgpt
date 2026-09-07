"""Package evidence and check report links, without changing product sources."""
from pathlib import Path
import csv, json, re, shutil, subprocess, datetime, xml.etree.ElementTree as ET

OUT=Path(__file__).resolve().parent
ROOT=OUT.parent.parent
verification=OUT/'verification'

result=[]
for mod in ['ffb-common','ffb-server','ffb-client-logic','ffb-statetest']:
    for p in sorted((ROOT/mod/'target'/'surefire-reports').glob('TEST-*.xml')):
        tree=ET.parse(p).getroot()
        entry={'module':mod,'suite':tree.attrib['name']}
        entry.update({k:int(tree.attrib.get(k,0)) for k in ['tests','failures','errors','skipped']})
        result.append(entry)
        dest=verification/'clean-verify-reports'/mod
        dest.mkdir(parents=True,exist_ok=True)
        shutil.copy2(p,dest/p.name)
with (verification/'clean-verify-results.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=result[0].keys());w.writeheader();w.writerows(result)
totals={k:sum(r[k] for r in result) for k in ['tests','failures','errors','skipped']}
totals['passed']=totals['tests']-totals['failures']-totals['errors']-totals['skipped']
log=(verification/'clean-verify-java8.log').read_text(encoding='utf-8',errors='replace')
assert 'BUILD SUCCESS' in log and 'ExitCode=0' in log
assert totals=={'tests':348,'failures':0,'errors':0,'skipped':1,'passed':347},totals

p=OUT/'07-verification-and-coverage.md'
t=p.read_text(encoding='utf-8')
t=t.replace('Status: execution in progress; final counts and findings will replace this paragraph before handoff.','Status: **completed baseline verification**. Both required Java 8 lifecycle runs passed: 348 reported tests, 347 passed, one existing disabled test, zero failures/errors in each run. Full independent service startup and browser play remain unverified.')
t=t.replace('that setup is beyond a no-code audit and was not simulated by claiming a usage-only process launch as startup success.','no disposable database was provisioned in this audit. A follow-up PATH check found no docker, podman, mariadbd or mysqld executable. This is a remaining runtime-verification gap, not a restriction on future local testing; the usage-only process check is not startup success.')
t=t.split('\n\n## CI lifecycle and final evidence')[0]
t+='''

## CI lifecycle and final evidence

`clean verify` completed with **BUILD SUCCESS, ExitCode=0** at 2026-09-06T16:09:25-04:00 in 1 minute 57 seconds. All eight reactor projects succeeded. The suite totals match clean install: **348 reported, 347 passed, one source-disabled, zero failures/errors**. Evidence: [console log](verification/clean-verify-java8.log), [per-suite CSV](verification/clean-verify-results.csv), and XML files under `verification/clean-verify-reports/`. This rerun was required to exercise the repository's CI lifecycle separately; no additional product tests were added.

The successful build runs used portable Java 8 and an isolated Maven cache. Initial sandbox failures and offline missing-plugin failures are retained for diagnosis; they are not counted as remaining product defects. The Java 21 `mockito5` profile, Linux CI platform, real MariaDB lifecycle, browser client, live WebSocket interoperability, and full-match recovery were not exercised. No required failed check is being represented as passing.

Portable Java/Maven archives, extracted tools, copied dependency cache and package extraction are ignored by the report directory's `.gitignore`; they are local reproducibility tooling, not reviewable product changes. Keep text logs/CSV/XML and checksum metadata when sharing the report. Logs/XML contain local filesystem paths and runtime properties; review them before publishing externally. Nothing was committed, pushed, deployed, or written to live FUMBBL services.
'''
p.write_text(t,encoding='utf-8')

summ=json.loads((OUT/'evidence/inventory-summary.json').read_text())
rows=list(csv.DictReader((OUT/'assets/samples.csv').open(encoding='utf-8')))
cats=sorted(set(r['sheet'] for r in rows))
gallery=['# Asset contact sheets','', '230 sampled images across all 18 inventory image categories were visually inspected. These sheets show first frames and scale previews; they do not replace full-resolution or in-game QA. Source paths and scale factors are in [samples.csv](samples.csv). See [inventory.csv](inventory.csv) for every measured file/member and [visual assessment](../03-assets-and-visual-strategy.md).','']
for cat in cats:
    gallery += ['## '+cat[:-4],'',f'![{cat[:-4]}]({cat})','']
(OUT/'assets/README.md').write_text('\n'.join(gallery),encoding='utf-8')

metadata={'revision':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'branch':subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip(),'captured_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'initial_status':'?? .notes/','final_status':subprocess.check_output(['git','status','--short'],cwd=ROOT,text=True),'tracked_diff':subprocess.check_output(['git','diff','--stat'],cwd=ROOT,text=True),'verify_totals':totals,'sources':'Git-tracked source/media at revision; .notes background read separately'}
(OUT/'evidence/audit-metadata.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')

issues=[]; count=0
for p in OUT.glob('*.md'):
    content=p.read_text(encoding='utf-8').replace('](../../../','](../../')
    p.write_text(content,encoding='utf-8')
    for target in re.findall(r'\]\(([^\n]+?)\)',p.read_text(encoding='utf-8')):
        if target.startswith(('https://','http://','#')):continue
        target=target.strip('<>');filepart=target.split('#')[0]
        dest=(p.parent/filepart).resolve()
        count+=1
        if not dest.exists():issues.append({'file':p.name,'link':target,'error':'missing file'})
        elif '#L' in target and dest.is_file():
            lineno=int(re.search(r'#L(\d+)',target).group(1))
            if lineno>len(dest.read_text(encoding='utf-8',errors='replace').splitlines()):issues.append({'file':p.name,'link':target,'error':'line beyond file'})
(OUT/'evidence/report-link-check.json').write_text(json.dumps({'checked':count,'issues':issues},indent=2),encoding='utf-8')
print(json.dumps({'tests':totals,'metadata':metadata,'links_checked':count,'link_issues':issues},indent=2))
