# FUMBBL Rebuild — Project Summary & Export Notes

## 1. Project Overview & Objectives
* **Goal**: Modernize and rebuild the legacy **FUMBBL** (Blood Bowl online tabletop simulator) Java client into a modern, browser-native HTML5/WebGL web application[cite: 1].
* **Core Motivation**: Move away from obsolete Java Web Start (.jnlp) technology while decoupling the core game/rules engine from UI rendering[cite: 1].
* **Target Stack**:
  * **Backend**: Headless Java / Kotlin / Node.js game server wrapped with WebSockets (JSON / Protocol Buffers)[cite: 1].
  * **Frontend**: TypeScript + HTML5 Canvas / Phaser.js / PixiJS (Desktop primary, with landscape mobile responsiveness)[cite: 1, 3].
  * **AI Pair Programming**: Utilizing Google Antigravity & Gemini for codebase analysis, PRD drafting, refactoring, and UI mockups[cite: 1, 3].

---

## 2. Legal & IP Compliance Guardrails
* **Mechanics vs. Expression**: Game mechanics (grid movement, 2D6 tables, dice probabilities, turn structures) are not copyrightable under 17 U.S.C. § 102(b)[cite: 1].
* **IP Protection**: Must avoid Games Workshop (GW) trademarks, logos, exact rulebook text, specific lore/character names (e.g., *Reikland Reavers*, *Warpstone*), and protected visual artwork[cite: 1].
* **FUMBBL Context**: FUMBBL operates in a non-profit fan-project space following a 2009 asset cleanup[cite: 1]. For any published or commercial rebuild, complete re-theming, re-naming of proprietary skills/rosters, and custom icon design are mandatory[cite: 1].

---

## 3. Product & Technical Requirements (PRD Foundations)

### 3.1 Architecture Overview
`mermaid
graph TD
    A[Browser Front-End<br/>TypeScript + Phaser.js / Canvas API + HTML5] <-->|WebSockets<br/>JSON / Protobuf| B[Backend Engine<br/>Java / Spring Boot or Node.js - Headless Server]
`",
",

* /legacy-client: Legacy Java Swing/AWT codebase (reference only)[cite: 1].
* /server-core: Headless Java game state engine and rules processor[cite: 1].
* /web-client: Modern TypeScript / Vite / Phaser.js web frontend[cite: 1].

### 3.3 UI / UX Layout Specifications
* **Style**: High-definition 32-bit crisp retro pixel art style[cite: 2, 4].
* **Desktop View**: Full 16:9 layout featuring a 15x26 pitch grid, dark-mode theme, sidebars for player stat cards, dice panel, and live action logs[cite: 2, 4].
* **Mobile Landscape View**:
  * Viewport centered on active action zone with touch-drag panning and pinch-to-zoom[cite: 2, 4].
  * Compact left panel for active unit stats (MA, ST, AG, AV) & skill badges[cite: 2].
  * Collapsible right drawer for action log & dice rolling history[cite: 2].
  * Bottom thumb-friendly touch action bar (End Turn, Blitz, Pass, Reroll)[cite: 2].

---

## 4. Antigravity Execution Roadmap & Artifacts

### 4.1 Artifacts Workflow
1. PRD.md: Comprehensive product requirements document mapping functional scope, client-server split, and compliance[cite: 3].
2. implementation_plan.md: Technical system design detailing WebSocket packet serialization, headless Java engine decoupling, and Phaser.js grid handlers[cite: 1, 3].
3. 	ask.md: Phased execution task list with interactive checkboxes[cite: 3].
4. walkthrough.md: Auto-generated documentation tracking visual code diffs, API changes, and unit test results[cite: 3].

### 4.2 Phased Sprint Execution Plan
* **Sprint 1: Headless Board State**
  * Strip Swing/AWT components from Java core[cite: 1].
  * Expose pitch grid (15x26) state and initial team positions via JSON packet serialization[cite: 1].
* **Sprint 2: Static Canvas Rendering**
  * Build Vite + TypeScript web client[cite: 1].
  * Connect via WebSockets and render team sprites on a Phaser 3 canvas grid[cite: 1].
* **Sprint 3: Interactivity & Action Loop**
  * Implement player selection, movement pathing overlays, target percentage indicators, and WebSocket MOVE_ACTION payloads[cite: 1].

---

## 5. Key Decisions Summary
1. **Document Standard**: Replaced initial PDR (Preliminary Design Review) concept with a software-centric **PRD (Product Requirement Document)** approach[cite: 3].
2. **Platform & Perspective**: Desktop HTML5 web application is the primary priority, designed with high-res pixel art top-down/isometric views, with secondary responsive landscape mobile support[cite: 2, 4].
3. **Tooling & Environment**: Leverage Antigravity's persistent artifacts, Generative UI preview panel, and browser subagent for end-to-end prototyping and pair programming[cite: 3]. 
