(function () {
    var gid = 0;
    var allGraphs = {};

    var drawCallback = (function () {
        var currRange;
        var block = false;
        return function (me, initial) {
            if (block || initial) return;
            block = true;
            var opts = {
                dateWindow: me.xAxisRange()
            };
            if (currRange === undefined || currRange[0] != opts.dateWindow[0] || currRange[1] != opts.dateWindow[1]) {
                currRange = opts.dateWindow;
                for (id in allGraphs) {
                    if (allGraphs.hasOwnProperty(id)) {
                        allGraphs[id].updateOptions(opts);
                    }
                }
            }
            block = false
        };
    })();

    var highlightCallback = (function() {
        var block = false;
        return function(event, x, points, row, seriesName) {
            if (block) return;
            block = true;
            var me = this;
            for (id in allGraphs) {
                if (allGraphs.hasOwnProperty(id) && me != allGraphs[id]) {
                    var idx = dygraphsBinarySearch(allGraphs[id], x);
                    if (idx !== null) {
                        allGraphs[id].setSelection(idx, seriesName);
                    }
                }
            }
            block = false;
        }
    })();

    var unhighlightCallback = (function() {
        var block = false;
        return function(event, x, points, row, seriesName) {
            if (block) return;
            block = true;
            var me = this;
            for (id in allGraphs) {
                if (allGraphs.hasOwnProperty(id) && me != allGraphs[id]) {
                    var idx = dygraphsBinarySearch(allGraphs[id], x);
                    if (idx !== null) {
                        allGraphs[id].clearSelection();
                    }
                }
            }
            block = false;
        }
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
    ])
        .directive('ngDygraphs', ['$window', '$sce', function ($window, $sce) {
            return {
                restrict: 'E',
                scope: { // Isolate scope
                    data: '=',
                    options: '=',
                    legend: '=?',
                    lastTm: '=' // When to redraw because of new data.
                },
                templateUrl: "directives/angular-dygraphs/angular-dygraphs.html",
                link: function (scope, element, attrs) {
                    scope.LegendEnabled = true;

                    var parent = element.parent();
                    var mainDiv = element.children()[0];
                    var chartDiv = $(mainDiv).children()[0];
                    var legendDiv = $(mainDiv).children()[1];
                    var popover = element.find('.dypopover');

                    var popoverWidth = 0;
                    var popoverHeight = 0;
                    var chartArea;
                    var popoverPos = false;
                    var options = {};

                    var myOptions = {};
                    var graph;

                    var currWidth = 0;

                    scope.$watchGroup(['lastTm', 'options'], function () {
                        options = $.extend(true, {}, scope.options);
                        options.file = scope.data;

                        if (graph === undefined || currWidth != $(parent).width()) {
                            currWidth = $(parent).width();
                            resize();
                        }

                        if (graph !== undefined && options.file !== undefined && options.file.length > 0) {
                            graph.updateOptions(options);
                        }
                    }, true);

                    scope.$watch("legend", function () {
                        if (graph === undefined) return;

                        // Clear the legend
                        var colors = graph.getColors();
                        var labels = graph.getLabels();

                        scope.legendSeries = {};

                        if (scope.legend !== undefined && scope.legend.dateFormat === undefined) {
                            scope.legend.dateFormat = 'MMMM Do YYYY, h:mm:ss a';
                        }

                        // If we want our own legend, then create it
                        if (scope.legend !== undefined && scope.legend.series !== undefined) {
                            var cnt = 0;
                            for (var key in scope.legend.series) {
                                scope.legendSeries[key] = {};
                                scope.legendSeries[key].color = colors[cnt];
                                scope.legendSeries[key].label = scope.legend.series[key].label;
                                scope.legendSeries[key].format = scope.legend.series[key].format;
                                scope.legendSeries[key].visible = true;
                                scope.legendSeries[key].column = cnt;

                                cnt++;
                            }
                        }

                        resize();
                    });

                    scope.highlightCallback = function (event, x, points, row) {
                        if (!myOptions.showPopover) {
                            return;
                        }

                        //console.log(event, x, points, row);
                        var html = "<table><tr><th colspan='2'>";
                        if (typeof moment === "function") {
                            html += moment(x).format(scope.legend.dateFormat);
                        }
                        else {
                            html += x;
                        }
                        html += "</th></tr>";

                        angular.forEach(points, function (point) {
                            var color;
                            var label;
                            var value;
                            if (scope.legendSeries[point.name] !== undefined) {
                                label = scope.legendSeries[point.name].label;
                                color = "style='color:" + scope.legendSeries[point.name].color + ";'";
                                if (scope.legendSeries[point.name].format) {
                                    value = point.yval.toFixed(scope.legendSeries[point.name].format);
                                }
                                else {
                                    value = point.yval;
                                }
                            }
                            else {
                                label = point.name;
                                color = "";
                            }
                            html += "<tr " + color + "><td>" + label + "</td>" + "<td>" + value + "</td></tr>";
                        });
                        html += "</table>";
                        popover.html(html);
                        popover.show();
                        var table = popover.find('table');
                        popoverWidth = table.outerWidth(true);
                        popoverHeight = table.outerHeight(true);

                        // Provide some hysterises to the popup position to stop it flicking back and forward
                        if (points[0].x < 0.4) {
                            popoverPos = false;
                        }
                        else if (points[0].x > 0.6) {
                            popoverPos = true;
                        }
                        var x;
                        if (popoverPos == true) {
                            x = event.pageX - popoverWidth - 20;
                        }
                        else {
                            x = event.pageX + 20;
                        }
                        popover.width(popoverWidth);
                        popover.height(popoverHeight);
                        popover.animate({left: x + 'px', top: (event.pageY - (popoverHeight / 2)) + 'px'}, 20);

                        //console.log("Moving", {left: x + 'px', top: (event.pageY - (popoverHeight / 2)) + 'px'})
                    };

                    scope.unhighlightCallback = function (event, a, b) {
                        if (!myOptions.showPopover) {
                            return;
                        }

                        // Check if the cursor is still within the chart area
                        // If so, ignore this event.
                        // This stops flickering if we get an even when the mouse covers the popover
                        if (event.pageX > chartArea.left && event.pageX < chartArea.right && event.pageY > chartArea.top && event.pageY < chartArea.bottom) {
                            var x;
                            if (popoverPos == true) {
                                x = event.pageX - popoverWidth - 20;
                            }
                            else {
                                x = event.pageX + 20;
                            }
                            popover.animate({left: x + 'px'}, 10);
                            return;
                        }
                        //console.log(event, a, b);
                        popover.hide();
                    };

                    scope.seriesLine = function (series) {
                        return $sce.trustAsHtml('<svg height="14" width="20"><line x1="0" x2="16" y1="8" y2="8" stroke="' +
                        series.color + '" stroke-width="3" /></svg>');
                    };

                    scope.seriesStyle = function (series) {
                        if (series.visible) {
                            return {color: series.color};
                        }
                        return {};
                    };

                    scope.selectSeries = function (series) {
                        //console.log("Change series", series);
                        series.visible = !series.visible;
                        graph.setVisibility(series.column, series.visible);
                    };

                    resize();

                    var w = angular.element($window);
                    w.bind('resize', function () {
                        resize();
                    });

                    function resize() {
                        if (options.file === undefined || options.file.length <= 0) return;
                        if (graph === undefined) {
                            createGraph();
                        }
                        var maxWidth = 0;
                        $(element.find('div.series')).each(function () {
                            var itemWidth = $(this).width();
                            maxWidth = Math.max(maxWidth, itemWidth)
                        });
                        $(element.find('div.series')).each(function () {
                            $(this).width(maxWidth);
                        });

                        var width = $(parent).width() - 20;
                        var height = width / (2 * 1.618);

                        var legendHeight = $(element.find('div.legend')).outerHeight(true);
                        graph.resize(width, height);
                        chartArea = $(chartDiv).offset();
                        chartArea.bottom = chartArea.top + height;
                        chartArea.right = chartArea.left + width;
                    }

                    function createGraph() {
                        graph = new Dygraph(chartDiv, scope.data, options);

                        graph.updateOptions({
                            drawCallback: drawCallback,
                            highlightCallback: highlightCallback,
                            unhighlightCallback: unhighlightCallback
                        });

                        var id = gid;
                        allGraphs[id] = graph;
                        gid += 1;

                        scope.$on('$destroy', function () {
                            delete allGraphs[id];
                        });
                    }
                }
            };
        }]);
})();
