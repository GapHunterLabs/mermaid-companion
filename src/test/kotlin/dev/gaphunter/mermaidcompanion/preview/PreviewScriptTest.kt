package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the real `preview/companion-preview.js` in Node and checks its pure
 * view math (zoom around the cursor, pan, fit, 100%, wheel factor) with
 * Node's own assert module. The browser wiring can't run headless; the math
 * that decides where the diagram ends up can. Skipped, not failed, on a
 * machine without Node.
 */
class PreviewScriptTest {

    private val script = File("src/main/resources/preview/companion-preview.js").absoluteFile

    private fun nodeAvailable(): Boolean = try {
        ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor(20, TimeUnit.SECONDS)
    } catch (e: Exception) {
        false
    }

    @Test
    fun viewMathBehavesInNode() {
        assumeTrue("node is not installed", nodeAvailable())
        val harness = File.createTempFile("companion-preview-test", ".js").apply { deleteOnExit() }
        harness.writeText(
            """
            const assert = require('assert');
            const p = require(${jsString(script.path)});
            const near = (a, b) => assert.ok(Math.abs(a - b) < 1e-9, a + ' != ' + b);

            // Zooming keeps the point under the cursor fixed.
            let v = p.zoomAt({ scale: 1, x: 0, y: 0 }, 2, 100, 50);
            near(v.scale, 2); near((100 - v.x) / v.scale, 100); near((50 - v.y) / v.scale, 50);

            // Clamped at both ends, and a clamped zoom doesn't drift.
            v = p.zoomAt({ scale: p.MAX_SCALE, x: 10, y: 20 }, 2, 300, 300);
            near(v.scale, p.MAX_SCALE); near(v.x, 10); near(v.y, 20);
            near(p.zoomAt({ scale: p.MIN_SCALE, x: 0, y: 0 }, 0.5, 0, 0).scale, p.MIN_SCALE);

            v = p.panBy({ scale: 3, x: 5, y: 5 }, -2, 7);
            near(v.scale, 3); near(v.x, 3); near(v.y, 12);

            // Fit: 1000x500 into 532x282 (16px padding) -> 0.5, centered.
            v = p.fit(1000, 500, 532, 282);
            near(v.scale, 0.5); near(v.x, 16); near(v.y, 16);
            // A tiny diagram is never blown up past FIT_MAX_SCALE.
            near(p.fit(10, 10, 1000, 1000).scale, p.FIT_MAX_SCALE);
            // Nothing measurable yet -> identity, never NaN.
            assert.deepStrictEqual(p.fit(0, 0, 800, 600), { scale: 1, x: 0, y: 0 });

            // 100%: centered, or pinned to the padding when larger than the viewport.
            v = p.actualSize(2000, 100, 800, 600);
            near(v.scale, 1); near(v.x, 16); near(v.y, 250);

            // Scrolling up zooms in; huge deltas are clamped.
            assert.ok(p.wheelFactor(-100) > 1 && p.wheelFactor(100) < 1);
            near(p.wheelFactor(1000), p.wheelFactor(200));

            console.log('ALL OK');
            """.trimIndent(),
        )
        val process = ProcessBuilder("node", harness.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        assertEquals(output, "ALL OK", output.trim())
    }

    private fun jsString(path: String) = "'" + path.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
