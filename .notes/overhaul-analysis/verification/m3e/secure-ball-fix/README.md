# Secure the Ball validation failure follow-up

The owner supplied a hosted Ubuntu validation failure from 2026-09-11 03:21:50 UTC:
`BallAndFoulActionsTest.secureLooseBallUsesNativeAutomaticPickupAndEndsActivation`,
line 75, expected ball possession but observed false. The class reported 19 tests
with one failure; Maven verify failed. The supplied output is preserved in
`reported-ci.log`. This follow-up started at `d98fd60d97fa9f924882fd6ef7489826cfa632cc`
with a clean working tree.

## Cause and correction

The test incorrectly described Secure the Ball as automatic pickup and left its
dice unseeded. The retained BB2025 `StepPickUp.pickUp` computes the Secure the Ball
target from 2 and native pickup modifiers, then calls `rollSkill`; a natural 1 can
fail. This fixture has no pickup modifiers, so its success expectation depended on
a random roll. Earlier local green builds do not establish that test was reliable.

The renamed success test seeds 2, verifies possession, the once-per-turn resource
and cleared acting player. The new failure test seeds 1 then 2, asserts no initial
possession and an offered team reroll, resolves it, and verifies exactly one reroll
consumed plus possession and activation completion. Both run through the existing
BrowserActionEvidence/SetupSession helper, which also checks wrong-actor/stale
rejection and exact-duplicate engine/dice invariance.

The coverage matrix now describes the native pickup roll and reroll. The existing
88 checked-in browser traces remain historical passing fixtures; the additional
failed-pickup path is covered by the new Java/adapter regression. No product code,
engine rule, catalog, credentials, server/container or browser behavior changed.
No user-facing ChangeList entry is needed for this test-only correction.

## Verification

Commands run from the repository root with the existing host Java 8/Maven toolchain:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 test -Offline -Module ffb-statetest -Test 'BallAndFoulActionsTest#secureLooseBallUsesNativePickupRollAndEndsActivation+failedSecureBallPickupOffersNativeTeamRerollAndResolvesOnce'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 test -Offline -Module ffb-statetest -Test BallAndFoulActionsTest
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 install -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 verify -Offline
git diff --check
```

The first selector passed two tests (`focused.log`). After strengthening the
activation-end assertions, the entire class passed all 20 tests (`class.log`).
Clean offline install passed all eight reactor projects in 3m07s (`install.log`).
Clean offline verify passed all eight in 2m38s (`verify.log`): **485 tests, zero
failures/errors/skips**, recorded from Surefire XML in `test-suites.csv`.
`git diff --check` passed (`whitespace.log`). No browser/live demonstration was
rerun: this correction changes only tests and documentation, with no product or
browser fixture changes. Host execution is
used because the existing ClassGraph tests need workspace access unavailable in
the sandbox. No skip/retry-until-green workaround was added.

This is a local correction and validation. A new hosted Ubuntu workflow run has
not been triggered or observed; no commit or push was requested.
