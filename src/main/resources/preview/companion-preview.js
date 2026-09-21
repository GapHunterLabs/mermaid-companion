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

    /** Zoom by `factor` keeping the point (cx, cy) of the viewport fixed. */
    function zoomAt(view, factor, cx, cy) {
        var scale = clamp(view.scale * factor, MIN_SCALE, MAX_SCALE);
        var k = scale / view.scale;
        return { scale: scale, x: cx - (cx - view.x) * k, y: cy - (cy - view.y) * k };
    }

    function panBy(view, dx, dy) {
        return { scale: view.scale, x: view.x + dx, y: view.y + dy };
    }

    /** Whole diagram visible and centered; never enlarged past FIT_MAX_SCALE. */
    function fit(contentW, contentH, viewportW, viewportH) {
        if (!(contentW > 0 && contentH > 0 && viewportW > 0 && viewportH > 0)) {
            return { scale: 1, x: 0, y: 0 };
        }
        var scale = clamp(Math.min((viewportW - 2 * FIT_PADDING) / contentW,
            (viewportH - 2 * FIT_PADDING) / contentH), MIN_SCALE, FIT_MAX_SCALE);
        return { scale: scale, x: (viewportW - contentW * scale) / 2, y: (viewportH - contentH * scale) / 2 };
    }

    /** 100%, centered (or pinned to the top-left edge when larger than the viewport). */
    function actualSize(contentW, contentH, viewportW, viewportH) {
        return {
            scale: 1,
            x: Math.max(FIT_PADDING, (viewportW - contentW) / 2),
            y: Math.max(FIT_PADDING, (viewportH - contentH) / 2)
        };
    }

    /** Wheel delta -> zoom factor: smooth, and the same for trackpads and wheels. */
    function wheelFactor(deltaY) {
        return Math.exp(-clamp(deltaY, -200, 200) * 0.0015);
    }

    var api = {
        MIN_SCALE: MIN_SCALE, MAX_SCALE: MAX_SCALE, FIT_MAX_SCALE: FIT_MAX_SCALE,
        zoomAt: zoomAt, panBy: panBy, fit: fit, actualSize: actualSize, wheelFactor: wheelFactor
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
    var renderSeq = 0;
    var exportSeq = 0; // separate from renderSeq: an export must never cancel a preview render
    var hasFitted = false;
    var drag = null;

    function send(payload) {
        if (typeof root.gapHunterBridge === 'function') {
            root.gapHunterBridge(payload);
        }
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

    function setError(message) {
        errorBar.textContent = message || '';
        errorBar.style.display = message ? 'block' : 'none';
    }

    function initializeMermaid(extra) {
        var config = { startOnLoad: false, theme: theme };
        for (var key in (extra || {})) {
            config[key] = extra[key];
        }
        root.mermaid.initialize(config);
    }

    root.gapHunterRender = function (text) {
        lastText = text;
        var seq = ++renderSeq;
        document.body.style.background = background();
        initializeMermaid();
        root.mermaid.render('gap-hunter-mermaid-svg-' + seq, text).then(function (result) {
            if (seq !== renderSeq) {
                return; // a newer render already started
            }
            stage.innerHTML = result.svg;
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
        }).catch(function (err) {
            // Keep the last good diagram on screen; show why the new one failed.
            setError('Diagram error: ' + ((err && err.message) || err));
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

    root.gapHunterExportSvg = function () {
        var svg = stage.querySelector('svg');
        if (!svg) {
            send('error:Nothing to export yet -- the diagram has not rendered.');
            return;
        }
        send('svg:' + serialize(svg));
    };

    /*
     * PNG goes through a canvas. A canvas that draws an SVG containing
     * <foreignObject> (mermaid's HTML labels) becomes tainted and can't be
     * exported, so the PNG is rendered separately with htmlLabels off. If a
     * diagram type still taints the canvas, report it instead of failing silently.
     */
    root.gapHunterExportPng = function () {
        if (lastText === null) {
            send('error:Nothing to export yet -- the diagram has not rendered.');
            return;
        }
        var seq = ++exportSeq;
        initializeMermaid({ htmlLabels: false, flowchart: { htmlLabels: false } });
        root.mermaid.render('gap-hunter-mermaid-png-' + seq, lastText).then(function (result) {
            initializeMermaid();
            var holder = document.createElement('div');
            holder.innerHTML = result.svg;
            var svg = holder.querySelector('svg');
            naturalize(svg);
            var w = parseFloat(svg.getAttribute('width')) || 800;
            var h = parseFloat(svg.getAttribute('height')) || 600;
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
                    send('png:' + canvas.toDataURL('image/png').split(',')[1]);
                } catch (e) {
                    send('error:PNG export isn\'t possible for this diagram type (' + e.name + ') -- export SVG instead.');
                }
            };
            img.onerror = function () {
                send('error:PNG export failed while rasterizing the diagram -- export SVG instead.');
            };
            img.src = 'data:image/svg+xml;base64,' + btoa(bytes);
        }).catch(function (err) {
            initializeMermaid();
            send('error:PNG export failed: ' + ((err && err.message) || err));
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
