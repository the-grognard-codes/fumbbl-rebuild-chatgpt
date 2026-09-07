# Humans, Orcs and match-screen concepts

Preliminary visual exploration, 2026-09-06. Produced with the built-in image-generation tool for owner review; [exact prompts and reference chain](prompts.md). All three images are 1536 × 1024 PNG concept boards. These are neither executable UI nor production-ready sprite atlases. Existing game assets remain unchanged.

## Human team

Blue/ivory sample uniform; lineman, blitzer, catcher and thrower silhouettes; a braced/running pose comparison.

![Human concept sheet](humans-concept-v1.png)

## Orc team

Rust/charcoal sample uniform; lineman, blitzer, blocker and thrower silhouettes; heavier proportions distinguish the team from Humans. Roles are sample art archetypes, not a validated or complete BB2025 roster.

![Orc concept sheet](orcs-concept-v1.png)

## Pitch, dugouts and match controls

Restrained grass field, upright players, separate home/away dugouts, score/half/turn/reroll indicators, selected-player card, action buttons and log. These are illustrative positions and available actions, not an engine-validated match situation.

![Desktop match concept](match-screen-concept-v1.png)

## Visual review and next production trial

**Observed strengths:** contrasting team silhouettes, common equipment treatment, pitch-first layout, labeled dugout states and a visible selected-player outline. Humans and Orcs share the same broad art treatment while remaining distinguishable.

**Observed limitations:** the sheets have more texture and fine detail than a strict low-resolution sprite palette; real-size readability has not been validated. The Human sheet's generated "32px" scale label is illustrative and must not define the sprite contract. Pose studies are not registered animation frames. Palette variants have not been exported. None of these opaque presentation sheets is a transparent, sliceable atlas.

The match-screen grid is illustrative: exact 26 × 15 square geometry, line placement, coordinates, wide zones, player/ball alignment and path legality must be defined by renderer code. End-zone symbols are exploratory, not selected branding. Generated serif/condensed text differs from the requested clean sans-serif direction; production labels will be DOM text and should use a readable system sans-serif first. Some borders/labels need stronger contrast; dark decorative chrome should remain restrained. Dugout capacity, role eligibility, action availability and all numbers come from application state in production, not this image. No accessibility or mobile usability validation is implied.

**Next trial:** choose a native sprite cell after placing a few cleaned Human/Orc sprites on the actual board, test small/normal/large silhouettes, then export a short pose set with matching origins and transparent edges. Verify integer zoom, ball/status overlays, home/away differentiation and readable controls. This is a small art-production trial; the approximately 30-team sprite/color-variation sprint remains separate.

**Owner review focus:** preferred amount of pixel detail, Human/Orc silhouettes, pitch texture, dugout placement and overall panel density. The sample colors are provisional. No final team name or branding decision is needed for M0/M1.

See [implementation kickoff](../overhaul-analysis/09-implementation-kickoff.md) for accepted architecture and engineering slices.
