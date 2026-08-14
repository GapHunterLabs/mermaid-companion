<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Mermaid Companion Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.2...HEAD
[0.1.2]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/mermaid-companion/commits/0.1.0
