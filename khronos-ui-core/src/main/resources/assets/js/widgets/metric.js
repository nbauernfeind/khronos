'use strict';

khronosApp.controller('MetricWidgetCtrl', ['$q', '$scope', 'WebSocket', function($q, $scope, WebSocket) {
    function initConfig(name, def) {
        if ($scope.widget.config[name] === undefined) {
            $scope.widget.config[name] = def
        }
    }
    initConfig('tags', []);
    initConfig('options', {renderer: "line", width: "100"});
    initConfig('features', {
        palette: 'spectrum14',
        hover: {
            xFormatter: function (x) {
                return 't=' + x;
            },
            yFormatter: function (y) {
                return '$' + y;
            }
        },
        xAxis: true
    });

    $scope.widget.lastTm = 0;
    $scope.widget.data = [];
    $scope.widget.gidToIdx = {};

    console.log($scope.widget.config.options);
    console.log($scope.widget.config.features);

    $scope.widget.notifications = [];
    $scope.clearNotification = function(idx) {
        $scope.widget.notifications.splice(idx, 1);
    };

    $scope.tags = $scope.widget.config.tags;
    $scope.renderers = ['area', 'bar', 'line', 'scatterplot'];

    $scope.fetchSuggestions = function(viewValue) {
        var tags = $scope.tags.map(function(t) { return t.tag; });
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

    $scope.formatSuggestion = function(suggestion, query) {
        var matches = (suggestion.numMatches == 1) ? "match" : "matches";

        var text = replaceAll(encodeHTML(suggestion.tag), encodeHTML(query), '<em>$&</em>');

        return "<span><span class=\"typeahead-result\">" +
            text +
            "</span><span class=\"pull-right matches\">" +
            "(" + suggestion.numMatches + " " + matches + " )" +
            "</span></span>";
    };

    var cancelSubscription = function() {};

    $scope.$watchCollection('tags', function() {
        if ($scope.tags.length > 0) {
            while ($scope.widget.data.length > 0) {
                $scope.widget.data.pop();
            }
            $scope.widget.notifications = [];
            cancelSubscription();

            // TODO: dropdown aggregation mode (also auto detect agg mode)
            var tags = $scope.tags.map(function(t) { return t.tag; });
            var params = {type: "metric-subscribe", tags: tags, agg: "AVG"};
            cancelSubscription = WebSocket.sendRecurringRequest($scope, params, function(r) {
                handleMR(r);
                //graph.update();
            });
        }
    });

    function handleMR(r) {
        switch(r.type) {
            case "header":
                console.log(r);
                handleHeaderMR(r);
                console.log($scope.widget.data);
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
        var ts = {
            name: "Aggregate",
            data: []
        };
        $scope.widget.gidToIdx[r.id] = ts;
        $scope.widget.data.push(ts);
        //graph = resetGraph();
    }

    function handleValueMR(r) {
        if ($scope.widget.gidToIdx[r.id] === undefined) {
            return handleNotificationMR({type: "error", what: "have not received a header for gid: " + r.id});
        }
        var ts = $scope.widget.gidToIdx[r.id];
        var numPoints = r.points.length;
        for (var i = 0; i < numPoints; ++i) {
            ts.data.push({x: r.points[i].tm, y: r.points[i].value});
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