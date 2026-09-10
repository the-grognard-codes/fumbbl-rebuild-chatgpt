# M3a independent review

Final verdict: APPROVE (read-only code review, 2026-09-09).

Initial findings and disposition:

- Unknown activation COMMIT acknowledgement could leave a same-JVM retry without
  an initialization reservation. Corrected with an in-process pending reservation
  that permits the original exact retry to initialize once. Added focused test.
- Reservation keys initially omitted fixed envelope fields. Added explicit
  version/type/operation validation before touching reservations; malformed
  same-ID retries cannot consume the original reservation. Added regression.
- The 32-entry resident bound was initially questioned; review accepted the
  explicit local retained-lifetime policy. Kickoff-ready games are not terminal,
  fixture reset cannot discard them, and restart retires sessions fail-closed.
- Failed-session loads/retries now consistently return SESSION_UNAVAILABLE with
  null state and duplicate=false. Added regression.

Final reviewer found no actionable issue in authorization, activation retry,
restart policy, failed-session responses or current UI identity/revision/retry
guards. Reviewer did not independently rerun tests; root recorded execution.
