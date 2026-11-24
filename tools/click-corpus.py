"""Clicks every node, subgraph and participant of each corpus diagram through the plugin's real
preview page in headless Chromium, and checks what the page sends back:

  - a click on a clickable element sends goto:name:<id> or goto:index:<n>;
  - the name is written in the diagram's text as a whole word (what SourceLocator.kt looks for);
  - a drag of 20 px from the same point sends nothing.

Writes <corpus-dir>/click-targets.tsv (file, kind, value), which SourceLocatorCorpusTest reads to
check that the plugin finds every one of them in the diagram's text.

Prints one line per diagram and a summary; exits 1 if a name is missing from its text or a drag
sends a goto.

Usage: python -I tools/click-corpus.py <repo-dir> <corpus-dir>
"""
import pathlib
import re
import sys
import time

from playwright.sync_api import sync_playwright

sys.stdout.reconfigure(encoding="utf-8")

repo = pathlib.Path(sys.argv[1])
corpus = pathlib.Path(sys.argv[2])
lib = (repo / "src/main/resources/mermaid/mermaid.min.js").read_text(encoding="utf-8")
js = (repo / "src/main/resources/preview/companion-preview.js").read_text(encoding="utf-8")
html = ('<html><head><style>html, body { margin: 0; height: 100%; overflow: hidden; background: #fff; }'
        '#viewport { position: absolute; inset: 0; overflow: hidden; } #stage { position: absolute; left: 0; top: 0; transform-origin: 0 0; }'
        '</style></head><body><div id="error"></div><div id="viewport"><div id="stage"></div></div>'
        '<script id="mermaid-lib">' + lib + '</script><script>' + js + '</script></body></html>')
CLICKABLE = "#stage g.node, #stage g.cluster, #stage .actor[name], #stage g.actor-man[data-id]"


def word_in(text, name):
    return re.search(r"(?<![\w])" + re.escape(name) + r"(?![\w])", text) is not None


problems, total_clicks, total_named = [], 0, 0
targets = []  # (file, kind, value) the page sent: SourceLocatorCorpusTest reads them
with sync_playwright() as pw:
    browser = pw.chromium.launch()
    page = browser.new_page(viewport={"width": 1400, "height": 1000})
    page.set_content(html)
    page.evaluate("window.__m = []; window.gapHunterBridge = (p) => window.__m.push(p);")
    for f in sorted(corpus.glob("valid-*.mmd")):
        text = f.read_text(encoding="utf-8")
        page.evaluate("window.__m.length = 0")
        page.evaluate("(t) => window.gapHunterRender(t, 1)", text)
        for _ in range(200):
            if page.evaluate("window.__m.some((m) => m.startsWith('rendered:1') || m.startsWith('renderfailed:1'))"):
                break
            time.sleep(0.02)
        page.evaluate("window.gapHunterFit()")  # only the first render of a page is fitted on its own
        url_before = page.url
        # A subgraph is clicked on its title, where a person clicks it (its middle is usually an edge).
        boxes = page.evaluate("""(sel) => [...document.querySelectorAll(sel)].map((g) => {
            const e = (g.classList.contains('cluster') && g.querySelector('.cluster-label')) || g; const r = e.getBoundingClientRect();
            return [r.left + r.width / 2, r.top + r.height / 2, r.width, r.height, g.getAttribute('id') || g.getAttribute('class')]; }).filter((b) => b[2] > 2 && b[3] > 2)""", CLICKABLE)
        names, misses = [], []
        silent = []
        for x, y, _, _, what in boxes[:40]:
            if not (0 < x < 1400 and 0 < y < 1000):
                continue
            n = page.evaluate("window.__m.length")
            page.mouse.click(x, y)
            time.sleep(0.05)
            try:
                got = [m for m in page.evaluate("window.__m.slice(%d)" % n) if m.startswith("goto:")]
            except Exception:  # the click loaded another document: the page lost its own
                problems.append("%s: a click navigated the page (to %s)" % (f.name, page.url))
                page.wait_for_load_state()
                page.set_content(html)
                page.evaluate("window.__m = []; window.gapHunterBridge = (p) => window.__m.push(p);")
                break
            total_clicks += 1
            if not got:
                silent.append(what)
            if got:
                total_named += 1
                kind, _, value = got[0][len("goto:"):].partition(":")
                names.append(value)
                if (f.name, kind, value) not in targets:
                    targets.append((f.name, kind, value))
                if kind == "name" and not word_in(text, value):
                    misses.append(value)
            # The same point dragged 20 px is a pan: nothing may be sent.
            n = page.evaluate("window.__m.length")
            page.mouse.move(x, y)
            page.mouse.down()
            page.mouse.move(x + 20, y + 5, steps=4)
            page.mouse.up()
            if any(m.startswith("goto:") for m in page.evaluate("window.__m.slice(%d)" % n)):
                problems.append("%s: a drag sent a goto" % f.name)
            page.evaluate("window.gapHunterFit()")
            if page.url != url_before:
                problems.append("%s: a click navigated the page to %s" % (f.name, page.url))
                page.set_content(html)
                page.evaluate("window.__m = []; window.gapHunterBridge = (p) => window.__m.push(p);")
                break
        if boxes:
            print("%-40s clickable %2d  named %2d  %s%s" % (f.name, len(boxes), len(names), sorted(set(names))[:6],
                                                           "  MISSING " + str(misses) if misses else "") + ("  silent: " + str(silent)[:120] if silent else ""))
        if misses:
            problems.append("%s: names not in the text: %s" % (f.name, misses))
    browser.close()

print("clicks %d, a goto for %d" % (total_clicks, total_named))
with (corpus / "click-targets.tsv").open("w", encoding="utf-8", newline="\n") as fh:
    for row in targets:
        fh.write("\t".join(row) + "\n")
print("wrote", corpus / "click-targets.tsv", len(targets), "targets")
for p in problems:
    print("PROBLEM", p)
sys.exit(1 if problems else 0)
