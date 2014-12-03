'use strict';

var khronosApp = angular.module('khronos', ['ngRoute', 'ngResource', 'ngSanitize', 'mgcrea.ngStrap', 'ngTagsInput']);

khronosApp.config(['$routeProvider',
    function ($routeProvider) {
        $routeProvider.
            when('/', {
                templateUrl: 'partials/home.html'
            }).
            otherwise({
                redirectTo:'/'
            });
    }
]);

khronosApp.filter('asHumanFriendlyNumber', function() {
    return function(num) {
        if (num < 1000) return num;
        var r = Math.ceil(Math.log(num) / Math.LN10) % 3;
        return numeral(num).format(r == 0 ? "0a" : r == 1 ? "0.00a" : "0.0a");
    };
});

khronosApp.factory('Widgets', ['$resource',
    function($resource) {
        return $resource('/1/ui/widgets/:locationId', {}, {
            query: {method: 'GET', params: {locationId: 'location'}, isArray: true}
        });
    }
]);

khronosApp.controller('KhronosCtrl', ['$scope', 'Widgets', function ($scope, Widgets) {
    $scope.widgets = Widgets.query({locationId: 'home'});
}]);
