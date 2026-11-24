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

    /*
     * PNG options from the plugin: a scale of 1, 2 or 3 (anything else is the default, 2) and
     * a transparent background (only an explicit true; the theme's background otherwise).
     */
    var PNG_SCALES = [1, 2, 3];
    var DEFAULT_PNG_SCALE = 2;

    function pngOptions(options) {
        var o = options && typeof options === 'object' ? options : {};
        return {
            scale: PNG_SCALES.indexOf(o.scale) >= 0 ? o.scale : DEFAULT_PNG_SCALE,
            transparent: o.transparent === true
        };
    }

    /*
     * Diagram themes. The four mermaid.js ships, and presets built on its `base` theme (the only one
     * mermaid lets a page recolor). Each preset names the background it is drawn on: the preview and
     * a PNG use it. Text against its background reaches 4.5:1 and lines and borders 3:1 (WCAG AA),
     * checked by PreviewScriptNegativeTest. An id that is not listed here is the default theme.
     */
    var BUILT_IN_THEMES = { 'default': '#ffffff', 'neutral': '#ffffff', 'forest': '#ffffff', 'dark': '#1e1e1e',
        'neo': '#ffffff', 'neo-dark': '#333333', 'redux': '#ffffff', 'redux-dark': '#333333',
        'redux-color': '#ffffff', 'redux-dark-color': '#333333' };
    var THEME_PRESETS = {
        'high-contrast': { background: '#ffffff', vars: {
            primaryColor: '#ffffff', primaryTextColor: '#000000', primaryBorderColor: '#000000', lineColor: '#000000',
            secondaryColor: '#f2f2f2', tertiaryColor: '#ffffff', textColor: '#000000', titleColor: '#000000',
            clusterBkg: '#ffffff', clusterBorder: '#000000', edgeLabelBackground: '#ffffff',
            noteBkgColor: '#ffffff', noteTextColor: '#000000', noteBorderColor: '#000000',
            actorBkg: '#ffffff', actorBorder: '#000000', actorTextColor: '#000000', signalColor: '#000000', signalTextColor: '#000000' } },
        'blueprint': { background: '#0b2545', vars: {
            darkMode: true, background: '#0b2545',
            primaryColor: '#13315c', primaryTextColor: '#ffffff', primaryBorderColor: '#8da9c4', lineColor: '#eef4ed',
            secondaryColor: '#134074', tertiaryColor: '#0b2545', textColor: '#eef4ed', titleColor: '#ffffff',
            clusterBkg: '#13315c', clusterBorder: '#8da9c4', edgeLabelBackground: '#0b2545',
            noteBkgColor: '#134074', noteTextColor: '#ffffff', noteBorderColor: '#8da9c4',
            actorBkg: '#13315c', actorBorder: '#8da9c4', actorTextColor: '#ffffff', signalColor: '#eef4ed', signalTextColor: '#eef4ed' } },
        'monochrome': { background: '#ffffff', vars: {
            primaryColor: '#e8e8e8', primaryTextColor: '#1a1a1a', primaryBorderColor: '#4d4d4d', lineColor: '#333333',
            secondaryColor: '#d4d4d4', tertiaryColor: '#f5f5f5', textColor: '#1a1a1a', titleColor: '#1a1a1a',
            clusterBkg: '#f5f5f5', clusterBorder: '#4d4d4d', edgeLabelBackground: '#ffffff',
            noteBkgColor: '#f5f5f5', noteTextColor: '#1a1a1a', noteBorderColor: '#4d4d4d',
            actorBkg: '#e8e8e8', actorBorder: '#4d4d4d', actorTextColor: '#1a1a1a', signalColor: '#333333', signalTextColor: '#1a1a1a' } }
    };

    function knownTheme(id) {
        return Object.prototype.hasOwnProperty.call(BUILT_IN_THEMES, id) || Object.prototype.hasOwnProperty.call(THEME_PRESETS, id);
    }

    /* The part of mermaid's configuration a theme sets: { theme } or { theme: 'base', themeVariables }. */
    function themeConfig(id) {
        if (Object.prototype.hasOwnProperty.call(THEME_PRESETS, id)) {
            var vars = {};
            for (var k in THEME_PRESETS[id].vars) {
                vars[k] = THEME_PRESETS[id].vars[k];
            }
            return { theme: 'base', themeVariables: vars };
        }
        return { theme: Object.prototype.hasOwnProperty.call(BUILT_IN_THEMES, id) ? id : 'default' };
    }

    function themeBackground(id) {
        if (Object.prototype.hasOwnProperty.call(THEME_PRESETS, id)) {
            return THEME_PRESETS[id].background;
        }
        return Object.prototype.hasOwnProperty.call(BUILT_IN_THEMES, id) ? BUILT_IN_THEMES[id] : BUILT_IN_THEMES['default'];
    }

    /*
     * mermaid.js's looks. Anything else is classic: mermaid itself draws an unknown look in a third way.
     * handDrawn uses the fixed handDrawnSeed of every render, so its strokes are the same each time.
     */
    var LOOKS = ['classic', 'handDrawn', 'neo'];

    function lookConfig(id) {
        return { look: LOOKS.indexOf(id) >= 0 ? id : 'classic' };
    }

    function themeIds() {
        return Object.keys(BUILT_IN_THEMES).concat(Object.keys(THEME_PRESETS));
    }

    /* The message prefix of an export: Export on Save ('sync'), the clipboard ('copy'), or a save dialog. */
    function exportPrefix(mode) {
        return mode === 'sync' || mode === 'copy' ? mode : '';
    }

    /*
     * What a click on the rendered diagram points at, from one SVG element: its id without the
     * render's prefix, its class list and the name or data-id mermaid gives sequence actors.
     * Returns 'name:<id>' (a node, class, state, entity, subgraph, requirement or participant, as
     * written in the source), 'index:<n>' (the n-th mindmap node) or null. Edges are left out: their
     * ids (L_a_b_0) cannot be split back into two node ids when an id contains an underscore.
     */
    var NODE_ID_PATTERNS = [/^flowchart-(.+)-\d+$/, /^classId-(.+)-\d+$/, /^state-(.+)-\d+$/, /^entity-(.+)-\d+$/];
    var MAX_TARGET_LENGTH = 256;

    function hasClass(classes, name) {
        return (' ' + String(classes || '') + ' ').indexOf(' ' + name + ' ') !== -1;
    }

    /*
     * `hasText` is false for an element without visible text: the start and end dots mermaid draws
     * inside a composite state ("Moving_start") are its own, not something written in the source.
     */
    function clickTarget(id, classes, actorName, hasText) {
        var named = function (value) {
            return value && value.length <= MAX_TARGET_LENGTH && !/[\u0000-\u001f\u007f]/.test(value) ? 'name:' + value : null;
        };
        if (actorName && (hasClass(classes, 'actor') || hasClass(classes, 'actor-man'))) {
            return named(String(actorName));
        }
        id = String(id || '');
        if (!id) {
            return null;
        }
        for (var i = 0; i < NODE_ID_PATTERNS.length; i++) {
            var m = NODE_ID_PATTERNS[i].exec(id);
            if (m) {
                var name = m[1].replace(/----note$/, ''); // a state's note points at its state
                if (name === 'root_start' || name === 'root_end' || (hasText === false && /_(start|end)$/.test(name))) {
                    return null;
                }
                return named(name);
            }
        }
        var mind = /^node_(\d{1,6})$/.exec(id);
        if (mind && hasClass(classes, 'mindmap-node')) {
            return 'index:' + mind[1];
        }
        if (/^node-\d+$/.test(id)) {
            return null; // sankey nodes: numbered by mermaid, their names are only labels
        }
        if (hasClass(classes, 'cluster') || hasClass(classes, 'node')) {
            return named(id);
        }
        return null;
    }

    /* A press that travels farther than this is a drag (pan), not a click. */
    var CLICK_SLOP = 4;

    function isClick(start, end) {
        return !!start && !!end && finite(start.x, start.y, end.x, end.y) &&
            Math.abs(end.x - start.x) <= CLICK_SLOP && Math.abs(end.y - start.y) <= CLICK_SLOP;
    }

    var api = {
        MIN_SCALE: MIN_SCALE, MAX_SCALE: MAX_SCALE, FIT_MAX_SCALE: FIT_MAX_SCALE,
        zoomAt: zoomAt, panBy: panBy, fit: fit, actualSize: actualSize, wheelFactor: wheelFactor,
        isTooLargeStandIn: isTooLargeStandIn, canvasFits: canvasFits, pngOptions: pngOptions, exportPrefix: exportPrefix,
        clickTarget: clickTarget, isClick: isClick,
        themeConfig: themeConfig, themeBackground: themeBackground, themeIds: themeIds, THEME_PRESETS: THEME_PRESETS,
        lookConfig: lookConfig, LOOKS: LOOKS
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
    var look = 'classic';
    var lastText = null;
    var lastGoodText = null; // the text of the last render that succeeded: what an export exports
    var renderSeq = 0;
    var hasFitted = false;
    var drag = null;
    var press = null;       // where the last mouse press started, to tell a click from a drag
    var shownRenderId = ''; // the id of the render on screen: the prefix of its element ids

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
        send(exportPrefix(mode) + kind + ':' + payload);
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
        return themeBackground(theme);
    }

    function maxTextSize() {
        try {
            var size = root.mermaid.mermaidAPI.getConfig().maxTextSize;
            return typeof size === 'number' ? size : 50000;
        } catch (e) {
            return 50000;
        }
    }

    function tooLargeForPng(w, h, scale) {
        return 'PNG export isn\'t possible for a diagram this large (' + Math.round(w) + ' x ' + Math.round(h) +
            ' px at ' + scale + 'x) -- export SVG instead.';
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
        var config = { startOnLoad: false, suppressErrorRendering: true, handDrawnSeed: 1 };
        var t = themeConfig(theme);
        for (var tk in t) {
            config[tk] = t[tk];
        }
        config.look = lookConfig(look).look;
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
            shownRenderId = renderId;
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

    root.gapHunterSetLook = function (name) {
        look = lookConfig(name).look;
        if (lastText !== null) {
            root.gapHunterRender(lastText);
        }
    };

    root.gapHunterSetTheme = function (name) {
        theme = knownTheme(name) ? name : 'default';
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
    /* `options`: { scale: 1 | 2 | 3, transparent: true | false }, see pngOptions. */
    root.gapHunterExportPng = function (mode, options) {
        var png = pngOptions(options);
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
            var cw = Math.ceil(w * png.scale);
            var ch = Math.ceil(h * png.scale);
            if (!canvasFits(cw, ch)) {
                out(mode, 'error', tooLargeForPng(w, h, png.scale));
                return;
            }
            var bytes = unescape(encodeURIComponent(serialize(svg)));
            var img = new Image();
            img.onload = function () {
                var canvas = document.createElement('canvas');
                canvas.width = cw;
                canvas.height = ch;
                var ctx = canvas.getContext('2d');
                if (!png.transparent) {
                    ctx.fillStyle = background();
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                }
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                try {
                    var data = canvas.toDataURL('image/png').split(',')[1];
                    if (!data) {
                        out(mode, 'error', tooLargeForPng(w, h, png.scale));
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
        press = { x: e.clientX, y: e.clientY };
        viewport.style.cursor = 'grabbing';
        e.preventDefault();
    });

    /*
     * A click on a node asks the plugin to move the caret to it. The element ids carry the
     * render's id as a prefix ("<render id>-flowchart-A-0"), which is removed first.
     */
    function targetAt(el) {
        var prefix = shownRenderId ? shownRenderId + '-' : '';
        for (var n = el; n && n !== stage && n.getAttribute; n = n.parentNode) {
            var id = n.getAttribute('id') || '';
            if (prefix && id.indexOf(prefix) === 0) {
                id = id.substring(prefix.length);
            }
            var classes = n.getAttribute('class');
            var actor = n.getAttribute('name') || n.getAttribute('data-id');
            // A participant's label text has no name of its own: the box next to it (or its group) has it.
            if (!actor && hasClass(classes, 'actor') && n.parentNode && n.parentNode.querySelector) {
                var box = n.parentNode.querySelector('[name]');
                actor = (box && box.getAttribute('name')) || (n.parentNode.getAttribute && n.parentNode.getAttribute('data-id'));
            }
            var t = clickTarget(id, classes, actor, !!(n.textContent && n.textContent.trim()));
            if (t) {
                return t;
            }
        }
        return null;
    }

    /*
     * A diagram's links (click A "https://...") are <a href> elements: a click on one would load
     * that website inside the preview. Their default action is cancelled before anything else; the
     * click still moves the caret to the node, like any other. (NavigationGuard.kt is the second line.)
     */
    function insideLink(el) {
        for (var n = el; n && n !== stage; n = n.parentNode) {
            if (n.tagName && String(n.tagName).toLowerCase() === 'a') {
                return true;
            }
        }
        return false;
    }

    stage.addEventListener('click', function (e) {
        if (insideLink(e.target)) {
            e.preventDefault();
        }
    }, true);

    viewport.addEventListener('click', function (e) {
        var start = press;
        press = null;
        if (!isClick(start, { x: e.clientX, y: e.clientY })) {
            return;
        }
        var target = targetAt(e.target);
        if (target) {
            send('goto:' + target);
        }
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
