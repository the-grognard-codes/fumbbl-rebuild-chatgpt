# R2 local recovery compatibility boundary

This runtime is for the isolated `ffb-local-r2b` Compose project, port 22230. It must not replace a JVM that owns an unfinished Java 8 or R1 match. Keep that JVM and its database running until its matches complete. Do not point R2 at those retained databases to test migration. No public-service gate follows from these local checks.

## Versions and rejection

The private recovery envelope has `payload` and a SHA-256 checksum of its serialized payload. Recovery format **2** pins runtime adapter `ffb-3.4.0-bb2025-r2.2`, rules engine `ffb-3.4.0-bb2025-m3d.1`, replay format **1**, and the frozen BB2025 Human catalog/preset `bb2025-human-2026-09-08.1`. Browser JSON remains version 1. Local database marker **5** adds a separate recovery table; it does not change completed replay format or the schema-4 prepared-match document contract.

The artifact contains the full frozen teams and owners, authoritative revision, drive, native game/current step/step stack/dialog/log, private dice state and test-roll queues, accepted request fingerprints and response codes, kickoff selections, replay event bytes, command counter, turn timer start, failed-state marker, and pending-terminal flag. Both role projections are retained as restore assertions; they are not used to reconstruct the engine. Only the native snapshot reconstructs engine state. Recovery never runs a start sequence or command.

Missing recovery for an activated pre-R2 match returns `SESSION_UNAVAILABLE`; it never starts a replacement engine. Unsupported recovery/runtime/engine/replay versions return `RECOVERY_UNSUPPORTED`. A checksum, input binding, structure, or reconstructed decision mismatch returns `RECOVERY_CORRUPT`. Artifacts are retained on rejection. Membership is loaded and checked before recovery reads, replies, retries, and writes. There is no browser recovery-import route.

The checksum detects damaged stored bytes; it is not authentication against a database administrator. Recovery artifacts include future private randomness and must remain private. Evidence exports redact the seed.

## Durable boundaries

The initial format-1 trial (`compose.r2-trial.yaml`, image `3.4.0-r2.1`, port 22229) is retained with its unfinished synthetic match and checkpoint. A real kill exposed unordered native collection serialization across JVMs. Format 2 rejects that trial format; it does not upgrade or rewrite those artifacts. The final project has separate database and backup volumes and starts from a separate schema-4 copy.

The existing single communication worker remains the only match mutation executor. Each recovery row has an independent monotonic generation used for compare-and-swap; gameplay revision can remain unchanged for a recorded rejection. A dedicated JDBC transaction writes one bounded artifact before the success response or broadcast. Exact retries read the persisted history and do not write another checkpoint or execute the engine again.

Initialization first stages an unpublished initial checkpoint against the immutable prepared teams. Only then may the prepared document move from revision 2 to activated revision 3. A crash between those transactions leaves a prepared match with a reusable staged checkpoint. It does not expose an uncommitted engine or lose an acknowledged activation. Staging failures leave preparation retryable. Old activated matches without checkpoints cannot enter this path.

An action whose checkpoint commit succeeds is authoritative even if its acknowledgment is lost. On checkpoint SQL error, commit/close ambiguity, or CAS conflict, the application discards the resident engine and reads the durable checkpoint on the next authorized request. If commit did not happen, the previous decision remains authoritative. An unacknowledged action killed *before* its checkpoint may be executed again from the prior state; no resource or dice consumption from that uncommitted execution is retained. This is not a claim that physical instruction execution is exactly once across a kill inside an uncommitted command.

A terminal checkpoint is committed before the completed-result transaction. It retains the terminal request and complete replay. A crash before, during, or after the result commit is reconciled by an authorized load/retry: identical existing results succeed; otherwise the pending result is committed without a second engine execution. The terminal checkpoint remains available for exact request retries after restart.

## Intentional native compatibility adaptations

New recoverable matches use a private per-match HMAC-SHA256 counter stream (dice stream version 1), seeded with 32 bytes from the server JDK `SecureRandom`. Each counter value is encoded as eight big-endian bytes; the first HMAC byte is sampled with the same unbiased rejection-to-die mapping as Fortuna. Counter and seed are checkpointed. Legacy lifetimes still use server Fortuna. This is an explicit randomness ownership boundary, not an in-place conversion of a Fortuna lifetime. Client input cannot select seeds, counters, rolls, or test-roll queues.

Native JSON maps an absent queued kickoff scatter distance to zero on deserialization. Recovery format 2 normalizes that field for `kickoffScatterRoll` only; the native kickoff step overwrites it with its rolled distance before use. Native/replay serializers and BB2025 rule classes are unchanged. Object keys inside the recovery payload are canonicalized; replay event JSON is retained as an opaque string so replay bytes are not reordered. Separate continuation executions differ in elapsed wall time; restore-only parity includes original timer/model/log values exactly.

## Storage and operation

Format 2 normalizes only native collection properties backed by maps/sets: game options, roster normal/double skill categories, field move/pushback/track/dice/marker sets, per-player cards/effects, acting-player used skills, inducement maps and block reroll source sets. Ordered steps, dice, reports, logs, player lists and replay events retain their order. Recoverable sessions also emit actions in stable role/id order, so a restored decision presents the same action order despite native set iteration. These are explicit serialization/presentation adaptations; rule code, frozen catalog definitions and replay format are unchanged.

Migration `005-match-recovery.sql` creates an InnoDB recovery table with a 32 MiB artifact bound. It verifies columns, collations, primary key, checks and absence of triggers before advancing marker 5. Saved-team creation explicitly accepts marker 5 while continuing to reject future markers. R1 cannot open marker 5. Test this migration only against a separately restored copy when an existing database needs transition; retain the source unchanged. This tranche supplies no generalized retention or capacity cleanup policy.

Use `docker compose -f containers/local/compose.r2.yaml` without a project-name override. Do not combine it with other Compose files. Real process tests kill and start only `ffb-local-r2b-server-1`, retaining its image and database volume. Never use `down -v`, reset, or volume deletion for recovery verification.

The isolated MariaDB configuration sets `max_allowed_packet=64M` to accommodate
the 32 MiB artifact limit. The driver remains MariaDB JDBC 3.5.8 and the server
remains MariaDB 11.8.9. This setting is confined to the new R2 database.
