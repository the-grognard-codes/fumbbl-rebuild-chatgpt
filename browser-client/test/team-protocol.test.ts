import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { decodeTeam, emptyDraft } from '../src/team-protocol.ts';
import type { Catalog, Validation } from '../src/team-protocol.ts';
import { TeamValidationView } from '../src/team-validation-view.ts';

const fixture = JSON.parse(readFileSync(new URL('./fixtures/catalog-v1.json', import.meta.url), 'utf8'));
const result: Validation = { version: 1, type: 'teamValidation', requestId: 'test', catalogVersion: fixture.catalogVersion, ruleset: 'BB2025', valid: true, total: 700000, budget: 1150000, skillPoints: 0, messages: [] };
test('catalog fixture is runtime checked and new drafts retain version and ruleset', () => {
  const catalog = decodeTeam(JSON.stringify(fixture)) as Catalog;
  const draft = emptyDraft(catalog);
  assert.equal(draft.catalogVersion, catalog.catalogVersion); assert.equal(draft.ruleset, 'BB2025');
  assert.equal(catalog.positions.length, 6); assert.equal(catalog.skills.filter(skill => skill.selectable).length, 7);
});
test('unknown schema versions, fields, references and duplicate identifiers fail closed', () => {
  for (const invalid of [
    { ...fixture, version: 2 }, { ...fixture, catalogVersion: 'unknown' }, { ...fixture, ruleset: 'BB2020' },
    { ...fixture, class: 'internal' }, { ...fixture, budget: -1 }, { ...fixture, budget: 1.5 },
    { ...fixture, positions: [] }, { ...fixture, positions: [fixture.positions[0], fixture.positions[0]] },
    { ...fixture, positions: [{ ...fixture.positions[0], baseSkills: [{ id: 'unknown', value: 0 }] }] },
    { ...fixture, positions: [{ ...fixture.positions[0], cost: '50000' }] },
    { ...fixture, resources: [] }, { ...fixture, skills: [fixture.skills[0], fixture.skills[0]] },
    { ...fixture, skills: [{ ...fixture.skills[0], elite: 1 }] },
  ]) assert.throws(() => decodeTeam(JSON.stringify(invalid)));
  assert.throws(() => decodeTeam('{')); assert.throws(() => decodeTeam('x'.repeat(16385)));
});
test('validation decoder rejects contradictory or malformed server messages', () => {
  assert.deepEqual(decodeTeam(JSON.stringify(result)), result);
  for (const invalid of [
    { ...result, valid: 'true' }, { ...result, total: -1 }, { ...result, total: null },
    { ...result, valid: false }, { ...result, messages: [{ code: 'X' }] }, { ...result, type: 'other' },
  ]) assert.throws(() => decodeTeam(JSON.stringify(invalid)));
});
test('DOM renders server cost, rejection messages and unavailable totals accessibly', () => {
  assert.match(renderToStaticMarkup(createElement(TeamValidationView, { result: null })), /Draft has not been validated/);
  let html = renderToStaticMarkup(createElement(TeamValidationView, { result }));
  assert.match(html, /Valid draft/); assert.match(html, /700,000 gold/); assert.match(html, /aria-live="polite"/);
  for (const text of ['<script>bad</script>', '<SCRIPT>bad</SCRIPT>', '<ScRiPt>bad</ScRiPt>']) {
    html = renderToStaticMarkup(createElement(TeamValidationView, { result: { ...result, valid: false, total: null, messages: [{ code: 'QUANTITY', path: 'resources.rerolls', text }] } }));
    assert.match(html, /Draft needs changes/); assert.match(html, /Total unavailable/); assert.match(html, /resources.rerolls/);
    assert.match(html, /&lt;script&gt;/i); assert.doesNotMatch(html, /<script>/i);
  }
});
