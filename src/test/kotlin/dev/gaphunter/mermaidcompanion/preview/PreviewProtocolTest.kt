package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the real `preview/companion-preview.js` in Node against a minimal fake
 * DOM and a mermaid stub whose renders the test resolves by hand, and checks
 * the messages the page sends to the plugin: which render finished, failed or
 * was replaced, and whether an export is a manual one (save dialog) or one the
 * plugin asked for itself (Export on Save). This is the page half of the
 * protocol BridgeMessage parses; the real browser can't run headless, but the
 * order and the wording of these messages can be checked exactly.
 * Skipped, not failed, on a machine without Node.
 */
class PreviewProtocolTest {

    private val script = File("src/main/resources/preview/companion-preview.js").absoluteFile

    private fun nodeAvailable(): Boolean = try {
        ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor(20, TimeUnit.SECONDS)
    } catch (e: Exception) {
        false
    }

    @Test
    fun pageProtocolBehavesInNode() {
        assumeTrue("node is not installed", nodeAvailable())
        val harness = File.createTempFile("companion-preview-protocol", ".js").apply { deleteOnExit() }
        harness.writeText(HARNESS.replace("__SCRIPT_PATH__", jsString(script.path)))
        val process = ProcessBuilder("node", harness.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(60, TimeUnit.SECONDS)
        assertEquals(output, "ALL OK", output.trim())
    }

    private fun jsString(path: String) = "'" + path.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private companion object {
        // No dollar signs in here: this is a Kotlin raw string.
        val HARNESS = """
            const assert = require('assert');
            const fs = require('fs');
            const vm = require('vm');
            const code = fs.readFileSync(__SCRIPT_PATH__, 'utf8');

            function makeSvg(markup) {
                const attrs = {};
                const svg = {
                    _markup: markup,
                    viewBox: { baseVal: { width: 100, height: 50 } },
                    style: {},
                    getAttribute(n) { return attrs[n] === undefined ? null : attrs[n]; },
                    setAttribute(n, v) { attrs[n] = String(v); },
                    cloneNode() { return svg; }
                };
                return svg;
            }

            function makeEl(id) {
                const el = {
                    id, style: {}, textContent: '', clientWidth: 800, clientHeight: 600, _svg: null, _html: '',
                    addEventListener() {},
                    getBoundingClientRect() { return { left: 0, top: 0 }; },
                    querySelector(sel) { return sel === 'svg' ? this._svg : null; }
                };
                Object.defineProperty(el, 'innerHTML', {
                    get() { return this._html; },
                    set(v) { this._html = v; this._svg = v && v.indexOf('<svg') >= 0 ? makeSvg(v) : null; }
                });
                return el;
            }

            // A fresh page: its own bridge log and its own queue of pending mermaid renders.
            function loadPage() {
                const sent = [];
                const pending = [];
                const configs = [];
                const strays = {};
                const els = { viewport: makeEl('viewport'), stage: makeEl('stage'), error: makeEl('error') };
                const sandbox = {
                    document: {
                        body: { style: {} },
                        getElementById(id) { return els[id] || strays[id] || null; },
                        createElement() { return makeEl('x'); }
                    },
                    mermaid: {
                        initialize(config) { configs.push(config); },
                        render(id, text) { return new Promise((resolve, reject) => pending.push({ id, text, resolve, reject })); }
                    },
                    XMLSerializer: class { serializeToString(n) { return '<svg xmlns="http://www.w3.org/2000/svg">' + n._markup + '</svg>'; } },
                    Image: class {},
                    setImmediate, console, Promise, Math, unescape, encodeURIComponent, btoa: (s) => Buffer.from(s, 'binary').toString('base64')
                };
                sandbox.window = { addEventListener() {} };
                sandbox.gapHunterBridge = (p) => sent.push(p);
                vm.createContext(sandbox);
                vm.runInContext(code, sandbox);
                return {
                    root: sandbox, els, pending, configs, strays,
                    // Everything the page told the plugin except the constant zoom chatter.
                    messages() { return sent.filter((m) => m.indexOf('zoom:') !== 0); },
                    clear() { sent.length = 0; }
                };
            }

            const tick = () => new Promise((r) => setImmediate(r));
            const okSvg = (label) => ({ svg: '<svg id="gap-hunter-mermaid-svg-x">' + label + '</svg>' });

            (async () => {
                // 1. A render the plugin asked for reports back with its token.
                let page = loadPage();
                page.root.gapHunterRender('graph TD\nA-->B', 5);
                page.pending[0].resolve(okSvg('one'));
                await tick();
                assert.deepStrictEqual(page.messages(), ['rendered:5']);

                // 2. An ordinary (debounced) render says nothing: only asked-for renders are reported.
                page.clear();
                page.root.gapHunterRender('graph TD\nA-->C');
                page.pending[1].resolve(okSvg('two'));
                await tick();
                assert.deepStrictEqual(page.messages(), []);

                // 3. A diagram that does not parse: keeps the reason (one line), shows the error bar.
                page = loadPage();
                page.root.gapHunterRender('graph TD\nA--', 6);
                page.pending[0].reject(new Error('Parse error on line 2\nExpecting SEMI'));
                await tick();
                assert.deepStrictEqual(page.messages(), ['renderfailed:6:Parse error on line 2 Expecting SEMI']);
                assert.ok(page.els.error.textContent.indexOf('Diagram error') === 0);

                // 4. A newer render replaces a slow asked-for one: the old one is reported as superseded,
                //    whether it would have succeeded or failed.
                page = loadPage();
                page.root.gapHunterRender('first', 7);
                page.root.gapHunterRender('second');
                page.pending[0].resolve(okSvg('stale'));
                await tick();
                assert.deepStrictEqual(page.messages(), ['rendersuperseded:7']);

                page.clear();
                page.root.gapHunterRender('third', 8);
                page.root.gapHunterRender('fourth');
                page.pending[2].reject(new Error('late failure'));
                await tick();
                assert.deepStrictEqual(page.messages(), ['rendersuperseded:8']);

                // 5. Exports: an asked-for export (sync) and a manual one differ only in their message kind.
                page = loadPage();
                page.root.gapHunterRender('graph TD\nA-->B', 9);
                page.pending[0].resolve(okSvg('diagram'));
                await tick();
                page.clear();
                page.root.gapHunterExportSvg('sync');
                page.root.gapHunterExportSvg();
                const exported = page.messages();
                assert.strictEqual(exported.length, 2);
                assert.ok(exported[0].indexOf('syncsvg:<svg') === 0, exported[0]);
                assert.ok(exported[1].indexOf('svg:<svg') === 0, exported[1]);
                assert.ok(exported[0].indexOf('diagram') > 0);

                // 6. Nothing rendered yet: both kinds say so, each in its own kind.
                page = loadPage();
                page.root.gapHunterExportSvg('sync');
                page.root.gapHunterExportSvg();
                page.root.gapHunterExportPng('sync');
                page.root.gapHunterExportPng();
                const early = page.messages();
                assert.strictEqual(early.length, 4);
                assert.ok(early[0].indexOf('syncerror:Nothing to export yet') === 0, early[0]);
                assert.ok(early[1].indexOf('error:Nothing to export yet') === 0, early[1]);
                assert.ok(early[2].indexOf('syncerror:Nothing to export yet') === 0, early[2]);
                assert.ok(early[3].indexOf('error:Nothing to export yet') === 0, early[3]);

                // 7. A failed render must leave nothing of mermaid's own error graphic in the page: mermaid is
                //    told not to draw it, and the scratch element it may leave behind ("d" + render id) is removed.
                page = loadPage();
                let removed = false;
                const stray = makeEl('stray');
                stray.remove = () => { removed = true; };
                page.root.gapHunterRender('broken', 10);
                page.strays['dgap-hunter-mermaid-svg-1'] = stray;
                page.pending[0].reject(new Error('Parse error'));
                await tick();
                assert.ok(removed, 'the scratch element left by a failed render is removed');
                assert.ok(page.configs.length > 0, 'mermaid was configured');
                assert.ok(page.configs.every((c) => c.suppressErrorRendering === true), 'mermaid never draws its own error graphic');

                console.log('ALL OK');
            })().catch((e) => { console.log(e && e.stack || e); process.exit(1); });
        """.trimIndent()
    }
}
