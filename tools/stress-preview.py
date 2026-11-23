"""Stress POC for the preview page: large diagrams through the plugin's real render and
PNG export paths (same shell as PreviewPage.kt), headless Chromium.

Usage: python -I tools/stress-preview.py <repo-dir>
"""
import pathlib
import sys
import time

from playwright.sync_api import sync_playwright

repo = pathlib.Path(sys.argv[1])
mermaid_js = (repo / "src/main/resources/mermaid/mermaid.min.js").read_text(encoding="utf-8")
companion_js = (repo / "src/main/resources/preview/companion-preview.js").read_text(encoding="utf-8")
html = f"""<html><head><style>html, body {{ margin: 0; height: 100%; overflow: hidden; }}
#viewport {{ position: absolute; inset: 0; overflow: hidden; }} #stage {{ position: absolute; left: 0; top: 0; transform-origin: 0 0; }}
#error {{ display: none; }}</style></head><body><div id="error"></div><div id="viewport"><div id="stage"></div></div>
<script>{mermaid_js}</script><script>{companion_js}</script></body></html>"""


def chain(n, direction="TD"):
    return f"flowchart {direction}\n" + "".join(f"    n{i}[Node {i}] --> n{i + 1}[Node {i + 1}]\n" for i in range(n))


def fan(n):
    return "flowchart LR\n" + "".join(f"    root --> leaf{i}\n" for i in range(n))


cases = [
    ("chain 100 edges TD", chain(100)),
    ("chain 400 edges TD", chain(400)),
    ("chain 499 edges LR", chain(499, "LR")),
    ("chain 501 edges", chain(501)),
    ("fan 300 edges LR", fan(300)),
    ("49,990 chars", "flowchart TD\n    A[\"" + "x" * (49_990 - 22) + "\"] --> B\n"),
    ("50,001 chars", "flowchart TD\n    A[\"" + "x" * (50_001 - 22) + "\"] --> B\n"),
    ("sequence 400 messages", "sequenceDiagram\n" + "".join(f"    A->>B: message {i}\n" for i in range(400))),
]

with sync_playwright() as pw:
    b = pw.chromium.launch()
    page = b.new_page(viewport={"width": 1280, "height": 800})
    page.set_content(html)
    page.evaluate("window.__msgs = []; window.gapHunterBridge = function (p) { window.__msgs.push(p); };")
    token = 0
    for name, text in cases:
        token += 1
        page.evaluate("window.__msgs = []")
        t0 = time.time()
        page.evaluate("([t, k]) => window.gapHunterRender(t, k)", [text, token])
        msg = None
        while time.time() - t0 < 120:
            msgs = [m for m in page.evaluate("window.__msgs") if isinstance(m, str) and not m.startswith("zoom:")]
            if msgs:
                msg = msgs[0]
                break
            page.wait_for_timeout(50)
        ms = int((time.time() - t0) * 1000)
        size = page.evaluate("(() => { const s = document.querySelector('#stage svg'); return s ? s.getAttribute('width') + 'x' + s.getAttribute('height') : '-'; })()")
        line = f"{name:24s} len={len(text):>7} render={ms:>6} ms -> {(msg or 'timeout')[:90]!r} svg={size}"
        if msg and msg.startswith("rendered"):
            page.evaluate("window.__msgs = []")
            t1 = time.time()
            page.evaluate("window.gapHunterExportPng('sync')")
            png = None
            while time.time() - t1 < 120:
                m = [x for x in page.evaluate("window.__msgs") if isinstance(x, str) and (x.startswith("syncpng:") or x.startswith("syncerror:"))]
                if m:
                    png = m[0]
                    break
                page.wait_for_timeout(50)
            pms = int((time.time() - t1) * 1000)
            if png and png.startswith("syncpng:"):
                body = png[len("syncpng:"):]
                line += f" | png {pms} ms, base64 {len(body)} chars" + (" (EMPTY: canvas too large)" if not body else "")
            else:
                line += f" | png {pms} ms -> {(png or 'timeout')[:90]!r}"
        print(line, flush=True)
    b.close()
