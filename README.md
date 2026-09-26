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
- **Pure-function checker, off any UI thread concern.**
  `MermaidSyntaxChecker.check(text)` is a plain function over text (drives
  `MermaidLexer`'s token stream once), directly unit-testable without a
  platform test fixture; the Annotator is a thin per-file wrapper around
  it.
- **Visual preview directly answers "no other functionality at all."**
  A "Preview" tab renders the diagram via the real, bundled mermaid.js
  (pinned 11.16.1, MIT-licensed, downloaded once and committed --
  `resources/mermaid/`, never fetched at runtime) inside a `JBCefBrowser`.
  First and only use of JCEF in this catalog: it's the one dependency
  worth accepting, since hand-rolling graph layout (Sugiyama/dagre-style)
  would be months of work and look worse than the real thing -- exactly
  the "functionality at half-measure" risk this plugin exists to avoid.
  Re-renders are debounced (~300ms after the last keystroke), and if this
  IDE build doesn't support JCEF, the tab shows a plain fallback message
  instead -- the rest of the plugin (highlighting, validation) is
  completely unaffected either way.
- **Zoom, pan, themes and export where reviewers of the bundled plugin
  keep asking for them.** JetBrains's own Mermaid plugin (1.6M+ downloads,
  bundled with IntelliJ IDEA from 2026.2) is a much richer *editor*, but
  its Marketplace reviews through August 2026 repeatedly ask for preview
  zoom/pan ("Please can we add zoom to this... Ctrl + mouse scroll doesn't
  work either"), a diagram theme independent of the editor theme ("dark
  editor theme but want to create a printable mermaid diagram"), and
  export. This preview does all three. The zoom/pan math lives in
  `resources/preview/companion-preview.js` as pure functions, unit-tested
  in Node (`PreviewScriptTest`).
- **Coexistence instead of a silent takeover.** When two plugins claim the
  same extension, the platform gives it to the one the user installed --
  so without care, this plugin would take `.mmd`/`.mermaid` away from the
  bundled editor. Instead it asks once, changes nothing without a click,
  and keeps its Preview tab available with either editor (it opens by file
  name, not file type).
- **Earlier README claim corrected:** it said v1 had no custom pan/zoom
  because "mermaid.js already has its own". It doesn't; the preview had
  none until 0.2.0.

## Usage

Open a `.mmd`/`.mermaid` file. Structural keywords (`flowchart`,
`subgraph`, `end`, `sequenceDiagram`, `classDiagram`, etc.) and node-shape
delimiters, strings, and comments get their own colors; unterminated
strings, unmatched/mismatched brackets, and subgraphs missing `end` are
flagged as real errors. Click the "Preview" tab for a live rendered view:

- **Zoom:** Ctrl+mouse wheel or trackpad pinch (around the cursor), or the
  toolbar's zoom buttons. **Pan:** drag, or scroll. **Fit:** double-click
  or "Fit Diagram". **100%:** "Actual Size".
- **Theme:** the palette button -- Default, Neutral (printable), Forest,
  Dark -- independent of the IDE theme.
- **Export:** SVG or PNG (2x). Some diagram types can't be rasterized in
  the browser; PNG export then says so, and SVG always works.

If JetBrains's Mermaid plugin is also installed (it's bundled with
IntelliJ IDEA from 2026.2), you're asked once which editor should own
`.mmd`/`.mermaid` files. Switch any time from Tools | "Edit Mermaid Files
with JetBrains Mermaid Editor" / "... with Mermaid Companion".

## Mermaid Companion Pro

An optional paid tier on top of everything above. Nothing that was free
before is behind it.

- **Export on Save.** In the Preview toolbar, open "Export on Save (Pro)"
  and turn on SVG and/or PNG for the diagram. From then on, each time you
  save the file the diagram is rendered again and its export is rewritten
  next to it (`order-flow.mmd` -> `order-flow.svg` / `order-flow.png`, the
  same names the manual export suggests).
  - A diagram that doesn't render (a syntax error while you type) leaves
    the existing exports exactly as they are, and the toolbar says so.
  - The exported SVG carries no per-render id, so saving a diagram that
    didn't change normally leaves the file, and version control, alone.
  - "Sync Now" updates the exports on demand; the toolbar shows when they
    were last synced.
  - Turning it on asks first if the export file already exists, since it
    will be overwritten on every save.
  - It is remembered per project, for you -- not shared with your team
    through version control -- and it runs once the file's Preview tab has
    been opened in the IDE session, because the preview is what renders
    the diagram.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.

Bundles [mermaid.js](https://github.com/mermaid-js/mermaid) 11.16.1,
MIT-licensed -- see `src/main/resources/mermaid/LICENSE-mermaid.txt`.
