# Mermaid Companion

Syntax highlighting and real syntax validation for `.mmd`/`.mermaid`
diagram source files (flowchart, sequence, and class diagrams).

## Why it exists

**Mermaid Studio** (JetBrains Marketplace id 29870), 11,168 downloads,
paid, vendor Tachi Labs. Real, verbatim reviewer complaints:

- *"the plugin shows false warnings for valid non-ASCII subgraph
  identifiers... e.g. for subgraph á it warns for 'Subgraph identifier á
  contains special characters...'"* (2026-02-16, 2026-02-23 -- the same
  bug persisted across two separate reviews)
- *"shows false errors for valid node shapes where the node contains
  non-ASCII characters"* -- valid nested quotes, accepted by
  mermaid.live, marked as an error
- *"It opens and shows me a bunch of icons on page. That's it. No other
  functionality at all."* (1 star, 2026-03-30)

## Why built this way

- **Unicode-aware identifier lexing, structurally, not as a special
  case.** `MermaidLexer.scanIdentifier` uses `Char.isLetterOrDigit`
  (Unicode-aware) and there is no separate "is this identifier valid"
  ASCII character-class check anywhere in this plugin. The competitor's
  bug -- flagging `subgraph á` or a café/日本語 label as invalid -- has
  nowhere to hide in this design, and `MermaidSyntaxCheckerTest` has
  fixtures reproducing the exact reported cases and asserting zero
  issues.
- **Real validation, not just "we don't check anything so nothing is
  ever wrong."** `MermaidSyntaxChecker` tracks unterminated string
  literals, unmatched/mismatched node-shape brackets (`[]`/`()`/`{}`,
  with proper LIFO kind-matching so valid nested shapes like
  `id[(cylinder)]` are never flagged), and `subgraph` blocks missing
  their `end` -- genuine syntax errors, checked correctly against real
  Mermaid grammar instead of a naive ASCII regex.
- **v0.1 is validation only, no visual diagram preview** -- directly
  targets the "opens and shows a bunch of icons, no other functionality"
  complaint by making sure the one thing it does (tell you if your
  diagram source is syntactically valid) actually works correctly,
  rather than promising a rendering engine on top of it.
- **Pure-function checker, off any UI thread concern.**
  `MermaidSyntaxChecker.check(text)` is a plain function over text (drives
  `MermaidLexer`'s token stream once), directly unit-testable without a
  platform test fixture; the Annotator is a thin per-file wrapper around
  it.

## Usage

Open a `.mmd`/`.mermaid` file. Structural keywords (`flowchart`,
`subgraph`, `end`, `sequenceDiagram`, `classDiagram`, etc.) and node-shape
delimiters, strings, and comments get their own colors; unterminated
strings, unmatched/mismatched brackets, and subgraphs missing `end` are
flagged as real errors.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**kennyj.diazm@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
