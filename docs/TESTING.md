# Testing Mermaid Companion

I keep three kinds of tests in this repository. All of them run headless: none needs a
running IDE, and none sends anything over the network.

## 1. The normal suite

```
./gradlew test
```

Runs every unit test in `src/test` in about a minute. Tests that drive
`src/main/resources/preview/companion-preview.js` run it in Node; on a machine without
Node they are skipped, not failed.

What it covers:

| Area | Tests |
| --- | --- |
| Lexer | `MermaidLexerTest`, `MermaidLexerNegativeTest`: tokens are contiguous, never empty and never past the end, for random input, lone surrogates, BOM, CRLF and restarts in the middle of a buffer. |
| Syntax checker | `MermaidSyntaxCheckerTest`, `MermaidSyntaxCheckerNegativeTest`: empty and minimal files, every diagram type, brackets at every nesting depth up to 64 (and 10,000 without recursion), comments, directives, front matter, CRLF, BOM, tabs, Unicode identifiers and labels (accents, CJK, emoji, RTL, combining marks, surrogate pairs), and the free-text positions of sequence and class diagrams. |
| Checker against mermaid.js | `MermaidCorpusTest`, see below. |
| Licensing | `ProGateTest`, `LegacyFreeFeaturesBoundaryTest`: the exact cutoff days (November 2 and November 9, 2026), one day before and after, missing and corrupted first-run values, and a clock set in the future. |
| Export on Save | `SyncCoordinatorTest`, `SyncWriterTest`, `SyncWriterNegativeTest`: a render failure exports nothing, unusual file names (spaces, accents, CJK, emoji, RTL), a read-only export (Windows), a folder where the export should be, empty content. |
| Preview page | `PreviewScriptTest`, `PreviewScriptNegativeTest`: zoom, pan, fit and 100% with zero, negative, NaN, infinite and huge values; the mermaid.js configuration every render uses; the "maximum text size" stand-in diagram; PNG sizes the canvas cannot hold. |
| Bridge | `BridgeMessageSyncTest`, `BridgeMessageNegativeTest`: malformed messages from the page never reach the file system. |

## 2. The checker against mermaid.js (corpus)

`src/test/resources/corpus/` holds one diagram per `.mmd` file: 23 of the diagram types the
bundled mermaid.js 11.16.1 ships (flowchart through treemap), one it does not (zenuml), and
edge cases. `mermaid-verdicts.tsv` records what
mermaid.js itself said about each file. `MermaidCorpusTest` (part of the normal suite)
compares the checker with those verdicts:

- a diagram mermaid.js accepts must get no error from the checker (no false positives);
- the diagrams mermaid.js rejects and the checker misses must be exactly the ones listed in
  `MermaidCorpusTest.KNOWN_FALSE_NEGATIVES`, each with the reason it is out of scope.

To regenerate the verdicts after adding a corpus file or updating mermaid.js, I render the
corpus through the plugin's real preview page in headless Chromium:

```
pip install playwright
python -m playwright install chromium
python -I tools/render-corpus.py . src/test/resources/corpus build/corpus-report.tsv
```

Besides `mermaid-verdicts.tsv`, the report lists for each diagram whether it rendered, how
long it took, whether two renders gave a byte-identical SVG export, and whether its PNG
export worked.

`tools/stress-preview.py` renders large diagrams (hundreds of edges, the 50,000 character
limit) through the same page and reports render and PNG export times.

## 3. The preview in a real browser

These scripts load the plugin's real preview page (the bundled mermaid.js and
`companion-preview.js`) in Playwright's headless Chromium. Each exits 1 when a check fails.

| Script | What it checks |
| --- | --- |
| `tools/render-corpus.py` | Every corpus diagram: mermaid.js's verdict, render time, and whether two SVG and two PNG exports are byte-identical. |
| `tools/click-corpus.py` | Clicks every node, subgraph and participant of the corpus with the mouse: each click must name something written in the diagram, a 20 px drag must send nothing, and no click may load another page. Writes `click-targets.tsv`, which `SourceLocatorCorpusTest` reads to check that the plugin finds every target in its diagram's text. |
| `tools/theme-check.py` | Renders five diagram types with every theme and measures the contrast of the text mermaid.js actually drew against the shape behind it. A preset under 4.5:1 fails, and so does a diagram whose own theme (`%%{init}%%` or front matter) does not win. |
| `tools/look-check.py` | Classic, Hand-Drawn and Neo: exports stay byte-identical with each look, an unknown look is classic, and a look set in the diagram's front matter wins. Reports which diagram types each look changes. |
| `tools/stress-preview.py` | Large diagrams: render and PNG export times. |

```
python -I tools/click-corpus.py . src/test/resources/corpus
python -I tools/theme-check.py .
python -I tools/look-check.py .
```

## 4. The stress suite

```
./gradlew test -Pstress=true --tests '*StressTest'
```

`MermaidCheckerStressTest` is skipped unless `-Pstress=true` is set. It times the syntax
checker, which the editor runs on every edit, on realistic files and on inputs nobody
should have to type: 100,000 nodes, a single 5.5 MB line, nesting 200,000 levels deep,
hundreds of thousands of unterminated strings and directives, and 10 MB of random
characters. Each case prints one line:

```
STRESS|<case>|<characters>|<median ms>|<issues>
```

The asserted time limits are generous on purpose, so the suite passes on a slow machine;
the printed numbers are the result.

## Known gaps

- The preview's browser wiring is tested in Node against a fake DOM and a fake mermaid.js,
  and the corpus runs in Playwright's Chromium. Neither is the Chromium build bundled with
  each IDE (JCEF), which may have other canvas limits.
- The read-only export test runs on Windows only, where replacing a read-only file fails.
  I have not verified Linux and macOS, where a rename may replace the file whatever its
  own permissions.
- Writing the first-run date needs the IDE's settings store; only the decision made from
  that date is unit-tested.
