# Independent M1c review

An independent read-only code-review agent reviewed the transport, adapter,
socket/servlet/control lifecycle, renderer, acceptance drivers and measurement
methodology. It did not implement the changes it reviewed. Root integrated the
fixes and ran the final checks.

## Findings resolved before final image acceptance

| Finding | Resolution / regression |
|---|---|
| Admission permit could be lost before connection registration | Admission occurs in onConnect, ownership registers before throwable setup, and failure retires through common cleanup; repeated setup-failure regression |
| Reset socket could run queued join/action against new fixture | Atomic active/retired state checked before admission and on communication worker; queued-work-after-reset regression |
| Admission-rejected socket could process frames during close handshake | Active state remains false; unadmitted-frame regression |
| Overflow retained queue payloads and double-decremented shared metrics on late callback | Queue/message bytes cleared atomically, writing retired, late callback ignored; shared metrics/late completion and UTF-8 byte cap regressions |
| Failed/overflowed delivery depended on eventual Jetty close callback | Every terminal delivery closes through socket retirement immediately, with bounded serialized cleanup |
| Connection registration could occur after transport destruction | Registration and stop/snapshot share a lifecycle lock; acquired-before-stop/register-after-stop regression |
| Early renderer destruction leaked an application initialized afterward | The local late-resolving application is disposed explicitly; gated initialization lifecycle test |
| Claimed valid asset was fetched but not rendered | Real decode/Texture/Sprite path implemented; explicit local URL, successful SVG response and screenshots verified |

Other audited properties: legacy engine serialization remains unchanged; browser
ingress and outbound message/byte bounds are enforced; cleanup retains admission
permits until complete; results preserve history before delivery; snapshots are
not coalesced across results; monotonic watchdog terminates stalled writes;
same-actor/match exact retries retain original revisions; fixture replacement
retires old socket work and changes match identity. No actionable code findings
remained in the final review: **APPROVE**.

The subsequent fixed one-second filesystem-only operator hold was independently
reviewed. It is state-neutral, bounded, serialized by the mailbox's pending flag,
and fits within the existing shutdown-drain timeout. **APPROVE**.

## Measurement methodology review

The reviewer confirmed 100 measured lifetimes, six mutations/two rejects/two
duplicates each, two excluded warm-ups, and one reconnect per measured lifetime.
It checked matching result/snapshot revision, match identity, render sequence on
same-revision rejoin, and exclusion of credentials from output. **APPROVE**.

Required qualifications incorporated in the report: rendering ends at CPU Pixi
render submission plus marker observation, not GPU/display presentation; join
latency includes automated UI dispatch after credential filling; memory scopes
are distinct. Compare movement-lifetime samples for growth because the final
idle/retirement sample switches to a fresh Both Down fixture.

## Evidence and closeout review

Final independent review on 2026-09-07: **APPROVE**. The reviewer independently
recomputed workload composition and nearest-rank latency percentiles from raw
artifacts, checked payload and test totals, and confirmed that the memory prose
distinguishes scopes and qualifies the observed late RSS plateau. All nine M1
acceptance rows map to specific evidence. The final image identity, isolated
network probe, healthy stack and retained-resource cleanup are consistent.

No evidentiary correction or actionable finding remained. The reviewer supports
closing M1c and overall M1 and proceeding toward M2. Milestone status and next
action have been updated; no M2 implementation is included in this closeout.
