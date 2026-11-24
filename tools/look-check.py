"""Checks the diagram looks through the plugin's real preview page in headless Chromium:

  - handDrawn and neo each change at least one diagram type (the report lists which ones: not every
    type has every look in mermaid.js);
  - with every look, two SVG exports of the same diagram are byte-identical (normalized like
    SvgNormalizer.kt), so Export on Save leaves an unchanged diagram alone;
  - a look the page does not know is classic;
  - a look the diagram sets in its own front matter wins over the toolbar's.

Exits 1 on any failure.

Usage: python -I tools/look-check.py <repo-dir>
"""
import pathlib
import re
import sys
import time

from playwright.sync_api import sync_playwright

sys.stdout.reconfigure(encoding="utf-8")
repo = pathlib.Path(sys.argv[1])
lib = (repo / "src/main/resources/mermaid/mermaid.min.js").read_text(encoding="utf-8")
js = (repo / "src/main/resources/preview/companion-preview.js").read_text(encoding="utf-8")
html = ('<html><head><style>html, body { margin: 0; height: 100%; overflow: hidden; }'
        '#viewport { position: absolute; inset: 0; overflow: hidden; } #stage { position: absolute; left: 0; top: 0; }'
        '</style></head><body><div id="error"></div><div id="viewport"><div id="stage"></div></div>'
        '<script id="mermaid-lib">' + lib + '</script><script>' + js + '</script></body></html>')
SAMPLES = {
    "flowchart": "flowchart LR\n  A[Start] -->|label| B{Check}\n  B --> C((Done))\n  B --> D>Flag]",
    "class": "classDiagram\n  class Animal {\n    +name\n  }\n  Animal <|-- Duck",
    "state": "stateDiagram-v2\n  [*] --> Idle\n  Idle --> Busy : go\n  Busy --> [*]",
    "er": "erDiagram\n  CUSTOMER ||--o{ ORDER : places",
    "sequence": "sequenceDiagram\n  Alice->>Bob: Hello\n  Bob-->>Alice: Hi",
}
RENDER_ID = re.compile(r"gap-hunter-mermaid-(?:svg|png)-\d+")


def wait(page, start, prefixes):
    for _ in range(600):
        for m in page.evaluate("window.__m.slice(%d)" % start):
            if any(m.startswith(p) for p in prefixes):
                return m
        time.sleep(0.02)
    return None


def export(page, text, token):
    n = page.evaluate("window.__m.length")
    page.evaluate("([t, k]) => window.gapHunterRender(t, k)", [text, token])
    if not (wait(page, n, ["rendered:%d" % token, "renderfailed:%d" % token]) or "").startswith("rendered:"):
        return None
    n = page.evaluate("window.__m.length")
    page.evaluate("window.gapHunterExportSvg('sync')")
    m = wait(page, n, ["syncsvg:", "syncerror:"])
    return RENDER_ID.sub("mermaid-diagram", m[len("syncsvg:"):]) if m and m.startswith("syncsvg:") else None


failures = []
with sync_playwright() as pw:
    browser = pw.chromium.launch()
    page = browser.new_page(viewport={"width": 1200, "height": 900})
    page.set_content(html)
    page.evaluate("window.__m = []; window.gapHunterBridge = (p) => window.__m.push(p);")
    token = 0
    drawn = {}
    for look in ("classic", "handDrawn", "neo", "no-such-look"):
        page.evaluate("(l) => window.gapHunterSetLook(l)", look)
        stable = 0
        for name, text in SAMPLES.items():
            token += 1
            a = export(page, text, token)
            token += 1
            b = export(page, text, token)
            if a is None or b is None:
                failures.append("%s/%s: no export" % (look, name))
                continue
            drawn[(look, name)] = a
            if a == b:
                stable += 1
            else:
                failures.append("%s/%s: two exports differ" % (look, name))
        print("%-13s stable exports %d of %d" % (look, stable, len(SAMPLES)))
    # Not every diagram type has every look in mermaid.js: report which ones change, fail only if none does.
    for look in ("handDrawn", "neo"):
        changed = [n for n in SAMPLES if drawn.get((look, n)) != drawn.get(("classic", n))]
        print("%-9s changes: %s; same as classic: %s" % (look, changed, [n for n in SAMPLES if n not in changed]))
        if not changed:
            failures.append("%s changes no diagram type" % look)
    for name in SAMPLES:
        if drawn.get(("no-such-look", name)) != drawn.get(("classic", name)):
            failures.append("%s: an unknown look is not classic" % name)

    # The diagram's own look wins.
    page.evaluate("window.gapHunterSetLook('classic')")
    token += 1
    own = export(page, "---\nconfig:\n  look: handDrawn\n---\n" + SAMPLES["flowchart"], token)
    page.evaluate("window.gapHunterSetLook('handDrawn')")
    token += 1
    toolbar = export(page, SAMPLES["flowchart"], token)
    strokes = lambda svg: len(re.findall(r"<path", svg or ""))
    print("paths: front matter handDrawn under classic %d, toolbar handDrawn %d, classic %d"
          % (strokes(own), strokes(toolbar), strokes(drawn.get(("classic", "flowchart")))))
    if strokes(own) <= strokes(drawn.get(("classic", "flowchart"))):
        failures.append("the diagram's own look did not win")
    browser.close()

for f in failures:
    print("PROBLEM", f)
sys.exit(1 if failures else 0)
