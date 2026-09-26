<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Mermaid Companion Changelog

## [Unreleased]

## [2026.1.0]

### Added

- **Mermaid Companion Pro** (optional paid tier; everything that was free
  stays free): **Export on Save** keeps a diagram's SVG and/or PNG export
  up to date every time you save the file, written next to it under the
  same name the manual export suggests. A diagram that doesn't render
  leaves the existing exports untouched, and an unchanged diagram leaves
  the files alone. "Sync Now" in the Preview toolbar updates them on
  demand.

### Fixed

- Review/star CTA now links to this plugin's own Marketplace reviews page
  instead of the vendor's generic plugin list.
- A diagram that doesn't parse no longer leaves mermaid's own "Syntax error"
  graphic behind in the Preview -- one more for every pause while typing an
  invalid diagram. The error is shown in the bar above the last good render,
  as before.
- The Export on Save message ("Turn on SVG or PNG first", "Pro feature") no
  longer stays in the toolbar after you turn a format on or off.

## [0.2.0]

### Added

- Zoom and pan in the Preview tab: Ctrl+mouse wheel (or trackpad pinch)
  zooms around the cursor, dragging or scrolling pans, double-click or
  "Fit Diagram" shows the whole diagram, and "Actual Size" goes back to
  100%. The current zoom level is shown in the toolbar, and editing the
  diagram keeps your zoom and position.
- Diagram theme picker in the Preview toolbar -- Default, Neutral
  (printable), Forest, Dark -- independent of the IDE's editor theme,
  remembered across sessions.
- Export the rendered diagram as SVG or PNG (2x resolution), saved next to
  the source file by default.
- A diagram that stops parsing while you type no longer blanks the
  preview: the last good render stays on screen, with the error shown
  above it.
- Works alongside JetBrains's own Mermaid plugin (bundled with IntelliJ
  IDEA from 2026.2). When both are installed, the platform gives
  .mmd/.mermaid files to this plugin, which can hide the bundled editor's
  completion, formatting and rename. Mermaid Companion now asks once which
  editor should own those files; nothing changes without your click, and
  Tools | "Edit Mermaid Files with ..." switches back and forth at any
  time. The Preview tab (zoom, themes, export) stays available with either
  editor.

### Fixed

- On IntelliJ IDEA 2026.2 and later the Preview tab didn't open at all:
  2026.2 moved the embedded browser (JCEF) into its own bundled plugin,
  which Mermaid Companion now declares as a dependency. If the embedded
  browser still can't be reached, the tab now shows a message instead of
  silently not appearing.
- Opening a .mmd file could freeze the IDE for several seconds: the
  preview's embedded browser was started, and mermaid.js read, as soon as
  the file opened, even with the Text tab in front. The browser now starts
  the first time you open the Preview tab, and mermaid.js is read in the
  background.
- The README said the preview needed no custom pan/zoom because
  "mermaid.js already has its own". It doesn't -- the preview had no zoom
  or pan at all until this release.

## [0.1.3]

### Added

- Review/star CTA: after 10 distinct real signals of use -- either a
  real syntax error found by the annotator, or a successful diagram
  render in the Preview tab -- a one-time notification asks whether to
  rate the plugin on Marketplace, with a permanent "Don't ask again"
  option.

## [0.1.2] - 2026-08-09

### Fixed

- Plugin crashed on load in IntelliJ 2026.2+ (build 262.9437.65 and
  newer): JetBrains now bundles native Mermaid support using the same
  `Mermaid` Language ID this plugin used, causing an
  `ImplementationConflictException` the moment both were installed
  together. The internal Language ID (and matching FileType name) is
  now `MermaidCompanion` — no user-visible change (file associations,
  extensions, and the Preview tab all work exactly as before).

## [0.1.1]

### Added

- Visual diagram preview: a "Preview" tab next to the text editor renders
  the diagram live via bundled mermaid.js 11.16.1 (MIT, pinned, never
  fetched at runtime) inside a `JBCefBrowser`. Debounced re-render
  (~300ms) on edit; falls back to a plain message on IDE builds without
  JCEF support, with the rest of the plugin (lexer, highlighting, syntax
  validation) unaffected either way.

### Fixed

- Preview showing blank on first open: now waits for the `JBCefBrowser`
  page load to finish before executing the render script.

## [0.1.0]

### Added

- Syntax highlighting for Mermaid flowchart/sequence/class diagrams via a
  hand-rolled, Unicode-aware lexer.
- Known-keyword highlighting (`flowchart`, `subgraph`, `sequenceDiagram`,
  `classDiagram`, etc.).
- Real syntax validation: unterminated string literals,
  unmatched/mismatched node-shape brackets, and `subgraph` blocks missing
  their `end`.

[Unreleased]: https://github.com/GapHunterLabs/mermaid-companion/compare/2026.1.0...HEAD
[2026.1.0]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.2.0...2026.1.0
[0.2.0]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.3...0.2.0
[0.1.3]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/mermaid-companion/commits/0.1.0
