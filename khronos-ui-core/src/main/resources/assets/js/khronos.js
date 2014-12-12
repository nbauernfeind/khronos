'use strict';

var khronosApp = angular.module('khronos', ['ngRoute', 'ngResource', 'ngSanitize', 'mgcrea.ngStrap', 'ngTagsInput',
    'angular-rickshaw']);

khronosApp.config(['$routeProvider',
    function ($routeProvider) {
        $routeProvider.
            when('/Explore', {
                templateUrl: 'partials/explore.html',
                controller: 'ExploreTabCtrl'
            }).
            when('/Status', {
                templateUrl: 'partials/status.html',
                controller: 'StatusTabCtrl'
            }).
            otherwise({
                redirectTo: '/Explore'
            });
    }
]);

khronosApp.filter('asHumanFriendlyNumber', function () {
    return function (num) {
        if (num < 1000) return num;
        var r = Math.ceil(Math.log(num) / Math.LN10) % 3;
        return numeral(num).format(r == 0 ? "0a" : r == 1 ? "0.00a" : "0.0a");
    };
});

khronosApp.factory('Widgets', ['$resource',
    function ($resource) {
        return $resource('/1/ui/widgets/:path', {}, {
            all: {method: 'GET', params: {path: 'all'}, isArray: true}
        });
    }
]);

khronosApp.controller('KhronosCtrl', ['$scope', 'Widgets', function ($scope, Widgets) {
    $scope.tabs = ["Explore", "Status"];
    $scope.global = {};

    $scope.allWidgets = Widgets.all({});

    $scope.deepCopy = function (dupe) {
        return $.extend(true, {}, dupe);
    }
}]);

khronosApp.controller('StatusTabCtrl', ['$scope', 'Widgets', function ($scope, Widgets) {
    $scope.global.currTab = "Status";
}]);

khronosApp.controller('ExploreTabCtrl', ['$scope', 'Widgets', function ($scope, Widgets) {
    $scope.global.currTab = "Explore";
    $scope.widgets = [];

    $scope.addWidget = function(widget) {
        $scope.widgets.push(newWidget(widget));
    };

    $scope.configure = function(widget) {
        widget.editable = !widget.editable;
    };

    $scope.copyWidget = function(widget) {
        // Todo: Deep copy config from widget.
        $scope.widgets.push({
            name: widget.name,
            partial: widget.partial,
            title: widget.title,
            editable: false,
            config: $scope.deepCopy(widget.config)
        });
    };

    $scope.removeWidget = function(idx) {
        $scope.widgets.splice(idx, 1);
    };

    function newWidget(widget) {
        // Todo: Clone config defaults in widget.
        return {
            name: widget.name,
            partial: widget.partial,
            title: "New " + widget.name,
            editable: true,
            config: $scope.deepCopy(widget.config || {})
        };
    }

    // To more quickly test. (Maybe I should save state in a cookie instead?)
    $scope.addWidget({name: "Metric", partial: "partials/widgets/metric.html"});
}]);
