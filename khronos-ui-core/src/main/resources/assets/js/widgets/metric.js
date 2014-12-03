'use strict';

khronosApp.controller('MetricWidgetCtrl', ['$scope', 'WebSocket', function($scope, WebSocket) {
    $scope.tags = [];

    $scope.fetchSuggestions = function(viewValue) {
        var tags = $scope.tags.map(function(t) { return t.tag; });
        var params = {type: "metric-typeahead", tags: tags, tagQuery: viewValue};
        return WebSocket.sendRequest($scope, params).then(function (r) {
            var rlen = r.length;
            for (var i = 0; i < rlen; ++i) {
                r[i].suggestion = $scope.formatSuggestion(r[i], viewValue);
            }
            return r;
        });
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