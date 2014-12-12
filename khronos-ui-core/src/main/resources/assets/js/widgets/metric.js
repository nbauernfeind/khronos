'use strict';

khronosApp.controller('MetricWidgetCtrl', ['$q', '$scope', 'WebSocket', function($q, $scope, WebSocket) {
    if ($scope.widget.config.kvps === undefined) {
        $scope.widget.config.kvps = [];
    }
    $scope.kvps = $scope.widget.config.kvps;

    $scope.fetchSuggestions = function(viewValue) {
        var tags = $scope.kvps.map(function(t) { return t.tag; });
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

    $scope.$watchCollection('kvps', function() {
        if ($scope.kvps.length > 0) {
            var tags = $scope.kvps.map(function(t) { return t.tag; });
            var params = {type: "metric-subscribe", tags: tags, agg: "AVG"};
            cancelSubscription();
            cancelSubscription = WebSocket.sendRecurringRequest($scope, params, function(r) {
                console.log(r);
            });
        }
    });

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