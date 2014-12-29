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

    var rezoom = function (xAxisRange, isReset) {
        if (isReset === undefined) {
            isReset = false;
        }

        for (id in allGraphs) {
            if (allGraphs.hasOwnProperty(id)) {
                var g = allGraphs[id];
                var opts = {
                    dateWindow: xAxisRange,
                    valueRange: isReset ? null : g.yAxisRange()
                };
                g.graph.updateOptions(opts);
                g.highlight();
            }
        }
    };

    var drawCallback = (function () {
        var currRange;
        var block = false;
        return function (me, initial) {
            if (block) return;
            block = true;

            var opts = {
                dateWindow: me.xAxisRange()
            };
            var isReset = !me.isZoomed();

            if (isReset || currRange === undefined || currRange[0] != opts.dateWindow[0] || currRange[1] != opts.dateWindow[1]) {
                currRange = opts.dateWindow;
                for (id in allGraphs) {
                    if (allGraphs.hasOwnProperty(id) && allGraphs[id].graph != me) {
                        var g = allGraphs[id];
                        var myOpts = $.extend({}, opts);
                        if (!isReset) {
                            myOpts.valueRange = g.graph.yAxisRange();
                        }
                        g.graph.updateOptions(myOpts);
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

            block = false
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

    var clickCallback = (function () {
        return function () {
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
    })();

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

                    var myId;
                    var colors = [];
                    var options = {};
                    var graph;

                    // To Re-Size on Width Changes
                    var currWidth = 0;

                    element.bind('contextmenu', function(event) {
                        event.preventDefault();
                        event.stopPropagation();
                    });

                    var onDblClick = function() {
                        rezoom(scope.options.dateWindow, true);
                    };

                    scope.formatTm = function(tm) {
                        return moment(tm).format("YYYYMMDD-hh:mm:ss");
                    };

                    scope.$watchCollection('options.dateWindow', function() {
                        if (graph !== undefined) {
                            graph.updateOptions({dateWindow: scope.dateWindow});
                        }
                    });

                    scope.$watch('lastTm', function() {
                        options.file = scope.data;

                        if (scope.lastTm == 0) {
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
                        return !scope.hovered && !(line.visible && line.selected);
                    };

                    scope.valueFor = function (line) {
                        if (scope.values !== undefined && scope.values.length > line.index + 1) {
                            return scope.values[line.index + 1];
                        }
                        return 0;
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

                            function resetAll(visible) {
                                for (i = 0; i < scope.series.length; ++i) {
                                    if (i != idx) {
                                        scope.series[i].visible = visible;
                                        graph.setVisibility(i, visible);
                                    }
                                }
                            }

                            if (allInvisible) { // Reset Visibility
                                resetAll(true);
                            } else if (allVisible) { // Turn everything off except this one.
                                resetAll(false);
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
                        var isFirstGraph = (myId === undefined);

                        resize();

                        var opts = {};
                        $.extend(opts, options);

                        opts.interactionModel = {};
                        $.extend(opts.interactionModel, Dygraph.Interaction.defaultModel);
                        opts.interactionModel.dblclick = onDblClick;

                        graph = new Dygraph(chartDiv, scope.data, opts);

                        graph.updateOptions({
                            drawCallback: drawCallback,
                            highlightCallback: highlightCallback,
                            unhighlightCallback: unhighlightCallback,
                            clickCallback: clickCallback,
                            dateWindow: scope.options.dateWindow
                        });

                        var graphModel = {
                            highlight: highlight,
                            graph: graph
                        };
                        if (isFirstGraph) {
                            myId = gid;
                            allGraphs[myId] = graphModel;
                            gid += 1;

                            scope.$on('$destroy', function () {
                                delete allGraphs[myId];
                            });
                        } else {
                            allGraphs[myId] = graphModel;
                        }

                        highlight();
                    }
                }
            };
        }]);
})();
