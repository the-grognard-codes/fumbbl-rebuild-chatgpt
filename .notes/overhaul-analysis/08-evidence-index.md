# Evidence, scope coverage and remaining uncertainty

## Evidence contract

**Verified** means a specific source/configuration body was inspected, an automated inventory measured it, or a recorded command demonstrated it. **Observed** denotes visual inspection of selected images. **Inferred/recommended** denotes engineering judgment. **Unknown/gap** is not an assertion of absence or failure. Source links identify files/line anchors or symbols at the audited revision; inspect the named method if a renderer does not navigate local `#L` anchors.

The user supplied scope decisions in this task override the historical `.notes` summary. Its citation markers lack source documents, its stack examples were proposals, and its legal conclusions were not adopted. The original background files were preserved.

## Repository coverage

| Area | Completed inspection/verification | Remaining limitation |
|---|---|---|
| Root/build/CI | POM, module dependencies, profiles, Git revision/status, Java 8 install/verify | Java 21 profile and Linux platform not executed; no complete transitive vulnerability/license scan |
| Common | Full file/import/size census; sampled models, rules annotation/factories, commands, roster types; full existing JUnit suite | Not every rule/skill/serializer body manually read |
| Server | Sampled critical actions and lifecycle, sessions, sync, JDBC/startup, external routes; full existing JUnit suite | No live database/real socket/recovery/load/fuzz or comprehensive route/payload security test |
| Client logic | Import census, command/state flow, image/font/animation contracts and existing tests | Not every dialog/overlay manually exercised; two manual Swing tests unrun |
| Desktop client | Launcher/packaging/dependencies, source census, successful package build | No full interactive desktop match or graphical comparison |
| Resources | All tracked recognized media + every ZIP member measured; 230 image samples from every category viewed | Remote-only/JAR-embedded/generated visuals not enumerated as physical assets; no full animation playback/audio listening |
| Tools | Build scripts/collection/local icon mapping and module build | Collector utilities not executed against remote services |
| State tests | Existing source harness and scenario inspection; all Maven-discovered tests run | Synthetic teams skip catalog legality/pregame; sparse BB2025 scenarios are not exhaustive compliance |
| External technology | Official primary sources checked and linked in technology/asset reports | No performance benchmark or vendor price quote; pin/verify versions again at implementation |

Every reactor module is accounted for. Automated census is complete for the stated tracked paths/extensions; manual code and visual review are sampled. This is a modernization audit, not a statement that every line has been verified.

## Source census

[modules.csv](evidence/modules.csv) records 3,100 production Java files and 284,444 physical source lines across all seven modules. Counts include the five state-test helper files under `src/main`; they do not mean all are product runtime code. There are 25 Java test-source files, of which two are manual Swing programs. Parameterized JUnit expansion explains why source annotation counts differ from Surefire invocation counts.

Zero common/server files and 182 client-logic files match explicit AWT/Swing import patterns. This is an import census, not a proof that every transitive dependency runs headless. [source-size-hotspots.csv](evidence/source-size-hotspots.csv) ranks physical line count and rough branch-token count: FieldModel (1,302 lines), BB2025 StepEndTurn (1,043), kickoff result application (917), and shared catch/scatter/throw-in (871) are attention candidates. Branch tokens include comments/strings and are **not** cyclomatic complexity; large files are not automatically defects.

The source census spans both historical and BB2025 implementations. Intentional duplication follows the repository's ruleset-isolation guidance. Compare outcomes and ownership before consolidating matching classes. No clone detector or measured line/branch coverage instrumentation was added.

## Machine-readable artifacts

| Artifact | Interpretation |
|---|---|
| [Audit metadata](evidence/audit-metadata.json) | Revision, branch, initial/final status and final verification totals |
| [Module census](evidence/modules.csv) | Tracked files, production/test Java counts, lines and imports |
| [Dependency declarations](evidence/dependencies.csv) | Direct module dependencies, root-managed baseline Java 8 versions/scopes; not full effective/transitive dependency graph |
| [Source hotspots](evidence/source-size-hotspots.csv) | Reproducible size/import hints for prioritizing deeper review |
| [Test catalog](evidence/test-catalog.csv) | Test source paths, annotation/method discovery; includes manual test-named programs |
| [Asset inventory](assets/inventory.csv) | Each recognized tracked media file and ZIP member, hashes, actual image format, geometry, transparency and reference/provenance candidates |
| [Archive contents](assets/archive-members.csv) | Every member of four ZIPs, compressed/uncompressed bytes |
| [Exact duplicates](assets/exact-duplicates.csv) | Byte-identical content groups; semantic aliases may be intentional |
| [Variant candidates](assets/variant-candidates.csv) | Filename family heuristics; not perceptual similarity or interchangeable assets |
| [Loading references](assets/loading-references.csv) | Literal asset-like references with source and line; dynamic lookup incomplete |
| [Remote references](assets/remote-asset-references.csv) | HTTP-containing subset, including repeated cache mappings; no live URL availability claim |
| [Visual sample map](assets/samples.csv) | Contact sheet, sample position, original source and preview scale |
| [Clean verify results](verification/clean-verify-results.csv) | Module/suite reported invocation counts, failures/errors/skips |
| [Report-link check](evidence/report-link-check.json) | Existence/range checks for report links; not semantic proof of each claim |

Re-run the supplied inventory collector with the bundled Python/Pillow runtime to reproduce media and source data. It writes only in this report directory. The finalization helper packages the existing final Surefire reports and validates report references. Build commands/tool hashes are in [verification](07-verification-and-coverage.md). Future collector runs reflect the then-current checkout; preserve this revision's outputs when comparing changes.

## Sources and attribution

Primary local evidence: root/module POMs and CI workflow; `CommandSocket`, `ServerCommunication`, `StepExecutor`, `GameState`, `SessionManager`, BB2025 step bodies and generators; roster/team classes and local samples; `IconCache`, `PlayerIconFactory`, `FontCache`, animation definitions, cache builders; current tests. Reports 01–05 cite these directly at relevant claims.

External primary documentation consulted 2026-09-06: [Jetty lifecycle](https://jetty.org/download.html), [Jetty programming guide](https://jetty.org/docs/jetty/12.1/programming-guide/index.html), [Pixi renderers](https://pixijs.com/8.x/guides/components/renderers), [Pixi accessibility](https://pixijs.com/8.x/guides/components/accessibility), [Phaser](https://docs.phaser.io/phaser/getting-started/what-is-phaser), [Vite](https://vite.dev/guide/), [Node lifecycle](https://nodejs.org/en/about/previous-releases), [React](https://react.dev/learn), [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [Kotlin interoperability](https://kotlinlang.org/docs/java-interop.html), [Protobuf](https://protobuf.dev/programming-guides/proto3/), [WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket), [WebSocket server guidance](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers), [browser audio](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay), and [AWS SDK](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/welcome.html). Claims are attached to relevant links within their reports; these links are also indexed here for retrieval.

## Uncertainty register

| Gap | Why it matters | Resolution milestone |
|---|---|---|
| Full independent startup/match | Database and remote assumptions may remain despite passing state tests | M0 startup plus M1 network observation; full match M3 |
| Complete BB2025 catalog and interaction fidelity | Current implementation/test coverage is partial evidence; one integration case disabled | Per-feature characterization and explicit supported-content matrix, M2–M3 |
| Recipient privacy/authentication under reconnect | Sampled checks are not a full command/payload audit | M1 adversarial local contracts; M4 public route/security tests |
| Persistence/replay crash recovery | Serialization existence is not atomic durability or compatibility | M4 pending-decision restart and backup restore |
| Asset rights, branding and roster-data provenance | Root license and a Noto notice do not establish every content source | Content/qualified review before public distribution |
| Real browser accessibility and artwork readability | Contact sheets omit interaction, motion and actual board context | M1 prototype and M3 desktop tests; mobile M5 |
| Audio playback/loudness and runtime font behavior | Not tested by image inventory or Maven | M3 browser audio/font checks |
| Capacity, budget and retention | Needed for hosting cost and durable operations | Re-estimate after M1 measurements, lock before M4 |

The report package is complete within these disclosed audit limits. Outstanding items are evidence-gathering or implementation gates in the roadmap; they are not represented as completed runtime verification.
