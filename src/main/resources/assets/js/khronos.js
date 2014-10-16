'use strict';

var khronosApp = angular.module('khronos', ['ngRoute']);

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

khronosApp.controller('KhronosCtrl', function ($scope, $location) {
});