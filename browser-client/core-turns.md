# Browser core turns (M3b)

This extends [setup v1](setup.md) for the existing frozen Human catalog. Persisted
membership authorizes every request and broadcast. The Java BB2025 engine owns
all dice, movement costs, knockdowns, turnovers, resources and turn progression.
The browser sends no native commands, role, dice, seed or fixture data.

After authentication, use the existing setup load operation. The snapshot adds
`actions`, `turn`, `turnMode`, `ball`, `activePlayerId`, and a `state` description
on each player. `phase` additionally accepts `PLAY`. Ball coordinates are null
or `{x,y}` in canonical home orientation. Actions have exactly `id`, `kind`,
`label`, `actor`; they are server-issued typed decision options, including dice
faces and movement risk labels. Both participants see identical actions and
only the named actor may submit them. Coin/receive prompts retain their M3a shape.

```json
{"version":1,"type":"setup","operation":"action","requestId":"action-1","matchId":"00000000-0000-0000-0000-000000000002","expectedRevision":26,"actionId":"26:kick-18-7"}
```

Treat `actionId` as opaque. It is bound to the current revision and must be present
in the freshly projected legal list. Unknown IDs return INVALID_OPTION; wrong
actor, stale revision and changed request-ID reuse retain their existing error
codes. Validation occurs before command execution and dice consumption. Exact
retries return duplicate:true and current authoritative state without execution
or peer broadcast. Reconnect/load restores the same engine and pending options
while the JVM remains running; no mutation is automatically replayed.

History now retains at most 8,192 accepted requests/illegal setup confirmations
per resident match, without eviction. The existing 32-session limit, ingress and
outbound bounds still apply. All history and engine decisions remain in memory.
Restart retires activated sessions; durable prepared documents and frozen teams
are unchanged. No schema or credential migration is required. The paired local
image is `ffb-server:3.4.0-m3b.1`; update server and browser together because the
strict snapshot shape has expanded.

## Implemented controls

- Kick placement in the receiving half, native random scatter/events and touchback
  recipients. Quick Snap moves/finish and High Kick moves/finish are projected.
- Select movement, stand up, adjacent step movement with native dodge/rush risk
  labels, end player action and end turn for both participants.
- Declare blitz against reachable targets; native block targets and dice selection,
  team rerolls, offered Pro rerolls, push squares and follow-up decisions.
- Generic native reroll decisions (decline/team/Pro/offered skill), native
  knockdowns and turnovers. The grid shows authoritative ball/player status.
- Solid Defence and Charge player selections with explicit confirm/decline;
  bounded Solid Defence placement, correction and native legal-formation checks.
- Optional native skill use, modifying-skill alternatives, action-scoped decline,
  apothecary use and injury choice, owned by the affected participant.

Kickoff selection actions update a resident selection set and revision, without
executing the engine or rolling dice. Confirm submits only current eligible IDs
within the engine's minimum/maximum counts. Exact retries do not toggle twice.
Solid Defence projects one placement player at a time (plus selectable players
for correction), keeping snapshots below the existing 256 KiB outbound bound.
Failed formation confirmation returns ILLEGAL_SETUP without engine execution.
The legacy engine may retain consumed kickoff dialog data; projection uses the
current step to distinguish it from a live pending decision.

## Remaining controls and acceptance

All requested core action families are exposed for the bounded catalog. On the
Ball, Kick and other skills outside the frozen catalog are not recruited or added.
This does not certify every possible skill interaction or a complete match.
Unknown decisions expose no guessed actions; the engine remains resident for
inspection. Passing, hand-off, fouling, scoring/drive/half/completion UX, results,
replay, session cleanup and durable in-progress restart recovery remain later M3
work. There is no catalog, rules or infrastructure upgrade.

See [M3b evidence](../.notes/overhaul-analysis/verification/m3b/README.md) for actual
checks, failures and live demonstration status. Do not infer acceptance merely
from the availability of a control.
