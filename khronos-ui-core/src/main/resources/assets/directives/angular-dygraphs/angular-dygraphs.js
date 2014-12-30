(function () {
    var gid = 0;
    var allGraphs = {};
    var cfg = {
        seriesLocked: false,
        highlightSeries: "",
        highlightTm: new Date().getMilliseconds() / 1000
    };

    var colorScale = [
        '#3182bd',
        '#e6550d',
        '#31a354',
        '#756bb1',
        '#636363',
        '#6baed6',
        '#fd8d3c',
        '#74c476',
        '#9e9ac8',
        '#969696',
        '#9ecae1',
        '#fdae6b',
        '#a1d99b',
        '#bcbddc',
        '#bdbdbd',
        '#c6dbef',
        '#fdd0a2',
        '#c7e9c0',
        '#dadaeb',
        '#d9d9d9'
    ];

    var blockDraw = false;
    var rezoom = function (xAxisRange, isReset) {
        if (isReset === undefined) {
            isReset = false;
        }
        blockDraw = true;

        for (id in allGraphs) {
            if (allGraphs.hasOwnProperty(id)) {
                var g = allGraphs[id];
                var opts = {
                    dateWindow: xAxisRange,
                    valueRange: isReset ? null : g.graph.yAxisRange()
                };
                g.graph.updateOptions(opts);
                g.highlight();
            }
        }

        blockDraw = false;
    };

    var currTimeRange;
    var drawCallback = (function () {
        return function (me) {
            if (blockDraw) return;
            blockDraw = true;

            // On the initial draw, we haven't had the ability to set the id or put the graph in allGraphs.
            if (me.myId === undefined || allGraphs[me.myId] === undefined) {
                if (currTimeRange !== undefined) {
                    me.updateOptions({
                        dateWindow: currTimeRange,
                        valueRange: me.yAxisRange()
                    });
                }
                blockDraw = false;
                return;
            }

            var range = me.xAxisRange();

            if (currTimeRange === undefined || currTimeRange[0] != range[0] || currTimeRange[1] != range[1]) {
                console.log("Setting time range: " + range);
                currTimeRange = range;
                for (id in allGraphs) {
                    if (allGraphs.hasOwnProperty(id) && allGraphs[id].graph != me) {
                        var g = allGraphs[id];
                        g.graph.updateOptions({
                            dateWindow: range,
                            valueRange: g.graph.yAxisRange()
                        });
                        allGraphs[id].highlight();
                    }
                }
            } else {
                for (id in allGraphs) {
                    if (allGraphs.hasOwnProperty(id) && allGraphs[id].graph === me) {
                        allGraphs[id].highlight();
                    }
                }
            }

            blockDraw = false
        };
    })();

    var blockHighlight = false;
    var highlightCallback = (function () {
        return function (event, x, points, row, seriesName) {
            if (blockHighlight) return;
            blockHighlight = true;
            if (!cfg.seriesLocked) {
                cfg.highlightSeries = seriesName;
            }
            cfg.highlightTm = x;
            for (id in allGraphs) {
                if (allGraphs.hasOwnProperty(id)) {
                    allGraphs[id].highlight();
                }
            }
            blockHighlight = false;
        }
    })();

    var unhighlightCallback = (function () {
        //blockHighlight = false;
        return function (event, x, points, row, seriesName) {
            if (blockHighlight || cfg.seriesLocked) return;
            blockHighlight = true;
            for (id in allGraphs) {
                if (allGraphs.hasOwnProperty(id)) {
                    allGraphs[id].graph.clearSelection();
                }
            }
            blockHighlight = false;
        }
    })();

    var clickCallback = function () {
        blockHighlight = true;
        var me = this;
        var mSeries = me.getHighlightSeries();
        if (mSeries != me.getLabels()[0]) {
            cfg.highlightSeries = mSeries;
        }
        cfg.seriesLocked = !cfg.seriesLocked;

        for (id in allGraphs) {
            if (allGraphs.hasOwnProperty(id)) {
                allGraphs[id].highlight();
            }
        }
        blockHighlight = false;
    };

    var lockSeries = function(series) {
        blockHighlight = true;
        cfg.highlightSeries = series;
        cfg.seriesLocked = true;

        for (id in allGraphs) {
            if (allGraphs.hasOwnProperty(id)) {
                allGraphs[id].highlight();
            }
        }
        blockHighlight = false;
    };

    // Returns the index corresponding to xVal, or null if there is none.
    function dygraphsBinarySearch(g, xVal) {
        var low = 0,
            high = g.numRows();

        while (low + 1 < high) {
            var idx = (high + low) >> 1;
            var x = g.getValue(idx, 0);
            if (x <= xVal) {
                low = idx;
            } else {
                high = idx;
            }
        }

        return low;
    }

    angular.module("angular-dygraphs", [
        'ngSanitize'
    ]).directive('ngDygraphs', ['$window', '$timeout', function ($window, $timeout) {
            return {
                restrict: 'E',
                scope: { // Isolate scope
                    data: '=',
                    options: '=',
                    legend: '=?',
                    loading: '=',    // Are we currently loading data?
                    lastTm: '=',     // When to redraw because of new data.
                    legendAbove: '=' // Should we put a mini legend above the graph instead of beside?
                },
                templateUrl: "directives/angular-dygraphs/angular-dygraphs.html",
                link: function (scope, element, attrs) {
                    var parent = element.parent();
                    var mainDiv = element.children()[0];
                    var legendAboveDiv = $(mainDiv).children()[0];
                    var legendDiv = $(mainDiv).children()[1];
                    var chartDiv = $(mainDiv).children()[2];
                    var loadingDiv = $(mainDiv).children()[3];

                    var colors = [];
                    var options = {};
                    var graph;

                    // To Re-Size on Width Changes
                    var currWidth = 0;

                    scope.formatTm = function(tm) {
                        return moment(tm).format("YYYYMMDD-HH:mm:ss");
                    };

                    scope.$watchCollection('options.dateWindow', function(newValue, oldValue) {
                        if (newValue !== oldValue) {
                            currTimeRange = scope.options.dateWindow;
                            if (graph !== undefined) {
                                graph.myId = undefined;
                            }
                            graph = undefined;
                        }
                    });

                    scope.$watch('lastTm', function() {
                        options.file = scope.data;

                        if (scope.lastTm == 0) {
                            // graph's without an ID cannot update the time range.
                            if (graph !== undefined) {
                                graph.myId = undefined;
                            }
                            graph = undefined;
                        }

                        if (graph === undefined || currWidth != $(parent).width()) {
                            currWidth = $(parent).width();
                            resize();
                        }

                        if (graph === undefined && options.file.length > 0) {
                            createGraph();
                        } else if (graph !== undefined && scope.data.length > 0) {
                            graph.updateOptions({file: scope.data});
                        }
                    });

                    scope.$watchCollection('options.labels', function() {
                        scope.series = [];
                        colors = [];
                        for (var i = 0; i < scope.options.labels.length - 1; ++i) {
                            var color = chroma(colorScale[i % 20]).darken().hex();
                            colors.push(color);
                            scope.series.push({
                                label: scope.options.labels[i + 1],
                                color: color,
                                visible: true,
                                selected: false,
                                index: i
                            });
                        }
                        options.colors = colors;
                        if (graph !== undefined) {
                            graph.updateOptions({colors: colors});
                        }
                    });

                    scope.linePriority = function (line) {
                        if (scope.hovered) return 0;
                        if (line.selected) return -2;
                        if (line.visible) return -1;
                        return 0;
                    };

                    scope.valueFor = function (line) {
                        if (scope.hovered) return 0;
                        if (scope.values !== undefined && scope.values.length > line.index + 1) {
                            var x = scope.values[line.index + 1];
                            return isNaN(x) ? 0 : x;
                        }
                        return 0;
                    };

                    scope.valueForReverse = function (line) {
                        return -scope.valueFor(line);
                    };

                    scope.resetYAxis = function() {
                        var opts = {
                            valueRange: null
                        };
                        graph.updateOptions(opts);
                        highlight();
                    };

                    scope.resetXAxis = function() {
                        rezoom(scope.options.dateWindow, false);
                    };

                    scope.resetZoom = function() {
                        rezoom(scope.options.dateWindow, true);
                    };

                    scope.$watch('legendAbove', function() {
                        options.showRangeSelector = !scope.legendAbove;
                        if (graph !== undefined && options.file !== undefined && options.file.length > 0) {
                            graph.updateOptions(options);
                            resize();
                        }
                    });

                    scope.$watchCollection('options', function () {
                        for (var opt in scope.options) {
                            if (scope.options.hasOwnProperty(opt)) {
                                options[opt] = scope.options[opt];
                            }
                        }
                        options.colors = colors;
                        options.file = scope.data;
                        options.labelsDivWidth = 0;

                        if (graph === undefined && options.file.length > 0) {
                            createGraph();
                        }

                        if (graph === undefined || currWidth != $(parent).width()) {
                            currWidth = $(parent).width();
                            resize();
                        }

                        if (graph !== undefined && options.file !== undefined && options.file.length > 0) {
                            graph.updateOptions(options);
                        }
                    }, true);

                    scope.seriesStyle = function (series) {
                        if (graph !== undefined) {
                            return {color: options.colors[series]};
                        }
                    };

                    scope.resetVisibility = function(visible, except) {
                        for (i = 0; i < scope.series.length; ++i) {
                            if (i != except) {
                                scope.series[i].visible = visible;
                                graph.setVisibility(i, visible);
                            }
                        }
                    };

                    scope.selectSeries = function (idx) {
                        var i;

                        if (graph !== undefined) {
                            var isVisible = scope.series[idx].visible;
                            if (!isVisible) {
                                scope.series[idx].visible = true;
                                graph.setVisibility(idx, true);
                                return;
                            }

                            var allVisible = true;
                            var allInvisible = true;
                            for (i = 0; i < scope.series.length; ++i) {
                                if (i != idx) {
                                    if (scope.series[i].visible) {
                                        allInvisible = false;
                                    } else {
                                        allVisible = false;
                                    }
                                }
                            }

                            if (allInvisible) { // Reset Visibility
                                scope.resetVisibility(true, idx);
                            } else if (allVisible) { // Turn everything off except this one.
                                scope.resetVisibility(false, idx);
                            } else  { // Just turn this one off.
                                scope.series[idx].visible = false;
                                graph.setVisibility(idx, false);
                            }
                        }
                    };

                    scope.mouseOverSeries = function (series) {
                        if (graph !== undefined && !cfg.seriesLocked) {
                            highlightCallback({}, cfg.highlightTm, [], [], series.label);
                        }
                    };

                    scope.mouseEnterLegend = function () {
                        scope.hovered = true;
                    };

                    scope.mouseLeaveLegend = function () {
                        scope.hovered = false;
                    };

                    scope.highlightSeries = function (line) {
                        lockSeries(line.label);
                    };

                    scope.toggleSeriesVisibility = function (line) {
                        line.visible = !line.visible;
                        graph.setVisibility(line.index, line.visible);
                    };

                    scope.hideOtherSeries = function (line) {
                        scope.resetVisibility(false, line);
                        if (!line.visible) {
                            line.visible = true;
                            graph.setVisibility(line.index, true);
                        }
                    };

                    resize();

                    angular.element($window).bind('resize', resize);

                    function resize() {
                        var legendWidth = 140;
                        var width = $(parent).width() - (scope.legendAbove ? 0 : legendWidth);
                        var height = ($(parent).width() - 20) / (2 * 1.618) + 50;

                        legendDiv.setAttribute('style', 'height:' + height + 'px');
                        loadingDiv.width = width;
                        loadingDiv.setAttribute('style', 'height:' + height + 'px');
                        chartDiv.width = width;
                        chartDiv.setAttribute('style', 'height:' + height + 'px');
                        options.width = width;
                        options.height = height;

                        if (graph !== undefined) {
                            graph.resize(width, height);
                        }
                    }

                    function highlight() {
                        if (cfg.highlightTm != 0 && graph !== undefined) {
                            var fallback = graph.getLabels()[0];
                            var indexOfHighlighted = graph.getLabels().indexOf(cfg.highlightSeries);
                            var mn = indexOfHighlighted >= 0 ? cfg.highlightSeries : fallback;
                            var idx = dygraphsBinarySearch(graph, cfg.highlightTm);
                            if (idx < graph.getLeftBoundary_(idx)) {
                                idx = graph.getLeftBoundary_(idx);
                            }
                            if (graph.getSelection() != mn) {
                                graph.setSelection(idx, mn, cfg.seriesLocked);
                            }
                            $timeout(function () {
                                for (var i = 0; i < scope.series.length; ++i) {
                                    scope.series[i].selected = i == (indexOfHighlighted - 1);
                                }
                                scope.values = scope.data[idx];
                            });
                        }
                    }

                    function createGraph() {
                        var isFirstGraph = (scope.myId === undefined);

                        resize();

                        var opts = {};
                        $.extend(opts, options);

                        opts.interactionModel = {};
                        $.extend(opts.interactionModel, Dygraph.Interaction.defaultModel);
                        opts.interactionModel.dblclick = scope.resetZoom;

                        graph = new Dygraph(chartDiv, scope.data, opts);

                        graph.updateOptions({
                            drawCallback: drawCallback,
                            highlightCallback: highlightCallback,
                            unhighlightCallback: unhighlightCallback,
                            clickCallback: clickCallback,
                            dateWindow: scope.options.dateWindow
                        });

                        if (isFirstGraph) {
                            scope.myId = gid;
                            gid += 1;

                            scope.$on('$destroy', function () {
                                delete allGraphs[scope.myId];
                            });
                        }

                        graph.myId = scope.myId;
                        allGraphs[scope.myId] = {
                            highlight: highlight,
                            graph: graph,
                            myId: scope.myId
                        };

                        highlight();
                    }
                }
            };
        }]);
})();
