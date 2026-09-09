# M2a status

**M2a implemented and validated, 2026-09-08 UTC (2026-09-07 local).**
The owner's approved BB2025 source and selected 1,150,000-gold budget resolved
the original blocker. See [current closeout, exact checks and limitations](closeout.md)
and [catalog source comparison](../../../../browser-client/catalog.md).
M2 remains incomplete; the next slice is M2b: saved-team round trip.

## Historical repository-only provenance gate — 2026-09-07

Status: **blocked before implementation; M2a and M2 are not complete**.
Inspected HEAD `162f8c7c958b85994fa0382d0f90b7aa54286d5d`. Starting working
tree contained only the untracked `12-m2a-catalog-validation-prompt.md`; preserved it.
Read ADR-001–004, the PRD, roadmap, kickoff, browser protocol and M1c closeout.

The task explicitly requires: “If the available source cannot support a defensible
curated starter catalog, record that precise blocker and stop rather than guessing.”
That gate applies here. No product catalog version or supported content is published.
The supported M2a roster/position/skill set is empty pending verified source data.
This says nothing about the engine's independently characterized skill support.

## Source trace and precise blocker

- `ffb-server/rosters/` contains 29 XML rosters: 24 have `.lrb6` identifiers.
  The remaining five are Dark Elf (`4959`, team `1084086`), Tomb Kings (team
  `55051`), Nippon (`5681`), Chaos Renegade (team `1050157`), and Slann (team
  `744258`). None declares a BB2025 ruleset, catalog version, or provenance that
  establishes its quantities, costs and permitted skills as BB2025 starter data.
  Numeric external IDs are not evidence of a particular rules edition.
- `ffb-server/rosters/roster_human.xml` identifies itself as `human.lrb6`.
  Its lineman has quantity 16, cost 50000, armour 8, and normal General / double
  Agility, Strength, Passing access. Its Ogre lists both `bone head` and
  `bone-head`. These are source observations, not certified BB2025 rules.
  Selecting a small subset does not establish the missing edition provenance.
- `containers/local/fixtures/rosters/human.xml` is `fixture.human`, with a
  `fixture.lineman` costing 50000, quantity 16, armour 9 and an empty skill list.
  `containers/local/README.md` explicitly calls these startup fixtures, not a
  validated roster-builder catalog. Choosing BB2025 before loading them selects
  engine behavior; it does not certify synthetic recruitment data.
- `ffb-server/src/main/java/com/fumbbl/ffb/server/RosterCache.java` loads XML by
  team ID, falling back to roster ID, and parses with the supplied game's rules.
  Its documentation describes copying FUMBBL team/roster API output. It performs
  no source-edition certification. No remote data was fetched or substituted.
- `ffb-common/src/main/java/com/fumbbl/ffb/model/RosterPosition.java` reads
  `quantity` and `cost` directly as integers (lines 552–571); it does not derive
  current recruitment values from BB2025 mechanics. `skillList` resolves names
  against the game's skill factory, storing optional values, but silently skips
  unresolved skills (lines 515–524). `normal` and `double` category lists also
  skip unresolved categories (lines 526–540). Thus successful XML loading is
  insufficient evidence of a complete permitted-skill mapping.
- `ffb-common/src/main/java/com/fumbbl/ffb/model/Roster.java` reads reroll price,
  maximum rerolls, apothecary and maximum big-guy fields from supplied data.
  These model fields do not supply a provenance-backed starter preset budget
  or certify roster recruitment constraints.
- `ffb-statetest/src/main/java/com/fumbbl/ffb/test/GameStateBuilder.java` constructs
  positions and assigns synthetic stats/skills; `applySkills` skips unknown names.
  It supplies engine scenarios, not a recruitment catalog. Searches of production
  cost/quantity setters found XML assignment, not an alternate BB2025 price list.

The blocker is missing verified BB2025 recruitment input, not missing Java skill
implementations or a transport limitation. Reusing legacy/synthetic prices and
adding a BB2025 label would invent the guarantee the task requires.

## Checks and changes

Read-only inspection completed:

```powershell
git status --short
git rev-parse HEAD
rg -n '<roster |<ruleset|2025' ffb-server/rosters containers/local/fixtures
rg -l -uuu '<roster[ >]' ffb-server containers ffb-common ffb-statetest -g '*.xml' -g '!**/target/**'
rg -n -i '2025|ruleset|rulebook' ffb-server/rosters
rg -n 'setCost\(|setQuantity\(' ffb-common/src/main ffb-server/src/main ffb-statetest/src/main
```

The XML inventory found the 29 bundled sources plus the one local synthetic
roster. The edition-marker search found no matches. Read the loader, model,
fixture builder and source XML to interpret these searches; absence of a marker
alone is not a claim that every numeric value is wrong.

Changed files: this report and `09-implementation-kickoff.md` (status link).
No Java, browser, protocol, build, workflow or ChangeList changes; no exposed route.
`git diff --check` passed after documentation edits. Required tooling tests,
focused Java selector, offline install/verify, npm tests/build and live adapter
demonstration were **not run** because the explicit source gate stopped
implementation. No M2a test acceptance or current hosted CI result is claimed;
M1c's historical passing results remain historical. No environmental failure was
encountered in the source inspection. No disabled tests were added.

## Unblock and continuation

Supply a traceable BB2025 source snapshot for at least one starter roster with
position identifiers, stats, recruitment costs and limits, base skills with
parameters, permitted enhancement/category semantics, resource prices/limits and
roster-wide constraints. Record its edition/source and the selected exhibition
preset budget and enhancement policy. A verified source may support a smaller
subset; unsupported content must remain explicit. A parser success or an art
sample is not that evidence.

Resume **M2a** by validating that input against the existing engine, then implement
the immutable versioned catalog, application seams, strict bounded JSON contract,
DOM editor and rejection/recomputation tests. Run the entire requested validation
sequence and live demonstration after implementation. The next slice after M2a
acceptance remains **M2b: saved-team round trip**; do not start it from this blocker.
No commits, pushes, deployments, volume resets or credential changes occurred.
