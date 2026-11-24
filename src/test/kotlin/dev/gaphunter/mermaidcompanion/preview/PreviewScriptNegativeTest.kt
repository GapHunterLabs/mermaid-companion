package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `preview/companion-preview.js` on input the toolbar and the mouse never produce on
 * purpose (NaN, infinities, zero, negative and huge values), and its browser wiring run in
 * Node against a fake DOM and a fake mermaid.js: the mermaid config it uses, the
 * "maximum text size" stand-in diagram, and PNG sizes the canvas cannot hold. Skipped, not
 * failed, on a machine without Node.
 */
class PreviewScriptNegativeTest {

    private val script = File("src/main/resources/preview/companion-preview.js").absoluteFile

    private fun nodeAvailable(): Boolean = try {
        ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor(20, TimeUnit.SECONDS)
    } catch (e: Exception) {
        false
    }

    private fun runNode(body: String) {
        assumeTrue("node is not installed", nodeAvailable())
        val harness = File.createTempFile("companion-preview-negative", ".js").apply { deleteOnExit() }
        harness.writeText(
            """
            const assert = require('assert');
            const fs = require('fs');
            const vm = require('vm');
            const SCRIPT = ${jsString(script.path)};
            const p = require(SCRIPT);
            const same = (a, b) => assert.deepStrictEqual(a, b);
            const isFiniteView = (v) => [v.scale, v.x, v.y].every(Number.isFinite) && v.scale > 0;

            // Browser wiring in a sandbox: a fake DOM and a fake mermaid.js.
            function page(renderImpl, canvasAnswer) {
                const el = () => ({ style: {}, textContent: '', innerHTML: '', clientWidth: 800, clientHeight: 600,
                    addEventListener() {}, querySelector() { return null; }, getBoundingClientRect() { return { left: 0, top: 0 }; } });
                const elements = { viewport: el(), stage: el(), error: el(), 'mermaid-lib': { textContent: '/* bundled mermaid.js */' } };
                const frames = [];
                const sent = [], configs = [], images = [];
                const svgStub = (w, h) => {
                    const attrs = {};
                    const node = { style: {}, viewBox: { baseVal: { width: w, height: h } },
                        setAttribute(k, v) { attrs[k] = String(v); }, getAttribute(k) { return attrs[k] || null; },
                        cloneNode() { return node; } };
                    return node;
                };
                const mermaidStub = { initialize: (c) => configs.push(c), render: renderImpl,
                    mermaidAPI: { getConfig: () => ({ maxTextSize: 50000 }) } };
                const body = { style: {}, appendChild(f) { f.parentNode = body; }, removeChild(f) { f.parentNode = null; } };
                const sandbox = {
                    document: {
                        body,
                        getElementById: (id) => elements[id] || null,
                        createElement: (tag) => {
                            if (tag === 'iframe') {
                                // An export's own page: the script copied into it starts the same mermaid stub.
                                const win = {};
                                const frame = { style: {}, parentNode: null, setAttribute() {}, contentWindow: win,
                                    contentDocument: { createElement: () => ({}), head: { appendChild(s) { if (s.text) win.mermaid = mermaidStub; } } } };
                                frames.push(frame);
                                return frame;
                            }
                            if (tag === 'canvas') {
                                return { width: 0, height: 0, getContext: () => ({ fillRect() {}, drawImage() {} }),
                                    toDataURL: () => canvasAnswer };
                            }
                            const holder = { _svg: null, querySelector() { return this._svg; } };
                            Object.defineProperty(holder, 'innerHTML', { set(v) {
                                const m = /data-size="(\d+)x(\d+)"/.exec(v);
                                holder._svg = svgStub(m ? +m[1] : 100, m ? +m[2] : 50);
                            } });
                            return holder;
                        }
                    },
                    window: { addEventListener() {} },
                    mermaid: mermaidStub,
                    gapHunterBridge: (msg) => sent.push(msg),
                    XMLSerializer: function () { this.serializeToString = () => '<svg/>'; },
                    Image: function () { images.push(this); const img = this;
                        Object.defineProperty(this, 'src', { set() { img.onload(); } }); },
                    btoa: (s) => Buffer.from(s, 'binary').toString('base64'),
                    unescape, encodeURIComponent, String, Math, parseFloat, isFinite, isNaN
                };
                vm.createContext(sandbox);
                vm.runInContext(fs.readFileSync(SCRIPT, 'utf8'), sandbox);
                return { sandbox, sent, configs, images, elements, frames };
            }
            const flush = () => new Promise((r) => setImmediate(r));

            (async () => {
            ${body.trimIndent()}
            console.log('ALL OK');
            })().catch((e) => { console.log(e && e.stack || e); process.exit(1); });
            """.trimIndent(),
        )
        val process = ProcessBuilder("node", harness.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(60, TimeUnit.SECONDS)
        assertEquals(output, "ALL OK", output.trim().lines().last())
    }

    @Test
    fun `zoom ignores factors and points that are not finite positive numbers`() = runNode(
        """
        const v = { scale: 1.5, x: 10, y: -20 };
        for (const f of [0, -1, -0.5, NaN, Infinity, -Infinity, undefined, null, '2']) same(p.zoomAt(v, f, 100, 100), v);
        for (const c of [NaN, Infinity, -Infinity]) { same(p.zoomAt(v, 2, c, 0), v); same(p.zoomAt(v, 2, 0, c), v); }
        // Huge and tiny factors clamp; a point so far out that the result overflows leaves the view alone.
        assert.strictEqual(p.zoomAt(v, 1e308, 0, 0).scale, p.MAX_SCALE);
        assert.strictEqual(p.zoomAt(v, 1e-308, 0, 0).scale, p.MIN_SCALE);
        same(p.zoomAt({ scale: 1, x: 0, y: 0 }, 8, 1.7e308, 0), { scale: 1, x: 0, y: 0 });
        assert.ok(isFiniteView(p.zoomAt(v, 2, 1e300, -1e300)));
        """,
    )

    @Test
    fun `pan ignores deltas that are not finite and never overflows`() = runNode(
        """
        const v = { scale: 2, x: 5, y: 5 };
        for (const d of [NaN, Infinity, -Infinity, undefined]) { same(p.panBy(v, d, 0), v); same(p.panBy(v, 0, d), v); }
        const far = p.panBy(v, 1e308, 0);
        assert.ok(isFiniteView(far));
        same(p.panBy(far, 1e308, 0), far);
        same(p.panBy(v, 0, 0), v);
        same(p.panBy(v, -7, 3), { scale: 2, x: -2, y: 8 });
        """,
    )

    @Test
    fun `fit and 100 percent always return a finite view`() = runNode(
        """
        const identity = { scale: 1, x: 0, y: 0 };
        for (const bad of [0, -1, NaN, Infinity, -Infinity, undefined]) {
            same(p.fit(bad, 100, 800, 600), identity); same(p.fit(100, bad, 800, 600), identity);
            same(p.fit(100, 100, bad, 600), identity); same(p.fit(100, 100, 800, bad), identity);
        }
        // A viewport smaller than the padding: smallest scale, still finite.
        const tiny = p.fit(100, 100, 10, 10);
        assert.strictEqual(tiny.scale, p.MIN_SCALE); assert.ok(isFiniteView(tiny));
        // A diagram far larger than MIN_SCALE can fit: clamped, finite (it overflows the viewport).
        const huge = p.fit(1e6, 1e6, 800, 600);
        assert.strictEqual(huge.scale, p.MIN_SCALE); assert.ok(isFiniteView(huge));
        for (const bad of [NaN, Infinity, -Infinity, undefined]) {
            same(p.actualSize(bad, 100, 800, 600), { scale: 1, x: 16, y: 16 });
            same(p.actualSize(100, 100, bad, 600), { scale: 1, x: 16, y: 16 });
        }
        assert.ok(isFiniteView(p.actualSize(0, 0, 800, 600)));
        assert.ok(isFiniteView(p.actualSize(-5, -5, 0, 0)));
        """,
    )

    @Test
    fun `wheel factor is 1 for NaN and clamped for infinities`() = runNode(
        """
        assert.strictEqual(p.wheelFactor(NaN), 1);
        assert.strictEqual(p.wheelFactor(undefined), 1);
        assert.strictEqual(p.wheelFactor(0), 1);
        assert.strictEqual(p.wheelFactor(Infinity), p.wheelFactor(200));
        assert.strictEqual(p.wheelFactor(-Infinity), p.wheelFactor(-200));
        assert.strictEqual(p.wheelFactor(-1e308), p.wheelFactor(-200));
        """,
    )

    @Test
    fun `the canvas size check sits exactly on Chromium's limits`() = runNode(
        """
        assert.ok(p.canvasFits(65535, 1)); assert.ok(!p.canvasFits(65536, 1)); assert.ok(!p.canvasFits(1, 65536));
        assert.ok(p.canvasFits(16384, 16384)); assert.ok(!p.canvasFits(16385, 16384));
        for (const bad of [0, -1, NaN, Infinity]) { assert.ok(!p.canvasFits(bad, 10)); assert.ok(!p.canvasFits(10, bad)); }
        """,
    )

    @Test
    fun `the maximum text size stand-in is recognized, and only when the user did not write it`() = runNode(
        """
        const standIn = '<svg><text>Maximum text size in diagram exceeded</text></svg>';
        assert.ok(p.isTooLargeStandIn('flowchart TD\n A --> B', standIn));
        assert.ok(!p.isTooLargeStandIn('flowchart TD\n A[Maximum text size in diagram exceeded]', standIn));
        assert.ok(!p.isTooLargeStandIn('flowchart TD', '<svg/>'));
        assert.ok(!p.isTooLargeStandIn('', ''));
        """,
    )

    @Test
    fun `every render fixes the hand-drawn seed so the same diagram gives the same SVG`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg/>' }));
        pg.sandbox.gapHunterRender('flowchart TD\n A --> B', 1);
        await flush();
        assert.ok(pg.configs.length > 0);
        for (const c of pg.configs) assert.strictEqual(c.handDrawnSeed, 1, JSON.stringify(c));
        assert.ok(pg.sent.includes('rendered:1'), pg.sent.join());
        // The PNG render uses its own config: the seed is fixed there too.
        pg.sandbox.gapHunterExportPng('sync');
        await flush();
        for (const c of pg.configs) assert.strictEqual(c.handDrawnSeed, 1, JSON.stringify(c));
        """,
    )

    @Test
    fun `a too-long diagram is a render failure, never a rendered stand-in`() = runNode(
        """
        const standIn = '<svg><text>Maximum text size in diagram exceeded</text></svg>';
        let answer = '<svg id="good"/>';
        const pg = page(async () => ({ svg: answer }));
        pg.sandbox.gapHunterRender('flowchart TD\n A --> B', 1);
        await flush();
        assert.strictEqual(pg.elements.stage.innerHTML, '<svg id="good"/>');
        answer = standIn;
        pg.sandbox.gapHunterRender('flowchart TD\n' + ' A --> B\n'.repeat(6000), 2);
        await flush();
        assert.ok(!pg.sent.includes('rendered:2'), pg.sent.join());
        assert.ok(pg.sent.some((m) => m.startsWith('renderfailed:2:') && m.includes('50000')), pg.sent.join());
        assert.strictEqual(pg.elements.stage.innerHTML, '<svg id="good"/>', 'the last good diagram stays');
        assert.ok(pg.elements.error.textContent.includes('50000'));
        // The PNG path refuses the stand-in too.
        pg.sent.length = 0;
        pg.sandbox.gapHunterExportPng('sync');
        await flush();
        assert.ok(pg.sent.length === 1 && pg.sent[0].startsWith('syncerror:'), pg.sent.join());
        """,
    )

    @Test
    fun `a PNG larger than the canvas allows fails fast with a clear message`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg data-size="40000x30"/>' }), 'data:image/png;base64,AAAA');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        pg.sandbox.gapHunterExportPng('sync');
        await flush();
        assert.strictEqual(pg.images.length, 0, 'nothing is drawn');
        assert.ok(pg.sent.some((m) => m.startsWith('syncerror:') && m.includes('40000 x 30')), pg.sent.join());
        """,
    )

    @Test
    fun `an empty canvas answer is an error, not an empty PNG`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg data-size="300x200"/>' }), 'data:,');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        pg.sandbox.gapHunterExportPng('sync');
        await flush();
        assert.ok(!pg.sent.some((m) => m.startsWith('syncpng:')), pg.sent.join());
        assert.ok(pg.sent.some((m) => m.startsWith('syncerror:') && m.includes('300 x 200')), pg.sent.join());
        """,
    )

    @Test
    fun `a PNG that fits is still exported`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg data-size="300x200"/>' }), 'data:image/png;base64,AAAA');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        pg.sandbox.gapHunterExportPng('sync');
        await flush();
        assert.ok(pg.sent.includes('syncpng:AAAA'), pg.sent.join());
        assert.strictEqual(pg.images.length, 1);
        """,
    )

    @Test
    fun `a NaN zoom from the toolbar leaves the stage transform finite`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg/>' }));
        pg.sandbox.gapHunterZoom(NaN);
        pg.sandbox.gapHunterZoom(Infinity);
        assert.ok(!/NaN|Infinity/.test(pg.elements.stage.style.transform || ''), pg.elements.stage.style.transform);
        assert.ok(!pg.sent.some((m) => /zoom:(NaN|Infinity)/.test(m)), pg.sent.join());
        """,
    )

    private fun jsString(path: String) = "'" + path.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
