/*
 * Mermaid Companion preview page script: zoom, pan, theme and export on top
 * of the bundled mermaid.js. The view math at the top is pure and exported
 * for Node, so it is unit-tested without a browser (PreviewScriptTest); the
 * browser wiring below only runs when there is a DOM.
 */
(function (root) {
    'use strict';

    var MIN_SCALE = 0.1;
    var MAX_SCALE = 8;
    var FIT_MAX_SCALE = 2;
    var FIT_PADDING = 16;

    function clamp(value, lo, hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /** True when every argument is a finite number (not NaN, not +/-Infinity). */
    function finite() {
        for (var i = 0; i < arguments.length; i++) {
            if (typeof arguments[i] !== 'number' || !isFinite(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    /*
     * A view that is not finite would stick: every later zoom or pan starts from it, and the
     * diagram disappears until Fit. So an input that would produce one leaves the view as it is.
     */
    function keepFinite(next, view) {
        return finite(next.scale, next.x, next.y) && next.scale > 0 ? next : view;
    }

    /** Zoom by `factor` keeping the point (cx, cy) of the viewport fixed. */
    function zoomAt(view, factor, cx, cy) {
        if (!finite(factor, cx, cy) || !(factor > 0)) {
            return view;
        }
        var scale = clamp(view.scale * factor, MIN_SCALE, MAX_SCALE);
        var k = scale / view.scale;
        return keepFinite({ scale: scale, x: cx - (cx - view.x) * k, y: cy - (cy - view.y) * k }, view);
    }

    function panBy(view, dx, dy) {
        if (!finite(dx, dy)) {
            return view;
        }
        return keepFinite({ scale: view.scale, x: view.x + dx, y: view.y + dy }, view);
    }

    /** Whole diagram visible and centered; never enlarged past FIT_MAX_SCALE. */
    function fit(contentW, contentH, viewportW, viewportH) {
        if (!(finite(contentW, contentH, viewportW, viewportH) &&
            contentW > 0 && contentH > 0 && viewportW > 0 && viewportH > 0)) {
            return { scale: 1, x: 0, y: 0 };
        }
        var scale = clamp(Math.min((viewportW - 2 * FIT_PADDING) / contentW,
            (viewportH - 2 * FIT_PADDING) / contentH), MIN_SCALE, FIT_MAX_SCALE);
        return { scale: scale, x: (viewportW - contentW * scale) / 2, y: (viewportH - contentH * scale) / 2 };
    }

    /** 100%, centered (or pinned to the top-left edge when larger than the viewport). */
    function actualSize(contentW, contentH, viewportW, viewportH) {
        if (!finite(contentW, contentH, viewportW, viewportH)) {
            return { scale: 1, x: FIT_PADDING, y: FIT_PADDING };
        }
        return {
            scale: 1,
            x: Math.max(FIT_PADDING, (viewportW - contentW) / 2),
            y: Math.max(FIT_PADDING, (viewportH - contentH) / 2)
        };
    }

    /** Wheel delta -> zoom factor: smooth, and the same for trackpads and wheels. */
    function wheelFactor(deltaY) {
        if (typeof deltaY !== 'number' || isNaN(deltaY)) {
            return 1;
        }
        return Math.exp(-clamp(deltaY, -200, 200) * 0.0015);
    }

    /*
     * mermaid.js renders a diagram longer than its maxTextSize (50,000 characters by default)
     * as a stand-in diagram that only says so, and reports it as a success. Treated as a
     * failure here, so the preview keeps the last good diagram and Export on Save never
     * writes the stand-in over a real export.
     */
    var TOO_LARGE_MARK = 'Maximum text size in diagram exceeded';

    function isTooLargeStandIn(text, svg) {
        return String(svg).indexOf(TOO_LARGE_MARK) !== -1 && String(text).indexOf(TOO_LARGE_MARK) === -1;
    }

    /*
     * Chromium refuses a canvas past these sizes (toDataURL then returns "data:,"), after
     * spending up to a minute drawing it. Checked before drawing; the empty answer is caught
     * too, for a browser with lower limits.
     */
    var MAX_CANVAS_SIDE = 65535;
    var MAX_CANVAS_AREA = 268435456;

    function canvasFits(width, height) {
        return finite(width, height) && width > 0 && height > 0 &&
            width <= MAX_CANVAS_SIDE && height <= MAX_CANVAS_SIDE && width * height <= MAX_CANVAS_AREA;
    }

    var api = {
        MIN_SCALE: MIN_SCALE, MAX_SCALE: MAX_SCALE, FIT_MAX_SCALE: FIT_MAX_SCALE,
        zoomAt: zoomAt, panBy: panBy, fit: fit, actualSize: actualSize, wheelFactor: wheelFactor,
        isTooLargeStandIn: isTooLargeStandIn, canvasFits: canvasFits
    };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    if (typeof document === 'undefined') {
        return;
    }

    // --- browser wiring -------------------------------------------------------

    var viewport = document.getElementById('viewport');
    var stage = document.getElementById('stage');
    var errorBar = document.getElementById('error');
    var view = { scale: 1, x: 0, y: 0 };
    var theme = 'default';
    var lastText = null;
    var lastGoodText = null; // the text of the last render that succeeded: what an export exports
    var renderSeq = 0;
    var hasFitted = false;
    var drag = null;

    function send(payload) {
        if (typeof root.gapHunterBridge === 'function') {
            root.gapHunterBridge(payload);
        }
    }

    /*
     * Export answers. `mode === 'sync'` marks an export the plugin asked for
     * itself (Export on Save): it comes back as syncsvg / syncpng / syncerror
     * so it is written without a file dialog. Anything else is a manual
     * export from the toolbar and keeps the save dialog.
     */
    function out(mode, kind, payload) {
        send((mode === 'sync' ? 'sync' : '') + kind + ':' + payload);
    }

    function apply() {
        stage.style.transform = 'translate(' + view.x + 'px,' + view.y + 'px) scale(' + view.scale + ')';
        send('zoom:' + Math.round(view.scale * 100));
    }

    /** Natural diagram size: mermaid's default SVG is width="100%" with a max-width, which would fight the zoom. */
    function naturalize(svg) {
        var vb = svg.viewBox && svg.viewBox.baseVal;
        if (vb && vb.width > 0 && vb.height > 0) {
            svg.setAttribute('width', vb.width);
            svg.setAttribute('height', vb.height);
            svg.style.maxWidth = 'none';
        }
    }

    function contentSize() {
        var svg = stage.querySelector('svg');
        if (!svg) {
            return null;
        }
        return { w: parseFloat(svg.getAttribute('width')) || 0, h: parseFloat(svg.getAttribute('height')) || 0 };
    }

    function viewportCenter() {
        return { x: viewport.clientWidth / 2, y: viewport.clientHeight / 2 };
    }

    function background() {
        return theme === 'dark' ? '#1e1e1e' : '#ffffff';
    }

    function maxTextSize() {
        try {
            var size = root.mermaid.mermaidAPI.getConfig().maxTextSize;
            return typeof size === 'number' ? size : 50000;
        } catch (e) {
            return 50000;
        }
    }

    function tooLargeForPng(w, h) {
        return 'PNG export isn\'t possible for a diagram this large (' + Math.round(w) + ' x ' + Math.round(h) +
            ' px) -- export SVG instead.';
    }

    function setError(message) {
        errorBar.textContent = message || '';
        errorBar.style.display = message ? 'block' : 'none';
    }

    /*
     * A render that fails must show nothing of its own. By default mermaid draws
     * its "Syntax error in text" bomb into the page and leaves it there, one more
     * for every failed render (every pause while typing an invalid diagram); the
     * error is reported through the error bar instead. discardStray removes the
     * scratch element mermaid may leave behind ("d" + the render id).
     */
    function discardStray(renderId) {
        var stray = document.getElementById('d' + renderId);
        if (stray && typeof stray.remove === 'function') {
            stray.remove();
        }
    }

    /*
     * handDrawnSeed: some shapes (asymmetric, hexagon, ER entities, state boxes...) are drawn
     * with random curve points unless the seed is fixed, which made two renders of the same
     * diagram differ and Export on Save rewrite an unchanged file.
     */
    function configFor(extra) {
        var config = { startOnLoad: false, theme: theme, suppressErrorRendering: true, handDrawnSeed: 1 };
        for (var key in (extra || {})) {
            config[key] = extra[key];
        }
        return config;
    }

    function initializeMermaid(extra) {
        root.mermaid.initialize(configFor(extra));
    }

    /*
     * Exports render in a page of their own. mermaid.js numbers some elements with counters that
     * live as long as the page (sequence actors, class ids, sankey links), so the same diagram
     * rendered twice in the preview page gives different SVG, and Export on Save rewrote an
     * unchanged file. A new iframe starts every counter from zero. It has no src, so it shares
     * this page's origin, and the bundled script is copied into it as text: nothing is fetched.
     */
    var EXPORT_ID = 'mermaid-diagram'; // the same id SvgNormalizer.kt writes

    function freshMermaid() {
        var lib = document.getElementById('mermaid-lib');
        if (!lib || !lib.textContent) {
            return Promise.reject(new Error('the bundled mermaid.js is missing from the preview page'));
        }
        var frame = document.createElement('iframe');
        frame.setAttribute('aria-hidden', 'true');
        frame.tabIndex = -1;
        // Laid out (mermaid measures text) but never shown; visibility, not display.
        frame.style.cssText = 'position:absolute;left:-20000px;top:0;width:2000px;height:2000px;border:0;visibility:hidden';
        document.body.appendChild(frame);
        var dispose = function () {
            if (frame.parentNode) {
                frame.parentNode.removeChild(frame);
            }
        };
        var win = frame.contentWindow;
        var doc = frame.contentDocument || (win && win.document);
        if (!win || !doc) {
            dispose();
            return Promise.reject(new Error('could not open a separate page to render the export'));
        }
        var script = doc.createElement('script');
        script.text = lib.textContent;
        (doc.head || doc.documentElement || doc.body).appendChild(script);
        if (!win.mermaid || typeof win.mermaid.render !== 'function') {
            dispose();
            return Promise.reject(new Error('the bundled mermaid.js did not start in the separate page'));
        }
        return Promise.resolve({ mermaid: win.mermaid, dispose: dispose });
    }

    function renderIsolated(text, extra) {
        return freshMermaid().then(function (m) {
            m.mermaid.initialize(configFor(extra));
            return m.mermaid.render(EXPORT_ID, text).then(function (result) {
                m.dispose();
                return result;
            }, function (err) {
                m.dispose();
                throw err;
            });
        });
    }

    function errorText(err) {
        return String((err && err.message) || err).replace(/\s+/g, ' ');
    }

    /*
     * `token` is set only for a render the plugin asked for on purpose (Export
     * on Save). Its outcome is reported back with that token: rendered,
     * renderfailed, or rendersuperseded when a newer render replaced it before
     * it finished -- so an unrelated (debounced) render is never mistaken for it.
     */
    root.gapHunterRender = function (text, token) {
        lastText = text;
        var tag = token === undefined ? '' : String(token);
        var seq = ++renderSeq;
        document.body.style.background = background();
        initializeMermaid();
        var renderId = 'gap-hunter-mermaid-svg-' + seq;
        root.mermaid.render(renderId, text).then(function (result) {
            if (seq !== renderSeq) {
                if (tag) {
                    send('rendersuperseded:' + tag);
                }
                return; // a newer render already started
            }
            if (isTooLargeStandIn(text, result.svg)) {
                var tooLarge = 'the diagram is longer than the ' + maxTextSize() + ' characters mermaid.js renders.';
                setError('Diagram error: ' + tooLarge);
                if (tag) {
                    send('renderfailed:' + tag + ':' + tooLarge);
                }
                return;
            }
            stage.innerHTML = result.svg;
            lastGoodText = text;
            var svg = stage.querySelector('svg');
            if (svg) {
                naturalize(svg);
            }
            setError(null);
            if (!hasFitted) {
                hasFitted = true;
                root.gapHunterFit();
            } else {
                apply();
            }
            if (tag) {
                send('rendered:' + tag);
            }
        }).catch(function (err) {
            // Keep the last good diagram on screen; show why the new one failed.
            discardStray(renderId);
            var message = (err && err.message) || String(err);
            if (seq !== renderSeq) {
                if (tag) {
                    send('rendersuperseded:' + tag);
                }
                return;
            }
            setError('Diagram error: ' + message);
            if (tag) {
                send('renderfailed:' + tag + ':' + message.replace(/\s+/g, ' '));
            }
        });
    };

    root.gapHunterZoom = function (factor) {
        var c = viewportCenter();
        view = zoomAt(view, factor, c.x, c.y);
        apply();
    };

    root.gapHunterFit = function () {
        var size = contentSize();
        if (size) {
            view = fit(size.w, size.h, viewport.clientWidth, viewport.clientHeight);
            apply();
        }
    };

    root.gapHunterActualSize = function () {
        var size = contentSize();
        if (size) {
            view = actualSize(size.w, size.h, viewport.clientWidth, viewport.clientHeight);
            apply();
        }
    };

    root.gapHunterSetTheme = function (name) {
        theme = name;
        if (lastText !== null) {
            root.gapHunterRender(lastText);
        }
    };

    function serialize(svg) {
        var clone = svg.cloneNode(true);
        clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
        clone.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
        return new XMLSerializer().serializeToString(clone);
    }

    var NOTHING_YET = 'Nothing to export yet -- the diagram has not rendered.';

    /** The export's own SVG element, sized like the preview's (see naturalize). */
    function exportSvgElement(markup) {
        var holder = document.createElement('div');
        holder.innerHTML = markup;
        var svg = holder.querySelector('svg');
        if (svg) {
            naturalize(svg);
        }
        return svg;
    }

    /*
     * Exports the last diagram that rendered, never text that is still broken while it is
     * being typed: the preview shows that diagram too.
     */
    root.gapHunterExportSvg = function (mode) {
        var text = lastGoodText;
        if (text === null) {
            out(mode, 'error', NOTHING_YET);
            return;
        }
        renderIsolated(text).then(function (result) {
            if (isTooLargeStandIn(text, result.svg)) {
                out(mode, 'error', 'SVG export failed: the diagram is longer than the ' + maxTextSize() + ' characters mermaid.js renders.');
                return;
            }
            var svg = exportSvgElement(result.svg);
            if (!svg) {
                out(mode, 'error', 'SVG export failed: mermaid.js returned no SVG.');
                return;
            }
            out(mode, 'svg', serialize(svg));
        }).catch(function (err) {
            out(mode, 'error', 'SVG export failed: ' + errorText(err));
        });
    };

    /*
     * PNG goes through a canvas. A canvas that draws an SVG containing
     * <foreignObject> (mermaid's HTML labels) becomes tainted and can't be
     * exported, so the PNG is rendered separately with htmlLabels off. If a
     * diagram type still taints the canvas, report it instead of failing silently.
     */
    root.gapHunterExportPng = function (mode) {
        var text = lastGoodText;
        if (text === null) {
            out(mode, 'error', NOTHING_YET);
            return;
        }
        renderIsolated(text, { htmlLabels: false, flowchart: { htmlLabels: false } }).then(function (result) {
            if (isTooLargeStandIn(text, result.svg)) {
                out(mode, 'error', 'PNG export failed: the diagram is longer than the ' + maxTextSize() + ' characters mermaid.js renders.');
                return;
            }
            var svg = exportSvgElement(result.svg);
            if (!svg) {
                out(mode, 'error', 'PNG export failed: mermaid.js returned no SVG.');
                return;
            }
            var w = parseFloat(svg.getAttribute('width')) || 800;
            var h = parseFloat(svg.getAttribute('height')) || 600;
            if (!canvasFits(Math.ceil(w * 2), Math.ceil(h * 2))) {
                out(mode, 'error', tooLargeForPng(w, h));
                return;
            }
            var bytes = unescape(encodeURIComponent(serialize(svg)));
            var img = new Image();
            img.onload = function () {
                var canvas = document.createElement('canvas');
                canvas.width = Math.ceil(w * 2);
                canvas.height = Math.ceil(h * 2);
                var ctx = canvas.getContext('2d');
                ctx.fillStyle = background();
                ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                try {
                    var data = canvas.toDataURL('image/png').split(',')[1];
                    if (!data) {
                        out(mode, 'error', tooLargeForPng(w, h));
                        return;
                    }
                    out(mode, 'png', data);
                } catch (e) {
                    out(mode, 'error', 'PNG export isn\'t possible for this diagram type (' + e.name + ') -- export SVG instead.');
                }
            };
            img.onerror = function () {
                out(mode, 'error', 'PNG export failed while rasterizing the diagram -- export SVG instead.');
            };
            img.src = 'data:image/svg+xml;base64,' + btoa(bytes);
        }).catch(function (err) {
            out(mode, 'error', 'PNG export failed: ' + errorText(err));
        });
    };

    viewport.addEventListener('wheel', function (e) {
        e.preventDefault();
        var rect = viewport.getBoundingClientRect();
        if (e.ctrlKey || e.metaKey) {
            // Ctrl+wheel, and trackpad pinch (Chromium reports it as ctrlKey).
            view = zoomAt(view, wheelFactor(e.deltaY), e.clientX - rect.left, e.clientY - rect.top);
        } else {
            view = panBy(view, -e.deltaX, -e.deltaY);
        }
        apply();
    }, { passive: false });

    viewport.addEventListener('mousedown', function (e) {
        if (e.button !== 0) {
            return;
        }
        drag = { x: e.clientX, y: e.clientY };
        viewport.style.cursor = 'grabbing';
        e.preventDefault();
    });
    window.addEventListener('mousemove', function (e) {
        if (!drag) {
            return;
        }
        view = panBy(view, e.clientX - drag.x, e.clientY - drag.y);
        drag = { x: e.clientX, y: e.clientY };
        apply();
    });
    window.addEventListener('mouseup', function () {
        drag = null;
        viewport.style.cursor = 'grab';
    });
    viewport.addEventListener('dblclick', function () {
        root.gapHunterFit();
    });
})(this);
