'use strict';

khronosApp.controller('MetricWidgetCfgCtrl', ['$q', '$scope', 'WebSocket', function ($q, $scope, WebSocket) {
    function initConfig(name, def) {
        if ($scope.widget.config[name] === undefined) {
            $scope.widget.config[name] = def;
        }
    }

    initConfig('version', 1);
    initConfig('tags', []);
    initConfig('aggMethod', 'sum');

    $scope.aggregationMethods = ['avg', 'sum', 'max', 'min'];

    $scope.fetchSuggestions = function (viewValue) {
        var tags = $scope.widget.config.tags.map(function (t) {
            return t.tag;
        });
        var params = {type: "metric-typeahead", tags: tags, tagQuery: viewValue};
        var defer = $q.defer();
        WebSocket.sendRequest($scope, params, function (r) {
            var rlen = r.length;
            for (var i = 0; i < rlen; ++i) {
                r[i].suggestion = $scope.formatSuggestion(r[i], viewValue);
            }
            return defer.resolve(r);
        });
        return defer.promise;
    };

    $scope.formatSuggestion = function (suggestion, query) {
        var matches = (suggestion.numMatches == 1) ? "match" : "matches";

        var text = replaceAll(encodeHTML(suggestion.tag), encodeHTML(query), '<em>$&</em>');

        return "<span><span class=\"typeahead-result\">" +
            text +
            "</span><span class=\"pull-right matches\">" +
            "(" + suggestion.numMatches + " " + matches + " )" +
            "</span></span>";
    };

    function replaceAll(str, substr, newSubstr) {
        if (!substr) {
            return str;
        }

        var expression = substr.replace(/([.?*+^$[\]\\(){}|-])/g, '\\$1');
        return str.replace(new RegExp(expression, 'gi'), newSubstr);
    }

    function encodeHTML(value) {
        return value.replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }
}]);

khronosApp.controller('MetricWidgetCtrl', ['$q', '$scope', 'WebSocket', function ($q, $scope, WebSocket) {
    function emptyFunc() {}

    function initConfig(name, def) {
        if ($scope.widget.config[name] === undefined) {
            $scope.widget.config[name] = def;
        }
    }

    initConfig('version', 1);
    initConfig('tags', []);
    initConfig('aggMethod', 'sum');

    $scope.data = [];

    function resetWidgetTransients() {
        while ($scope.data.length > 0) {
            $scope.data.pop();
        }
        $scope.options = {
            labels: ['tm'],
            labelsKMB: true,
            highlightCircleSize: 2,
            highlightSeriesOpts: {
                highlightCircleSize: 3,
                strokeWidth: 2
            }
        };
        $scope.notifications = [];
        $scope.lastTm = 0;
    }

    resetWidgetTransients();

    $scope.clearNotification = function (idx) {
        $scope.notifications.splice(idx, 1);
    };

    var cancelSubscription = emptyFunc;

    var updateSubscription = function (newValue, oldValue) {
        if (newValue !== undefined && oldValue !== undefined && newValue === oldValue) {
            return;
        }

        resetWidgetTransients();
        cancelSubscription();
        cancelSubscription = emptyFunc;

        if ($scope.widget.config.tags.length > 0) {
            $scope.loading = true;
            var startTm = $scope.startTm.getTime() / 1000; // Seconds since Epoch.
            var range = $scope.storage.timeRange.dt * 60; // Number of Seconds to watch.
            var endTm = startTm + range;

            $scope.options.dateWindow = [startTm * 1000, endTm * 1000];

            // TODO: auto detect agg mode
            var tags = $scope.widget.config.tags.map(function (t) {
                return t.tag;
            });
            // TODO: Live vs. Historical w.r.t. timeRange
            var params = {
                type: "metric-subscribe",
                tags: tags,
                agg: $scope.widget.config.aggMethod,
                startTm: startTm,
                timeRange: range
            };
            cancelSubscription = WebSocket.sendRecurringRequest($scope, params, function (r) {
                handleMR(r);
            });
        }
    };
    $scope.$watchCollection('widget.config.tags', updateSubscription);
    $scope.$watch('widget.config.aggMethod', updateSubscription);
    $scope.$watch('startTm', updateSubscription);
    $scope.$watch('storage.timeRange', updateSubscription);

    updateSubscription();

    var onWidgetSizeChangeTO = null;
    var onWidgetSizeChange = function () {
        if (onWidgetSizeChangeTO != null) {
            clearTimeout(onWidgetSizeChangeTO);
        }
        onWidgetSizeChangeTO = setTimeout(function () {
            $scope.$apply(function () {
                if ($scope.lastTm > 0) {
                    $scope.lastTm += 1;
                }
            });
        }, 100);
    };
    $scope.$watch('widgetSize()', onWidgetSizeChange);

    function handleMR(r) {
        switch (r.type) {
            case "header":
                handleHeaderMR(r);
                break;
            case "value":
                handleValueMR(r);
                break;
            case "error":
            case "warn":
                handleNotificationMR(r);
                break;
            default:
                console.log(r);
                handleNotificationMR({type: "error", what: "could not find metrics response for type: " + r.type});
        }
    }

    function handleNotificationMR(r) {
        $scope.notifications.push(r);
    }

    function handleHeaderMR(r) {
        while ($scope.options.labels.length < r.id) {
            $scope.options.labels.push($scope.options.labels.length.toString);
        }
        $scope.options.labels[r.id] = r.label;

    }

    function handleValueMR(r) {
        $scope.loading = false;
        var numPoints = r.data.length;
        for (var i = 0; i < numPoints; ++i) {
            if (r.data[i].length > $scope.options.labels.length + 1) {
                return handleNotificationMR({
                    type: "error",
                    what: "have not received a header for timeseries #" + r.data[i].length
                });
            }
            r.data[i][0] = new Date(r.data[i][0] * 1000);
            $scope.data.push(r.data[i]);
        }
        $scope.lastTm += 1;
    }
}]);