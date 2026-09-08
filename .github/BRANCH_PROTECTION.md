# Lightweight branch and pull-request policy

Apply these settings to the GitHub `main` branch. They provide a dependable
gate for a solo project without requiring a second person or repeated manual
work.

## GitHub branch rule

- Require a pull request before merging; do **not** require approvals.
- Require the `Validate` status check from the `Maven Verify` workflow. In
  GitHub's checks list this is normally displayed as `Maven Verify / Validate`;
  select the `Validate` check, not the workflow heading alone.
- Do not require a branch to be up to date before merging.
- Require conversation resolution when a review conversation exists.
- Block force pushes and branch deletion.
- Allow only squash merges, with the default commit title set to the pull
  request title.
- Keep administrator bypass available to the repository owner for emergencies.

GitHub branch settings are repository-hosted configuration, so they must be
enabled in **Settings > Branches** after this file is merged.

## Working convention

- Start short-lived branches from `main`: `feat/...`, `fix/...`, `chore/...`,
  `refactor/...`, `test/...`, or `docs/...`.
- Open a pull request into `main` as soon as the work is reviewable. The PR
  template records scope and local validation; GitHub runs the full validation
  suite on every update.
- Merge only after `Validate` is green. Squash merge to keep `main` readable,
  then delete the merged branch.
- Keep pull requests focused. Use a separate PR for unrelated cleanup,
  dependency upgrades, or generated artifacts.

## Validation contract

`Maven Verify` checks the pinned build tooling, rejects disabled JUnit tests,
and runs `clean verify` against the complete Maven reactor. It runs for every
pull request to `main`, every push to `main`, and on demand. Superseded runs
for the same PR are cancelled to avoid wasted CI time.
