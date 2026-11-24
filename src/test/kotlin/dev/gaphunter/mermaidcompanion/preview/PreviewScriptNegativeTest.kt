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
                const sent = [], configs = [], images = [], canvases = [];
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
                                const canvas = { width: 0, height: 0, fills: 0,
                                    getContext: () => ({ fillRect() { canvas.fills++; }, drawImage() {} }),
                                    toDataURL: () => canvasAnswer };
                                canvases.push(canvas);
                                return canvas;
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
                return { sandbox, sent, configs, images, elements, frames, canvases };
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
    fun `PNG options keep only scales 1 to 3 and an explicit true for transparency`() = runNode(
        """
        for (const s of [1, 2, 3]) same(p.pngOptions({ scale: s }), { scale: s, transparent: false });
        for (const bad of [0, 4, -1, 2.5, '2', NaN, Infinity, null, undefined, {}, [2]]) assert.strictEqual(p.pngOptions({ scale: bad }).scale, 2, String(bad));
        for (const bad of [undefined, null, 'x', 5, true]) same(p.pngOptions(bad), { scale: 2, transparent: false });
        assert.strictEqual(p.pngOptions({ transparent: true }).transparent, true);
        for (const bad of ['true', 1, 'yes', {}, false]) assert.strictEqual(p.pngOptions({ transparent: bad }).transparent, false, String(bad));
        same(['sync', 'copy', undefined, 'save', 'syncx', ''].map(p.exportPrefix), ['sync', 'copy', '', '', '', '']);
        """,
    )

    @Test
    fun `the PNG is drawn at the scale asked for, and the canvas limit is checked at that scale`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg data-size="30000x10"/>' }), 'data:image/png;base64,AAAA');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        pg.sandbox.gapHunterExportPng('copy', { scale: 2 });
        await flush();
        assert.ok(pg.sent.includes('copypng:AAAA'), pg.sent.join());
        assert.strictEqual(pg.canvases[0].width, 60000);
        pg.sent.length = 0;
        pg.sandbox.gapHunterExportPng('copy', { scale: 3 });
        await flush();
        assert.strictEqual(pg.canvases.length, 1, 'nothing is drawn past the limit');
        assert.ok(pg.sent.length === 1 && pg.sent[0].startsWith('copyerror:') && pg.sent[0].includes('30000 x 10 px at 3x'), pg.sent.join());
        pg.sent.length = 0;
        pg.sandbox.gapHunterExportPng('copy', { scale: 1 });
        await flush();
        assert.strictEqual(pg.canvases[1].width, 30000);
        assert.strictEqual(pg.canvases[1].height, 10);
        """,
    )

    @Test
    fun `a transparent PNG skips the background, any other answer paints it`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg data-size="300x200"/>' }), 'data:image/png;base64,AAAA');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        pg.sandbox.gapHunterExportPng('sync', { transparent: true });
        await flush();
        pg.sandbox.gapHunterExportPng('sync', { transparent: 'true' });
        await flush();
        pg.sandbox.gapHunterExportPng('sync');
        await flush();
        same(pg.canvases.map((c) => c.fills), [0, 1, 1]);
        assert.strictEqual(pg.sent.filter((m) => m === 'syncpng:AAAA').length, 3, pg.sent.join());
        """,
    )

    @Test
    fun `copy answers use their own message kinds`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg data-size="300x200"/>' }), 'data:image/png;base64,AAAA');
        pg.sandbox.gapHunterExportSvg('copy');
        pg.sandbox.gapHunterExportPng('copy');
        assert.ok(pg.sent[0].startsWith('copyerror:Nothing to export yet') && pg.sent[1].startsWith('copyerror:Nothing to export yet'), pg.sent.join());
        pg.sent.length = 0;
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        pg.sandbox.gapHunterExportSvg('copy');
        await flush();
        assert.ok(pg.sent.some((m) => m.startsWith('copysvg:<svg')), pg.sent.join());
        """,
    )

    @Test
    fun `a click names the node, class, state, entity, subgraph or participant as written`() = runNode(
        """
        same(p.clickTarget('flowchart-start-0', 'node default'), 'name:start');
        same(p.clickTarget('flowchart-a-b-c-12', 'node'), 'name:a-b-c');
        same(p.clickTarget('classId-Animal-0', 'node default'), 'name:Animal');
        same(p.clickTarget('state-Idle-1', 'node statediagram-state'), 'name:Idle');
        same(p.clickTarget('entity-CUSTOMER-0', 'node default'), 'name:CUSTOMER');
        same(p.clickTarget('grp', 'cluster'), 'name:grp');
        same(p.clickTarget('req1', 'node default'), 'name:req1');
        same(p.clickTarget('', 'actor actor-top', 'Alice'), 'name:Alice');
        same(p.clickTarget('', 'actor-man actor-top', 'Bob'), 'name:Bob');
        same(p.clickTarget('node_2', 'node mindmap-node section-1'), 'index:2');
        same(p.clickTarget('state-Still----note-4', 'node'), 'name:Still');
        same(p.clickTarget('state-Moving_start-3', 'node', null, false), null);
        same(p.clickTarget('state-Moving_end-3', 'node', null, false), null);
        same(p.clickTarget('state-x_start-3', 'node', null, true), 'name:x_start');
        same(p.clickTarget('node-1', 'node'), null);
        same(p.clickTarget('node-1', 'cluster'), null);
        """,
    )

    @Test
    fun `a click on anything else, or with a name that could not be a real id, points at nothing`() = runNode(
        """
        for (const [id, cls, name] of [['state-root_start-0', 'node'], ['state-root_end-3', 'node'], ['L_a_b_0', 'edge'],
                ['edge0', 'label'], ['', ''], [null, null], ['node_2', 'node'], ['node_1234567', 'mindmap-node'],
                ['actor0', 'actor-line', 'Alice'], ['x', 'nodes'], ['x', 'clusterx']]) {
            same(p.clickTarget(id, cls, name), cls === 'node' && id === 'node_2' ? 'name:node_2' : null);
        }
        same(p.clickTarget('', 'actor', 'x'.repeat(256)), 'name:' + 'x'.repeat(256));
        same(p.clickTarget('', 'actor', 'x'.repeat(257)), null);
        same(p.clickTarget('', 'actor', 'a' + String.fromCharCode(10) + 'b'), null);
        same(p.clickTarget('', 'actor', 'a' + String.fromCharCode(0)), null);
        same(p.clickTarget('flowchart-' + 'y'.repeat(300) + '-0', 'node'), null);
        """,
    )

    @Test
    fun `a press that travels more than 4 px is a drag, not a click`() = runNode(
        """
        assert.ok(p.isClick({ x: 10, y: 10 }, { x: 10, y: 10 }));
        assert.ok(p.isClick({ x: 10, y: 10 }, { x: 14, y: 6 }));
        assert.ok(!p.isClick({ x: 10, y: 10 }, { x: 15, y: 10 }));
        assert.ok(!p.isClick({ x: 10, y: 10 }, { x: 10, y: 4.9 }));
        for (const bad of [null, undefined, { x: NaN, y: 0 }, { x: 0, y: Infinity }]) {
            assert.ok(!p.isClick(bad, { x: 0, y: 0 }));
            assert.ok(!p.isClick({ x: 0, y: 0 }, bad));
        }
        """,
    )

    @Test
    fun `the page knows exactly the themes the toolbar offers`() = runNode(
        """
        same(p.themeIds().slice().sort(), [${PreviewTheme.entries.joinToString { "'" + it.id + "'" }}].sort());
        """,
    )

    @Test
    fun `an unknown theme id is the default theme, and a preset's settings cannot be changed from outside`() = runNode(
        """
        for (const bad of ['', 'Dark', 'base', '__proto__', 'constructor', 'toString', undefined, null, 5]) {
            same(p.themeConfig(bad), { theme: 'default' });
            assert.strictEqual(p.themeBackground(bad), '#ffffff', String(bad));
        }
        same(p.themeConfig('forest'), { theme: 'forest' });
        assert.strictEqual(p.themeBackground('dark'), '#1e1e1e');
        const c = p.themeConfig('blueprint');
        assert.strictEqual(c.theme, 'base');
        c.themeVariables.primaryColor = '#ff0000';
        assert.strictEqual(p.themeConfig('blueprint').themeVariables.primaryColor, '#13315c');
        assert.strictEqual(p.themeBackground('blueprint'), '#0b2545');
        // The page ignores a theme it does not know, instead of handing it to mermaid.
        const pg = page(async () => ({ svg: '<svg/>' }));
        pg.sandbox.gapHunterSetTheme('no-such-theme');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        assert.strictEqual(pg.configs[pg.configs.length - 1].theme, 'default');
        pg.sandbox.gapHunterSetTheme('monochrome');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        const last = pg.configs[pg.configs.length - 1];
        assert.strictEqual(last.theme, 'base');
        assert.strictEqual(last.themeVariables.primaryColor, '#e8e8e8');
        assert.strictEqual(last.handDrawnSeed, 1);
        """,
    )

    @Test
    fun `every preset palette reaches WCAG AA contrast for text, lines and borders`() = runNode(
        """
        const lum = (h) => { const c = [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255)
            .map((v) => v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
            return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; };
        const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
        for (const [id, preset] of Object.entries(p.THEME_PRESETS)) {
            const v = preset.vars, bg = preset.background;
            const text = [['primaryTextColor', 'primaryColor'], ['primaryTextColor', 'secondaryColor'], ['textColor', 'clusterBkg'],
                ['actorTextColor', 'actorBkg'], ['noteTextColor', 'noteBkgColor'], ['titleColor', 'clusterBkg']];
            for (const [fg, back] of text) assert.ok(ratio(v[fg], v[back]) >= 4.5, id + ' ' + fg + ' on ' + back + ': ' + ratio(v[fg], v[back]));
            for (const fg of ['textColor', 'signalTextColor']) assert.ok(ratio(v[fg], bg) >= 4.5, id + ' ' + fg + ' on background');
            for (const fg of ['lineColor', 'primaryBorderColor', 'clusterBorder', 'actorBorder', 'signalColor']) {
                assert.ok(ratio(v[fg], bg) >= 3, id + ' ' + fg + ' on background: ' + ratio(v[fg], bg));
            }
            for (const value of Object.values(v)) if (typeof value === 'string') assert.ok(/^#[0-9a-f]{6}$/.test(value), id + ': ' + value);
        }
        """,
    )

    @Test
    fun `the page knows exactly the looks the toolbar offers, and anything else is classic`() = runNode(
        """
        same(p.LOOKS.slice().sort(), [${DiagramLook.entries.joinToString { "'" + it.id + "'" }}].sort());
        for (const ok of p.LOOKS) same(p.lookConfig(ok), { look: ok });
        for (const bad of ['', 'handdrawn', 'HandDrawn', 'default', 'bogus', '__proto__', 'toString', undefined, null, 1, {}]) {
            same(p.lookConfig(bad), { look: 'classic' });
        }
        """,
    )

    @Test
    fun `the chosen look reaches every render and every export, with the fixed seed`() = runNode(
        """
        const pg = page(async () => ({ svg: '<svg data-size="300x200"/>' }), 'data:image/png;base64,AAAA');
        pg.sandbox.gapHunterRender('flowchart LR\n A --> B');
        await flush();
        assert.strictEqual(pg.configs[pg.configs.length - 1].look, 'classic');
        pg.sandbox.gapHunterSetLook('handDrawn');
        await flush();
        assert.strictEqual(pg.configs[pg.configs.length - 1].look, 'handDrawn');
        pg.sandbox.gapHunterExportSvg('sync');
        pg.sandbox.gapHunterExportPng('sync');
        await flush();
        const tail = pg.configs.slice(-2);
        assert.ok(tail.every((c) => c.look === 'handDrawn' && c.handDrawnSeed === 1), JSON.stringify(tail));
        pg.sandbox.gapHunterSetLook('sketchy');
        await flush();
        assert.strictEqual(pg.configs[pg.configs.length - 1].look, 'classic');
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
