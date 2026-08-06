<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Mermaid Companion Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.0...0.1.1

## [0.1.0]

### Added

- Syntax highlighting for Mermaid flowchart/sequence/class diagrams via a
  hand-rolled, Unicode-aware lexer.
- Known-keyword highlighting (`flowchart`, `subgraph`, `sequenceDiagram`,
  `classDiagram`, etc.).
- Real syntax validation: unterminated string literals,
  unmatched/mismatched node-shape brackets, and `subgraph` blocks missing
  their `end`.

[Unreleased]: https://github.com/GapHunterLabs/mermaid-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/mermaid-companion/commits/0.1.0
