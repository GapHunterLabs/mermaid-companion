"""Renders sample diagrams with every theme of the preview in headless Chromium and measures the
contrast of what mermaid.js actually drew: each text element against the fill of the shape behind it
(or the theme's background), WCAG ratio. Also checks that a theme the diagram declares itself
(%%{init}%% or front matter) wins over the toolbar's.

Exits 1 when a preset's text drops under 4.5:1, or when a diagram's own theme does not win.

Usage: python -I tools/theme-check.py <repo-dir>
"""
import pathlib
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
    "flowchart": "flowchart LR\n  A[Start] -->|label| B{Check}\n  subgraph G [Group]\n    C((Done))\n  end\n  B --> C",
    "sequence": "sequenceDiagram\n  participant Alice\n  Alice->>Bob: Hello\n  Note right of Bob: thinking\n  Bob-->>Alice: Hi",
    "class": "classDiagram\n  class Animal {\n    +name\n  }\n  Animal <|-- Duck : extends",
    "state": "stateDiagram-v2\n  [*] --> Idle\n  Idle --> Busy : go",
    "er": "erDiagram\n  CUSTOMER ||--o{ ORDER : places",
}
PRESETS = ("high-contrast", "blueprint", "monochrome")

# For each visible text: its color, and the fill of the nearest shape under it (else the page background).
MEASURE = r"""(bg) => {
  const lum = (c) => { const m = c.match(/rgba?\(([^)]+)\)/); if (!m) return null; const p = m[1].split(',').map(Number);
    if (p.length > 3 && p[3] === 0) return null;
    const v = p.slice(0, 3).map((x) => x / 255).map((x) => x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4));
    return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]; };
  const ratio = (a, b) => (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  const bgL = lum(bg);
  const out = [];
  const texts = [...document.querySelectorAll('#stage svg text, #stage svg foreignObject span, #stage svg foreignObject p')]
    .filter((t) => (t.textContent || '').trim() && t.getBoundingClientRect().width > 0);
  for (const t of texts) {
    const isSvg = (e) => e.namespaceURI === 'http://www.w3.org/2000/svg';
    // SVG text shows its fill; HTML labels (inside foreignObject) show their color.
    // A <text> can carry the shape's class (sequence actors): the color shown is its <tspan>'s.
    const shown = (isSvg(t) && t.querySelector('tspan')) || t;
    const tl = lum(isSvg(t) ? getComputedStyle(shown).fill : getComputedStyle(t).color);
    if (tl === null) continue;
    const r = t.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    let behind = bgL, what = 'background';
    for (const s of document.querySelectorAll('#stage svg rect, #stage svg polygon, #stage svg circle, #stage svg path.basic, #stage svg .label-container, #stage svg .labelBkg')) {
      if (isSvg(s) && !['rect', 'polygon', 'circle', 'ellipse', 'path'].includes(s.tagName)) continue; // a <g> paints nothing itself
      const b = s.getBoundingClientRect();
      if (b.width < 4 || cx < b.left || cx > b.right || cy < b.top || cy > b.bottom) continue;
      const cs = getComputedStyle(s);
      const f = lum(isSvg(s) ? (cs.fill && cs.fill !== 'none' ? cs.fill : '') : (cs.backgroundColor || ''));
      if (f !== null) { behind = f; what = s.tagName + '.' + ((s.getAttribute('class') || '').split(' ')[0]); }
    }
    out.push([(t.textContent || '').trim().slice(0, 16), ratio(tl, behind), what]);
  }
  return out;
}"""

failures = []
with sync_playwright() as pw:
    browser = pw.chromium.launch()
    page = browser.new_page(viewport={"width": 1200, "height": 900})
    page.set_content(html)
    page.evaluate("window.__m = []; window.gapHunterBridge = (p) => window.__m.push(p);")
    for theme in ("default", "neutral", "forest", "dark", "neo", "neo-dark", "redux", "redux-dark", "redux-color", "redux-dark-color") + PRESETS:
        page.evaluate("(t) => window.gapHunterSetTheme(t)", theme)
        worst = None
        for name, text in SAMPLES.items():
            page.evaluate("(t) => window.gapHunterRender(t)", text)
            time.sleep(0.6)
            bg = page.evaluate("getComputedStyle(document.body).backgroundColor")
            for label, ratio, what in page.evaluate(MEASURE, bg):
                if worst is None or ratio < worst[0]:
                    worst = (ratio, name, label, what)
        flag = ""
        if theme in PRESETS and worst and worst[0] < 4.5:
            flag = "  FAIL"
            failures.append("%s: %.2f:1 for '%s' in %s on %s" % (theme, worst[0], worst[2], worst[1], worst[3]))
        print("%-14s lowest text contrast %.2f:1 (%s, '%s' on %s)%s" % (theme, worst[0], worst[1], worst[2], worst[3], flag))

    # A theme written in the diagram wins over the toolbar's.
    page.evaluate("window.gapHunterSetTheme('blueprint')")
    fills = {}
    for label, text in (("plain", "flowchart LR\n  A --> B"),
                        ("init forest", "%%{init: {'theme': 'forest'}}%%\nflowchart LR\n  A --> B"),
                        ("front matter forest", "---\nconfig:\n  theme: forest\n---\nflowchart LR\n  A --> B")):
        page.evaluate("(t) => window.gapHunterRender(t)", text)
        time.sleep(0.6)
        fills[label] = page.evaluate("getComputedStyle(document.querySelector('#stage g.node rect, #stage g.node polygon')).fill")
    page.evaluate("window.gapHunterSetTheme('forest')")
    page.evaluate("(t) => window.gapHunterRender(t)", "flowchart LR\n  A --> B")
    time.sleep(0.6)
    forest = page.evaluate("getComputedStyle(document.querySelector('#stage g.node rect, #stage g.node polygon')).fill")
    print("node fill: blueprint %s, diagram says forest %s / %s, forest itself %s" % (fills["plain"], fills["init forest"], fills["front matter forest"], forest))
    for label in ("init forest", "front matter forest"):
        if fills[label] != forest:
            failures.append("%s: the diagram's own theme did not win (%s, expected %s)" % (label, fills[label], forest))
    browser.close()

for f in failures:
    print("PROBLEM", f)
sys.exit(1 if failures else 0)
