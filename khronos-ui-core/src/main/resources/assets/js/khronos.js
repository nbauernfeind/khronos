'use strict';

var khronosApp = angular.module('khronos', ['ngRoute', 'ngResource', 'ngSanitize', 'mgcrea.ngStrap', 'ngTagsInput',
    'angular-dygraphs', 'angular-displaymode', 'ngStorage', 'ui.bootstrap.datetimepicker']);

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

khronosApp.controller('ExploreTabCtrl', ['$scope', '$localStorage', function ($scope, $localStorage) {
    $scope.global.currTab = "Explore";

    $scope.sizes = ["S", "M", "L", "XL"];
    $scope.ranges = [{label: "1 hour", dt: 60}, {label: "3 hours", dt: 3 * 60}, {label: "6 hours", dt: 6 * 60},
        {label: "8 hours", dt: 8 * 60},
        {label: "12 hours", dt: 12 * 60}, {label: "1 day", dt: 24 * 60}, {label: "3 days", dt: 3 * 24 * 60},
        {label: "7 days", dt: 7 * 24 * 60}, {label: "1 month", dt: 30 * 24 * 60}, {label: "3 months", dt: 90 * 24 * 60},
        {label: "1 year", dt: 365 * 24 * 60}];

    $scope.storage = $localStorage.$default({
        widgets: [],
        currSize: "M",
        timeRange: $scope.ranges[4],
        myOffset: 0
    });
    $scope.widgets = $scope.storage.widgets;

    $scope.startTm = new Date();
    $scope.startTm.setHours(0, 0, 0, 0);
    $scope.startTm = new Date($scope.startTm.getTime() + $scope.storage.myOffset);

    $scope.setSize = function (sz) {
        $scope.storage.currSize = sz;
    };

    $scope.setTimeRange = function (tr) {
        $scope.storage.timeRange = tr;
    };

    $scope.advanceTime = function (dtMin) {
        $scope.startTm = new Date($scope.startTm.getTime() + dtMin * 60 * 1000); // 60s in ms
    };

    $scope.$watch('startTm', function() {
        var tmp = new Date($scope.startTm.getTime());
        tmp.setHours(0, 0, 0, 0);
        $scope.storage.myOffset = $scope.startTm.getTime() - tmp.getTime();
    });

    $scope.addWidget = function (widget) {
        $scope.widgets.push(newWidget(widget));
    };

    $scope.configure = function (widget) {
        widget.editable = !widget.editable;
    };

    $scope.copyWidget = function (widget) {
        // Todo: Deep copy config from widget.
        $scope.widgets.push({
            name: widget.name,
            partial: widget.partial,
            title: widget.title,
            editable: false,
            config: $scope.deepCopy(widget.config)
        });
    };

    $scope.removeWidget = function (idx) {
        $scope.widgets.splice(idx, 1);
    };

    $scope.widgetSize = function () {
        switch ($scope.numColumns()) {
            case 6:
                return "col-xs-2 col-sm-2 col-md-2 col-lg-2";
            case 4:
                return "col-xs-3 col-sm-3 col-md-3 col-lg-3";
            case 3:
                return "col-xs-4 col-sm-4 col-md-4 col-lg-4";
            case 2:
                return "col-xs-6 col-sm-6 col-md-6 col-lg-6";
            default:
                return "col-xs-12 col-sm-12 col-md-12 col-lg-12";
        }
    };

    $scope.numColumns = function () {
        var szIdx = $scope.sizes.indexOf($scope.storage.currSize);
        var numElements = {
            desktop: [6, 3, 2, 1],
            'tablet-landscape': [4, 2, 1, 1],
            tablet: [2, 2, 1, 1],
            mobile: [1, 1, 1, 1]
        };
        return numElements[$scope.global.displayMode][szIdx];
    };

    $scope.idxCompletesRow = function (idx) {
        return (idx + 1) % $scope.numColumns() == 0;
    };

    function newWidget(widget) {
        // Todo: Clone config defaults in widget.
        return {
            name: widget.name,
            partial: widget.partial,
            title: "",
            editable: true,
            config: $scope.deepCopy(widget.config || {})
        };
    }
}]);
