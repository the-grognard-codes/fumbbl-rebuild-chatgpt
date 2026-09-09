import { createElement as h } from 'react';
import type { Validation } from './team-protocol.ts';

/** Pure DOM rendering of server output; text is escaped by React. */
export function TeamValidationView({ result }: { result: Validation | null }) {
  if (!result) return h('p', { role: 'status' }, 'Draft has not been validated.');
  return h('section', { 'aria-label': 'Server validation', 'aria-live': 'polite' },
    h('h2', null, result.valid ? 'Valid draft' : 'Draft needs changes'),
    h('p', null, result.total === null ? 'Total unavailable until invalid references or quantities are corrected.' : `Server total: ${result.total.toLocaleString('en-US')} gold / ${result.budget.toLocaleString('en-US')} budget`),
    h('p', null, `Purchased skill points: ${result.skillPoints}`),
    h('ul', null, ...result.messages.map((message, index) => h('li', { key: index }, `${message.path}: ${message.text} (${message.code})`))));
}
