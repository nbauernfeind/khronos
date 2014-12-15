'use strict';

angular.module('angular-rickshaw', [])
    .directive('rickshaw', function ($compile) {
        return {
            restrict: 'E',
            scope: {
                options: '=',
                series: '=',
                features: '=',
                lastTm: '='
            },
            link: function (scope, element, attrs) {
                var graph;
                var graphEl = function() {
                    var main = angular.element(element);
                    main.append($compile('<div></div>')(scope));
                    return main[0];
                }();

                function update() {
                    var palette = new Rickshaw.Color.Palette({scheme: "spectrum14"});
                    if (scope.features && scope.features.palette) {
                        palette = new Rickshaw.Color.Palette({scheme: scope.features.palette});
                    }

                    angular.element(graphEl).empty();
                    var settings = $.extend({}, scope.options);
                    settings.element = graphEl;
                    settings.series = scope.series;
                    graph = new Rickshaw.Graph(settings);

                    if (scope.features && scope.features.hover) {
                        var hoverConfig = {
                            graph: graph
                        };
                        hoverConfig.xFormatter = scope.features.hover.xFormatter;
                        hoverConfig.yFormatter = scope.features.hover.yFormatter;
                        hoverConfig.formatter = scope.features.hover.formatter;
                        new Rickshaw.Graph.HoverDetail(hoverConfig);
                    }

                    for (var i = 0; i < settings.series.length; i++) {
                        settings.series[i].color = palette.color();
                    }

                    graph.render();

                    if (scope.features && scope.features.xAxis) {
                        var xAxisConfig = {
                            graph: graph,
                            timeFixture: new Rickshaw.Fixtures.Time.Local(),
                            ticksTreatment: 'glow'
                        };
                        if (scope.features.xAxis.timeUnit) {
                            var time = new Rickshaw.Fixtures.Time();
                            xAxisConfig.timeUnit = time.unit(scope.features.xAxis.timeUnit);
                        }
                        var xAxis = new Rickshaw.Graph.Axis.Time(xAxisConfig);
                        xAxis.render();
                    }

                    if (scope.features && scope.features.yAxis) {
                        var yAxisConfig = {
                            graph: graph
                        };
                        if (scope.features.yAxis.tickFormat) {
                            yAxisConfig.tickFormat = Rickshaw.Fixtures.Number[scope.features.yAxis.tickFormat];
                        }

                        var yAxis = new Rickshaw.Graph.Axis.Y(yAxisConfig);
                        yAxis.render();
                    }

                    if (scope.features && scope.features.legend) {
                        // TODO this is definitely broken, no mainEl.
                        var legendEl = $compile('<div></div>')(scope);
                        mainEl.append(legendEl);

                        var legend = new Rickshaw.Graph.Legend({
                            graph: graph,
                            element: legendEl[0]
                        });
                        if (scope.features.legend.toggle) {
                            var shelving = new Rickshaw.Graph.Behavior.Series.Toggle({
                                graph: graph,
                                legend: legend
                            });
                        }
                        if (scope.features.legend.highlight) {
                            var highlighter = new Rickshaw.Graph.Behavior.Series.Highlight({
                                graph: graph,
                                legend: legend
                            });
                        }
                    }
                }

                var optionsWatch = scope.$watch('options', update, true);

                var newDataWatch = scope.$watch('lastTm', function() {
                    graph.update();
                });

                var seriesWatch = scope.$watchCollection('series', function () {
                    update();
                });

                var featuresWatch = scope.$watch('features', update, true);

                scope.$on('$destroy', function () {
                    optionsWatch();
                    seriesWatch();
                    newDataWatch();
                    featuresWatch();
                });

                update();
            },
            controller: function ($scope, $element, $attrs) {
            }
        };
    });