'use strict';

khronosApp.controller('MetricWidgetCtrl', ['$q', '$scope', 'WebSocket', function ($q, $scope, WebSocket) {
    function initConfig(name, def) {
        if ($scope.widget.config[name] === undefined) {
            $scope.widget.config[name] = def
        }
    }

    initConfig('tags', []);
    initConfig('aggMethod', 'avg');

    $scope.widget.lastTm = 0;
    $scope.widget.data = [];
    $scope.widget.gidToIdx = {};

    function resetWidgetTransients() {
        while ($scope.widget.data.length > 0) {
            $scope.widget.data.pop();
        }
        $scope.widget.options = {labels: ['tm']};
        $scope.widget.notifications = [];
    }

    resetWidgetTransients();

    $scope.clearNotification = function (idx) {
        $scope.widget.notifications.splice(idx, 1);
    };

    $scope.tags = $scope.widget.config.tags;
    $scope.aggregationMethods = ['avg', 'sum', 'max', 'min'];

    $scope.fetchSuggestions = function (viewValue) {
        var tags = $scope.tags.map(function (t) {
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

    var cancelSubscription = function () {
    };

    var updateSubscription = function () {
        resetWidgetTransients();
        cancelSubscription();

        if ($scope.tags.length > 0) {
            // TODO: dropdown aggregation mode (also auto detect agg mode)
            var tags = $scope.tags.map(function (t) {
                return t.tag;
            });
            var params = {type: "metric-subscribe", tags: tags, agg: $scope.widget.config.aggMethod};
            cancelSubscription = WebSocket.sendRecurringRequest($scope, params, function (r) {
                handleMR(r);
            });
        }
    };
    $scope.$watchCollection('tags', updateSubscription);
    $scope.$watch('widget.config.aggMethod', updateSubscription);

    $scope.$watch('widgetSize()', function() { setTimeout(function() {
        $scope.$apply(function() { $scope.widget.lastTm += 1; });
    }, 10); });

    function handleMR(r) {
        switch (r.type) {
            case "header":
                console.log(r);
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
        $scope.widget.notifications.push(r);
    }

    function handleHeaderMR(r) {
        while ($scope.widget.options.labels.length < r.id) {
            $scope.widget.options.labels.push($scope.widget.options.labels.length.toString);
        }
        $scope.widget.options.labels[r.id] = r.label;
    }

    function handleValueMR(r) {
       var numPoints = r.data.length;
        for (var i = 0; i < numPoints; ++i) {
            if (r.data[i].length > $scope.widget.options.labels.length + 1) {
                return handleNotificationMR({
                    type: "error",
                    what: "have not received a header for timeseries #" + r.data[i].length
                });
            }
            if (r.data[i] === null) {
                handleNotificationMR({
                    type: "error",
                    what: "happened on line #" + i
                });
            }
            r.data[i][0] = new Date(r.data[i][0] * 1000);
            $scope.widget.data.push(r.data[i]);
        }
        $scope.widget.lastTm += 1;
    }

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