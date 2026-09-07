# Pitch and match UI — owner feedback, 2026-09-07

**Status: layout direction accepted by the owner, 2026-09-07.** After reviewing
`pitch-review-v2.html`, the owner said: “Layout example looks great, we should
continue to build on that as we move forward.” Use concept 02 as the baseline
for subsequent match-screen implementation and art integration. Refine it as
real match states and viewport tests provide evidence; preserve the accepted
pitch-first layout, compact bench and expandable shared log/chat arrangement.
This approval does not establish completed production or accessibility testing.

The owner approves the original Human and Orc concept direction. Preserve the
Human blue/ivory and Orc rust/charcoal palettes, pixel treatment, perspective and
equipment language. Added companion sheets introduce Human Ogre/Halfling and
Orc Troll/Goblin. These are preliminary position designs, not changes to roster
eligibility, costs, skills or team-building rules.

The following requirements supersede the illustrative geometry and panel
proportions of `match-screen-concept-v1.png`. The old image remains a mood/art
reference. [Concept 02](pitch-review-v2.html) is a self-contained local layout
study, without game-engine connections, network calls or new dependencies.

## Board geometry and alignment

- Exactly **26 columns × 15 rows**, with left/right end zones occupying columns
  0 and 25. Each end zone is one square deep; those squares have the same width
  and height as every other pitch square. Decorative banners must sit outside
  the playable grid or stay within the existing end-zone cells.
- The halfway line lies between columns 12 and 13. It is a boundary, not an
  extra column or a center square.
- Two **dotted horizontal lines span left to right**, at the boundaries after
  row 4 and row 11 when counting rows from the top. Using zero-based geometry,
  draw at `y = 4 × cellSize` and `y = 11 × cellSize` across the full pitch.
  This leaves **4 wide-zone rows / 7 central rows / 4 wide-zone rows**.
- Anchor every player to its actual square: the logical cell center is
  `((column + 0.5) × cellSize, (row + 0.5) × cellSize)`. Sprite foot/base origins
  must use this same anchor. Selection markers, ball position and skill labels
  follow the cell anchor, not the sprite image's bounding box. Large players
  still occupy one logical square; visual height must not imply extra occupied
  cells. Concept tokens demonstrate exact centers; the sprite footprint trial
  remains future artwork work.
- One shared view transform moves/scales the pitch, players, overlays and grid.
  Convert pointer coordinates through the inverse transform before hit testing.
  Pan/zoom must never mutate game coordinates or selection state.

## Optional coordinates

Coordinates are a player setting and default **off in this proposal**. The owner
requires counting outward from halfway, rather than the old image's A–Z pitch
length. The proposed explicit convention is:

```text
Left end zone                    halfway                    Right end zone
13 12 11 10 9 8 7 6 5 4 3 2 1   |   1 2 3 4 5 6 7 8 9 10 11 12 13
```

There is no zero column. The nearest cell on either side is 1; each end zone is
13. Both top and bottom edges repeat the same distance labels. Optional A–O
row labels disambiguate the 15 rows; left/right half identity disambiguates
duplicated distance values. These are display labels, not a change to engine or
protocol coordinates. The 1–13 convention is the current design interpretation
of the requested midpoint count, not a claim about every classic-client setting.

## Usable desktop area, zoom and pan

Target a 1920 × 1080 desktop display, but size the application against the
**actual browser content viewport in CSS pixels**, not physical screen height.
Taskbars, browser tabs/address/bookmark bars, application headers and OS/browser
scaling reduce usable space. Do not hardcode a 1080-pixel game canvas.

Design checks should include 1920 × 900 and 1920 × 820 content viewports, plus a
smaller CSS viewport such as 1280 × 660 to reflect scaling or a reduced window.
These are test budgets, not assumed measurements of the owner's browser chrome.
Keep critical controls visible without requiring page scrolling at the target
desktop sizes. The review page's own title/control band is extra concept chrome;
it will not be part of the final match screen.

Fit cell size to both available dimensions:

```text
pitchWidth  = viewportWidth - sidePanel - gaps - outerPadding - coordinateGutters
pitchHeight = viewportHeight - appHeader - toolbar - benchStrip - gaps - coordinateGutters
fitCellSize = min(pitchWidth / 26, pitchHeight / 15)
```

Never independently stretch pitch width/height. Keep cells square. Provide Fit
pitch, zoom in/out, a zoom indicator and bounded drag panning. Preserve the view
anchor and selection through resizing, and provide keyboard equivalents. Concept
02 demonstrates 100–200% of Fit, pointer dragging, arrow-key panning, +/− zoom and
0 to reset. The height selector changes only the simulated available height;
width remains the actual window width.

For production sprites, choose a native cell size during the art trial and
validate integer sprite scaling. If a strict integer scale would make Fit
unreadable, retain a fit overview plus a crisp zoomed view; do not decide the
native sprite size from the old generated “32px” label. Skill text/UI should be
screen-space readable at every supported zoom, with full details available on
selection/focus. The review uses vector tokens, so it does not validate raster
scaling or final sprite readability.

## Skill notation and settings

Show short skill abbreviations adjacent to players. Provide an overall toggle,
per-skill visibility, editable abbreviations/order, and Restore defaults. Save
preferences per player in the eventual client. Suggested defaults:

| Skill | Abbreviation | Initially displayed |
|---|---|---|
| Mighty Blow | MB | Yes |
| Guard | G | Yes |
| Wrestle | W | Yes |
| Dodge | D | Yes |
| Block | B | Yes |

The first four abbreviations are owner-specified; Block is a proposed additional
default. A skill label must reflect actual match data in the product. The
concept assigns synthetic skills solely to test the display. Keep player numbers
and skill badges separate. Preserve skill names in accessible/selected-player
details; color alone must not identify a skill. Crowded combinations need a
defined overflow/priority treatment and expandable full details before release.

Concept 02 supports the overall toggle, per-skill visibility, editable 1–3
character abbreviations and Restore defaults. Settings last only for that open
preview; persistent storage, ordering, overflow and the full skill catalog remain
client implementation work. This does not expand the production browser milestone.

## Bench, history and chat

- Replace the large permanently visible dugouts with a compact bottom strip
  showing reserves/KO/casualty counts for each side. A Roster & bench control
  opens the detailed drawer when needed. In the real game, necessary setup,
  reserve selection and KO/recovery decisions must still expose the relevant
  player controls automatically; compactness must not hide a required action.
- Keep selected-player details compact at the top of a side panel. Give most
  of that panel's height to the match log, with a readable scrollable history.
- Share the larger history panel between **Match log** and **Chat** tabs. Chat
  is required but may be hidden/toggled; hiding it must preserve history/draft
  and show an unread indicator in the eventual client. Provide a message
  composer, local draft and clear send/connection state in the product.
- Expand the active log/chat panel into a large overlay, and allow collapse
  without losing the previous pitch view or reading position. Full match
  history must remain reachable, with Match start / Latest navigation and
  eventual turn/event navigation. Do not discard earlier entries to keep the
  small panel fast. Virtualization/paging may be used for long histories while
  maintaining full-history access and scroll position.
- New log entries must not steal scroll position while reading older events;
  show a new-events indicator and offer an explicit jump to Latest.

Concept 02 demonstrates a 330-pixel side panel (280 at narrower widths), a
compact 48-pixel bench row, full-height scrolling through 48 illustrative log
entries, Match start / Latest, expansion/collapse, a toggleable sample chat tab,
and a bench drawer. Chat has sample messages only; no composer, messaging,
reconnection, persistence or historical game loading is implemented.

## Review status and next checks

The two new position sheets were visually inspected against their respective
approved originals and saved with their exact image-generation prompts. Original
images were preserved. Their painted presentation tiles are not the pitch grid
specification or mechanically registered sprite assets.

The browser tool blocked navigation to the local HTML preview under its URL
policy. No alternative browser route was used to bypass that block. Offline
script/geometry/control checks are recorded in `pitch-review-checks.txt` (rerun
from repository root with `node .notes/art-preview/check-pitch-review.cjs`); actual
browser rendering at the viewport budgets above remains unverified. Open
`pitch-review-v2.html` locally for visual review, including label crowding,
keyboard/focus behavior and actual usable-height fit.

Next art trial: registered transparent sprites for small, normal and large
positions, with visible skill overlays on the exact board at practical zoom
levels. Production engineering should carry these requirements into the accepted
React/Pixi view, with board transformations independent of engine state. No
server, database, rules, runtime tooling or unrelated in-progress edits were
changed for this art/UI review.
