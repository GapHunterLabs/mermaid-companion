"""Renders every .mmd in a corpus directory through the plugin's real preview page
(bundled mermaid.min.js + companion-preview.js, same HTML shell as PreviewPage.kt)
in headless Chromium, and records per file:

  parse       mermaid.parse() verdict (ok / error message)
  render      the plugin's gapHunterRender() outcome (rendered / renderfailed)
  fo          number of <foreignObject> in the preview SVG
  svgStable   two consecutive renders + sync SVG exports, normalized like
              SvgNormalizer.kt, are byte-identical
  svgRawSame  same comparison without the normalizer
  png         the plugin's gapHunterExportPng('sync') outcome (bytes or error text)
  pngStable   two PNG exports of the same diagram are byte-identical

Also writes <corpus-dir>/mermaid-verdicts.tsv (file, ok|error, detail), the file
MermaidCorpusTest reads.

Needs Python 3 with Playwright and its Chromium (pip install playwright;
python -m playwright install chromium).

Usage: python -I tools/render-corpus.py <repo-dir> <corpus-dir> <report.tsv> [chromium-exe]
"""
import base64
import hashlib
import json
import pathlib
import re
import sys
import time

from playwright.sync_api import sync_playwright

repo = pathlib.Path(sys.argv[1])
corpus = pathlib.Path(sys.argv[2])
out_tsv = pathlib.Path(sys.argv[3])
exe = sys.argv[4] if len(sys.argv) > 4 else None

mermaid_js = (repo / "src/main/resources/mermaid/mermaid.min.js").read_text(encoding="utf-8")
companion_js = (repo / "src/main/resources/preview/companion-preview.js").read_text(encoding="utf-8")

# Same shell as PreviewPage.html().
html = f"""<html><head><style>
html, body {{ margin: 0; height: 100%; overflow: hidden; background: #ffffff; }}
#viewport {{ position: absolute; inset: 0; overflow: hidden; cursor: grab; }}
#stage {{ position: absolute; left: 0; top: 0; transform-origin: 0 0; }}
#error {{ display: none; position: absolute; left: 0; right: 0; top: 0; z-index: 1; }}
</style></head><body>
<div id="error"></div>
<div id="viewport"><div id="stage"></div></div>
<script>{mermaid_js}</script>
<script>{companion_js}</script>
</body></html>"""

RENDER_ID = re.compile(r"gap-hunter-mermaid-(?:svg|png)-\d+")  # SvgNormalizer.kt


def normalize(svg: str) -> str:
    return RENDER_ID.sub("mermaid-diagram", svg)


def wait_message(page, start_index, prefixes, timeout_s=30):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        msgs = page.evaluate(f"window.__msgs.slice({start_index})")
        for i, m in enumerate(msgs):
            if not isinstance(m, str):
                print('  non-string message:', repr(m)[:200]); continue
            for p in prefixes:
                if m.startswith(p):
                    return start_index + i + 1, m
        page.wait_for_timeout(25)
    return start_index, None


rows = []
with sync_playwright() as pw:
    browser = pw.chromium.launch(executable_path=exe) if exe else pw.chromium.launch()
    page = browser.new_page(viewport={"width": 1280, "height": 800})
    page.set_content(html)
    page.evaluate("window.__msgs = []; window.gapHunterBridge = function (p) { window.__msgs.push(p); };")
    version = None  # the bundle does not expose its version at runtime; see README
    print("chromium", browser.version, "mermaid", version)
    token = 0
    idx = 0
    for f in sorted(corpus.glob("*.mmd")):
        text = f.read_bytes().decode("utf-8")
        row = {"file": f.name}
        try:
            r = page.evaluate("""async (t) => { try { const r = await mermaid.parse(t); return 'ok:' + (r && r.diagramType); }
                                 catch (e) { return 'error:' + ((e && e.message) || String(e)).replace(/\\s+/g, ' ').slice(0, 160); } }""", text)
        except Exception as e:  # noqa
            r = "error:" + str(e)[:160]
        row["parse"] = r

        def render_and_export_svg():
            global token, idx
            token += 1
            t0 = time.time()
            page.evaluate("([t, k]) => window.gapHunterRender(t, k)", [text, token])
            idx, m = wait_message(page, idx, [f"rendered:{token}", f"renderfailed:{token}", f"rendersuperseded:{token}"])
            ms = int((time.time() - t0) * 1000)
            if m is None or not m.startswith("rendered:"):
                return (m or "timeout")[:160], None, ms, 0
            fo = page.evaluate("document.querySelectorAll('#stage svg foreignObject').length")
            page.evaluate("window.gapHunterExportSvg('sync')")
            idx, s = wait_message(page, idx, ["syncsvg:", "syncerror:"])
            return "rendered", (s[len("syncsvg:"):] if s and s.startswith("syncsvg:") else None), ms, fo

        status, svg1, ms, fo = render_and_export_svg()
        row["render"] = status
        row["renderMs"] = ms
        row["fo"] = fo
        if status == "rendered" and svg1 is not None:
            _, svg2, _, _ = render_and_export_svg()
            row["svgBytes"] = len(svg1.encode("utf-8"))
            row["svgRawSame"] = svg1 == svg2
            row["svgStable"] = svg2 is not None and normalize(svg1) == normalize(svg2)
            if not row["svgStable"] and svg2 is not None:
                a, b = normalize(svg1), normalize(svg2)
                i = next((k for k in range(min(len(a), len(b))) if a[k] != b[k]), min(len(a), len(b)))
                row["svgDiff"] = (a[max(0, i - 40):i + 40] + " || " + b[max(0, i - 40):i + 40]).replace("\t", " ").replace("\n", " ")
            pngs = []
            for _ in range(2):
                page.evaluate("window.gapHunterExportPng('sync')")
                idx, p = wait_message(page, idx, ["syncpng:", "syncerror:"])
                pngs.append(p)
            p1, p2 = pngs
            if p1 and p1.startswith("syncpng:"):
                raw = base64.b64decode(p1[len("syncpng:"):] or "")
                row["png"] = f"ok {len(raw)} bytes" if raw[:4] == b"\x89PNG" else f"empty/malformed ({len(raw)} bytes)"
                row["pngStable"] = p1 == p2
            else:
                row["png"] = (p1 or "timeout")[:200]
        rows.append(row)
        print(json.dumps(row, ensure_ascii=False)[:400])
    browser.close()

cols = ["file", "parse", "render", "renderMs", "fo", "svgBytes", "svgStable", "svgRawSame", "png", "pngStable", "svgDiff"]
with out_tsv.open("w", encoding="utf-8", newline="\n") as fh:
    fh.write("\t".join(cols) + "\n")
    for r in rows:
        fh.write("\t".join(str(r.get(c, "")).replace("\t", " ") for c in cols) + "\n")
verdicts = corpus / "mermaid-verdicts.tsv"
with verdicts.open("w", encoding="utf-8", newline="\n") as fh:
    fh.write("# Verdicts of the bundled mermaid.js " + str(version or "11.16.1") + " (mermaid.parse) for each corpus file.\n")
    fh.write("# Regenerate with tools/render-corpus.py; columns: file, verdict (ok|error), detail.\n")
    for r in rows:
        verdict, _, detail = r["parse"].partition(":")
        fh.write(f"{r['file']}\t{verdict}\t{detail}\n")
print("wrote", out_tsv, "and", verdicts)
