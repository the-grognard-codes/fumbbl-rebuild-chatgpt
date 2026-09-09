# Prepared-match JSON v1

M2c uses the existing authenticated local `/browser/v1` WebSocket and an additional
`preparedMatch` message family. It never accepts fixture controls, dice, arbitrary
team documents, resolved statistics, prices, ownership claims or legality flags.
The existing diagnostic `join` authenticates the socket; its home/away subject
is an identity namespace. Product roles are read from persisted membership.

## Requests and invitation policy

Requests require exactly these fields. Unknown, missing and duplicate fields,
fractional revisions, unsupported versions and malformed IDs fail before writes.
Ingress remains 16 KiB UTF-8 and eight nesting levels. IDs are lowercase UUIDs;
request IDs are 1–100 ASCII letters, digits, underscore or hyphen.

```json
{"version":1,"type":"preparedMatch","operation":"create","requestId":"create-1","teamId":"00000000-0000-0000-0000-000000000001","expectedDocumentVersion":1,"intendedOpponent":"away"}
{"version":1,"type":"preparedMatch","operation":"join","requestId":"join-1","matchId":"00000000-0000-0000-0000-000000000002","expectedRevision":1,"teamId":"00000000-0000-0000-0000-000000000003","expectedDocumentVersion":1}
{"version":1,"type":"preparedMatch","operation":"load","requestId":"load-1","matchId":"00000000-0000-0000-0000-000000000002"}
```

The creator must explicitly select the other authenticated local subject as
`intendedOpponent`. Self-invitations and other subjects fail. Creation assigns
home to the creator regardless of credential label. Only the intended opponent
can claim away using its own saved team. The creator and intended opponent may
read the complete preparation record; the invited reader has `callerRole:null`
until joining. There is no spectator, open seat, public invitation secret,
invitation change, team replacement, leave or delete operation. Knowing the match
ID confers no permission beyond that persisted invitation/membership policy.

Revision 1 has `WAITING_FOR_OPPONENT`, one frozen home member and `away:null`.
A successful join atomically creates revision 2 with `AWAITING_SETUP` and both
members. New join requests against that occupied seat fail. No request in this
contract can enter setup, kickoff or gameplay, or initialize an engine session.

## Responses and frozen data

Every reply has exactly:
`{version:1,type:"preparedMatch",requestId,code,duplicate,callerRole,recoveryMatchId,document}`.
`requestId` is null only when malformed input prevents correlation. A successful
`ACCEPTED` response has a complete document, `recoveryMatchId:null` and the caller's
persisted role (`home`, `away`, or null for an unjoined invitee). The only unknown
outcome code is `MATCH_OUTCOME_UNKNOWN`, which has `document:null`,
`callerRole:null` and the recovery match ID. Other failures have null document,
role and recovery ID, with `duplicate:false`. No rejected reply echoes input,
saved-team content, credentials or SQL errors.

Public document fields are `formatVersion:1`, `matchId`, `documentVersion`,
`lifecycle`, `invitation:{intendedOpponent}`, `home` and `away`. Each member has:

- Assigned `role`, `sourceTeamId`, `sourceDocumentVersion`.
- `ruleset`, `catalogVersion`, `rosterId`, `presetId`, `presetVersion`.
- `validation:{valid:true,total,budget,skillPoints,messages:[]}`.
- `roster:{captainId,resources,players}`. Each player has `id`, `slot`,
  `positionId`, purchased `skillIds` and a resolved `position` containing `name`,
  `role`, `race`, `maximum`, `cost`, `ma`, `st`, `ag`, `pa`, `av`, `primary`,
  `secondary`, `baseSkillIds` and `parameters` keyed by each base skill.

Parameters are explicit integers: Loner 3, Mighty Blow 1 and zero for skills
without parameters. Slots remain one-based. Captain Pro is an additional resolved
engine grant; it is not misrepresented as a purchased skill. Preset definitions
are versioned as part of the catalog, so `presetVersion` equals the pinned catalog
version and `presetId` selects the definition within it. The bounded format-1
interpreter supports BB2025 and the existing M2a Human catalog only.

The private persisted member additionally includes the complete `resolvedCatalog`
snapshot: all supported position statistics/access/quantities, base parameters,
skill categories/elite/selectable flags, resource prices/limits, roster/preset
limits, and an explicit format-1 validation policy (skill-point prices, purchase
limit, zero skill/captain gold, captain grant and unspent-budget policy). Private
owner subjects and accepted request fingerprints are stored with the document,
never included in public replies or normal application logs.

Selection loads one immutable saved document, checks the expected revision and
owner, and passes its draft through `TeamValidation` again. That read is the
source-selection consistency point: a later edit may finish before the match
write but cannot substitute its newer bytes. The match freezes the already-read
expected version. Subsequent edits/imports never change either frozen member.
No match operation writes the saved-team source.

Reading persisted format 1 verifies complete shape, ownership/roles/lifecycle,
version pins, resolved facts, computed totals/skill points and roster constraints
against the persisted catalog, without consulting mutable saved teams or fetching
a newer catalog. Unsupported/inconsistent snapshots return `SNAPSHOT_UNSUPPORTED`
and remain byte-for-byte stored. Malformed membership cannot establish read
authorization and returns `NOT_FOUND`. There is no automatic snapshot migration.
The bounded converter resolves all supported skills and parameters, links engine
Roster/RosterPosition objects and preserves stats/resources. It returns a Team
without creating an engine game or bypassing setup.

## Optimistic concurrency and durable retries

Create IDs are server-derived deterministic UUIDs scoped to local subject and
request ID, with a private canonical semantic fingerprint. The ID is a retry
locator, never authorization. Create retries must use the same request ID and
choices; reordered JSON properties are equivalent. Join keys are scoped to
match/subject/request ID. Its canonical fingerprint includes expected match and
team revisions. Reuse with different data returns `REQUEST_ID_REUSED`.

Accepted create/join metadata is persisted atomically with membership and frozen
rosters in a single InnoDB document row. There are exactly one or two retained
accepted requests per preparation record; they are never evicted. Concurrent
creates with one key resolve to one row. Concurrent distinct joins have exactly
one accepted winner. An exact concurrent retry may report duplicate acceptance,
but a distinct losing request cannot claim the winner's membership.

Exact accepted retries are resolved before rereading saved-team sources or
checking stale revisions. They return `duplicate:true` and the **current**
authoritative document, including an opponent that joined after the original
create. There is no duplicate team, lifecycle transition or engine initialization.
Failed attempts have no stored mutation or reserved key; a known rollback may be
retried with the exact request. This contract covers accepted mutation dedupe,
not a permanent audit of every rejected attempt.

`PERSISTENCE_FAILED` means no COMMIT was attempted and the transaction is rolled
back. Once COMMIT is attempted, an exception (including a lost acknowledgement or
connection-close failure) means `MATCH_OUTCOME_UNKNOWN`, never a false rejection.
Load the recovery ID and/or repeat the exact request to reconcile. A missing row
is not permission to invent another key after an ambiguous create; exact retry
can create that same stable ID if the first attempt rolled back. A socket close
likewise cannot establish whether a pending request committed.

The browser saves only request choices/IDs/revisions and identity subject in
session storage synchronously before sending. It stores no credential or private
roster document there. New mutations remain locked during ambiguity. Reconnect
requires re-entering the existing credential; the ordered authenticated fixture
snapshot identifies the subject solely to guard retry identity. A different
subject cannot silently replay the retained operation. Same-subject reconnect
reconciles the exact request or reloads the known match. Terminal correlated
replies clear retry metadata; callbacks from retired sockets and unrelated
request IDs cannot replace authoritative state. Browser tab/session storage
removal loses that convenience record; retain the match ID/request metadata when
moving between sessions. Server retry records survive independently.

Codes include `ACCEPTED`, `AUTHENTICATION_REQUIRED`, `AUTHORIZATION`, `NOT_FOUND`,
`INVITATION_REQUIRED`, `INVALID_REQUEST`, `UNSUPPORTED_VERSION`,
`STALE_TEAM_REVISION`, `STALE_REVISION`, `CONFLICT`, `SEAT_OCCUPIED`,
`REQUEST_ID_REUSED`, `VALIDATION_FAILED`, `MIGRATION_REQUIRED`,
`VERSION_UNAVAILABLE`, `INCOMPATIBLE_TEAM`, `SNAPSHOT_UNSUPPORTED`,
`PERSISTENCE_FAILED`, and `MATCH_OUTCOME_UNKNOWN`. The client explains errors as
text and retains team/match selections. Refresh saved teams after a stale source
revision; reload the match after a conflict.

See [migration and rollback boundaries](../containers/local/prepared-match-migration.md)
and [actual M2c acceptance evidence](../.notes/overhaul-analysis/verification/m2c/README.md).
