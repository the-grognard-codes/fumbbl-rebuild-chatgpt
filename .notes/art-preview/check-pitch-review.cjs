const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const root=process.cwd();
const html=fs.readFileSync(path.join(root,'.notes/art-preview/pitch-review-v2.html'),'utf8');
class Element {
  constructor(name='div'){this.name=name;this.children=[];this.attrs={};this.style={};this.hidden=false;this.checked=false;this.value='';this.scrollTop=0;this.scrollHeight=1800;this._text='';const set=new Set();this.classList={toggle(k){if(set.has(k)){set.delete(k);return false}set.add(k);return true},contains(k){return set.has(k)}}}
  setAttribute(k,v){this.attrs[k]=String(v)}
  append(...nodes){this.children.push(...nodes)}
  replaceChildren(...nodes){this.children=nodes;this._text=''}
  set textContent(t){this._text=String(t);this.children=[]}
  get textContent(){return this._text+this.children.map(c=>typeof c==='string'?c:c.textContent).join('')}
  focus(){}
  click(){this.onclick?.({target:this})}
  getBoundingClientRect(){return this.attrs.id==='viewport'?{width:1450,height:580}:{width:1884,height:796}}
  setPointerCapture(){}
}
const ids={};for(const match of html.matchAll(/<([\w-]+)\b([^>]*\bid="([^"]+)"[^>]*)>/g)){const node=new Element(match[1]);node.setAttribute('id',match[3]);node.hidden=/\bhidden\b/.test(match[2]);node.checked=/\bchecked\b/.test(match[2]);node.value=/\bvalue="([^"]*)"/.exec(match[2])?.[1]||'';ids[match[3]]=node}
const document={getElementById(id){assert.ok(ids[id],`missing id ${id}`);return ids[id]},createElement:n=>new Element(n),createElementNS:(_,n)=>new Element(n),addEventListener(){}};
const source=html.match(/<script>([\s\S]*?)<\/script>/)[1];
new vm.Script(source);
vm.runInNewContext(source,{document,ResizeObserver:class{constructor(fn){this.fn=fn}observe(){this.fn()}}});
function all(){const nodes=[];function walk(node){if(typeof node==='string')return;nodes.push(node);node.children.forEach(walk)}walk(ids.scene);return nodes}
const find=attr=>all().filter(n=>attr in n.attrs);
let checks=0;function check(name,fn){fn();checks++;console.log(`PASS: ${name}`)}
check('390 equal square cells',()=>{assert.equal(find('data-cell').length,390);assert.ok(find('data-cell').every(n=>n.attrs.width==='48'&&n.attrs.height==='48'))});
check('End zones occupy exactly one column each, with unchanged square size',()=>{const end=find('data-cell').filter(n=>['#2a4663','#74472c'].includes(n.attrs.fill));assert.equal(end.length,30);assert.ok(end.every(n=>['0','25'].includes(n.attrs['data-cell'].split(',')[0])))});
check('Dotted wide-zone boundaries span the pitch at rows 4 and 11',()=>{const lines=find('data-wide-boundary');assert.deepEqual(lines.map(n=>n.attrs.y1),['192','528']);assert.ok(lines.every(n=>n.attrs.x1==='0'&&n.attrs.x2==='1248'&&n.attrs['stroke-dasharray']==='2 7'))});
check('All 12 tokens anchor to their declared square centers',()=>{assert.equal(find('data-player').length,12);assert.ok(find('data-player').every(n=>n.attrs.transform===`translate(${(Number(n.attrs['data-col'])+.5)*48} ${(Number(n.attrs['data-row'])+.5)*48})`))});
check('Coordinates default off and count outwards 1–13 when enabled',()=>{assert.equal(find('data-distance').length,0);ids.coordinates.checked=true;ids.coordinates.onchange();assert.deepEqual(find('data-distance').map(n=>Number(n.textContent)),[13,12,11,10,9,8,7,6,5,4,3,2,1,1,2,3,4,5,6,7,8,9,10,11,12,13])});
check('Skill master toggle hides all badges',()=>{ids.skills.checked=false;ids.skills.onchange();assert.equal(all().filter(n=>n.attrs.class==='skill-tag').length,0);ids.skills.checked=true;ids.skills.onchange();assert.equal(all().filter(n=>n.attrs.class==='skill-tag').length,10)});
check('Custom skill abbreviations and restore-defaults affect displayed tokens',()=>{const guardInput=ids.skillOptions.children[1].children[1];guardInput.value='GD';guardInput.oninput();assert.ok(all().some(n=>n.attrs.class==='skill-tag'&&n.textContent==='B · GD'));ids.resetSkills.click();assert.ok(all().some(n=>n.attrs.class==='skill-tag'&&n.textContent==='B · G'))});
check('Per-skill visibility hides Mighty Blow badges',()=>{const checkbox=ids.skillOptions.children[0].children[0].children[0];checkbox.checked=false;checkbox.onchange();assert.ok(!all().some(n=>n.attrs.class==='skill-tag'&&n.textContent.includes('MB')));ids.resetSkills.click()});
check('Zoom changes the view only; cell-center transforms remain intact',()=>{const before=find('data-player').map(n=>n.attrs.transform);ids.zoomIn.click();assert.equal(ids.zoomLabel.textContent,'110%');assert.deepEqual(find('data-player').map(n=>n.attrs.transform),before);ids.fit.click();assert.equal(ids.zoomLabel.textContent,'100%')});
check('Keyboard panning adjusts viewBox and Fit restores it',()=>{const original=ids.pitch.attrs.viewBox;ids.zoomIn.click();const zoomed=ids.pitch.attrs.viewBox;ids.viewport.onkeydown({key:'ArrowDown',preventDefault(){}});assert.notEqual(ids.pitch.attrs.viewBox,zoomed);ids.fit.click();assert.equal(ids.pitch.attrs.viewBox,original)});
check('Log exposes 48 entries and Match start / Latest navigation',()=>{assert.equal(ids.log.children.length,48);ids.start.click();assert.equal(ids.log.scrollTop,0);ids.latest.click();assert.equal(ids.log.scrollTop,1800)});
check('Chat toggles into the shared panel and can be hidden',()=>{ids.chatToggle.click();assert.equal(ids.chat.hidden,false);assert.equal(ids.log.hidden,true);ids.chatToggle.click();assert.equal(ids.chat.hidden,true);assert.equal(ids.log.hidden,false)});
check('History expands and collapses',()=>{ids.expand.click();assert.equal(ids.feed.classList.contains('expanded'),true);ids.expand.click();assert.equal(ids.feed.classList.contains('expanded'),false)});
check('Bench drawer opens and closes',()=>{ids.benchToggle.click();assert.equal(ids.benchDrawer.hidden,false);ids.benchClose.click();assert.equal(ids.benchDrawer.hidden,true)});
check('Settings panel opens and closes',()=>{ids.settingsToggle.click();assert.equal(ids.settings.hidden,false);ids.settingsClose.click();assert.equal(ids.settings.hidden,true)});
check('Smaller usable-height control changes preview app budget',()=>{ids.sizing.onchange({target:{value:'820'}});assert.equal(ids.shell.style.height,'716px')});
console.log(`${checks} offline checks passed. JavaScript evaluated against a minimal simulated DOM; this is not browser rendering, CSS layout, accessibility, or viewport-fit verification.`);
