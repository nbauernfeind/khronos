'use strict';

var khronosApp = angular.module('khronos', ['ngRoute', 'ngResource']);

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

khronosApp.factory('DataStream', ['$q', '$rootScope', function($q, $rootScope) {
    var Service = {
        isConnected: false
    };

    var socket;
    var callbacks = {};

    var currentCallbackId = 0;
    function getCallbackId() {
        currentCallbackId += 1;
        if(currentCallbackId > 65536) {
            currentCallbackId = 0;
        }
        return currentCallbackId;
    }

    var request = {
        url: '/ws/',
        contentType: 'application/json',
        logLevl: 'debug',
        transport: 'websocket',
        trackMessageLength: true,
        reconnectInterval: 5000,
        maxStreamingLength: 1000
    };

    var resetSocket = function () {
        socket = jQuery.atmosphere.subscribe(request);
    };

    request.onOpen = function(response) {
        console.log('WebSocket: Connected to ' + request.url + '. (' + response.transport + ')');
        Service.isConnected = true;
        request.uuid = response.request.uuid;

        for (var callback in callbacks) {
            if (callbacks.hasOwnProperty(callback)) {
                doSendRequest(callbacks[callback]);
            }
        }
    };

    request.onClientTimeout = function(response) {
        console.log('WebSocket: Client closed the connection after a timeout.');
        Service.isConnected = false;
        setTimeout(resetSocket);
    };

    request.onReopen = function(response) {
        console.log('WebSocket: Reconnected to ' + request.url + '. (' + response.transport + ')');
        Service.isConnected = true;

        for (var callback in callbacks) {
            if (callbacks.hasOwnProperty(callback)) {
                doSendRequest(callbacks[callback]);
            }
        }
    };

    request.onTransportFailure = function(errorMsg, request) {
        jQuery.atmosphere.util.info(errorMsg);
        request.fallbackTransport = 'long-polling';
        console.log('WebSocket: Falling back to long-polling.');
    };

    request.onClose = function(response) {
        console.log('WebSocket: Server closed the connection.');
        Service.isConnected = false;
    };

    request.onError = function(response) {
        console.log('WebSocket: Unknown error occurred.');
        console.log(response);
        Service.isConnected = false;
    };

    request.onReconnect = function(request, response) {
        console.log('WebSocket: Connection lost, trying to reconnect.');
        Service.isConnected = false;
    };

    request.onMessage = function(message) {
        console.log("WebSocket: Msg Received");
        var obj = JSON.parse(message.responseBody);
        console.log(obj);
        if (callbacks.hasOwnProperty(obj.callbackId)) {
            var callback = callbacks[obj.callbackId];
            callback.scope.$apply(callback.cb(obj.data));

            var recurring = callback.request.recurring;
            if (recurring === undefined || recurring === false) {
                delete callbacks[obj.callbackId];
                console.log('WebSocket: Fulfilled request.', request);
            }
        }
    };

    function doSendRequest(callback) {
        var request = callback.request;
        console.log('WebSocket: Subscribing request.', request);
        socket.push(JSON.stringify(request));

        if (request.recurring === true) {
            callback.scope.$on("$destroy", function () {
                delete callbacks[request.callbackId];
                var cancel = {
                    type: 'cancel',
                    callbackId: request.callbackId
                };
                console.log('WebSocket: Cancelling request.', request);
                socket.push(JSON.stringify(cancel));
            });
        }
    }

    function sendRequest($scope, request, callback) {
        request.callbackId = getCallbackId();
        callbacks[request.callbackId] = {
            scope: $scope,
            time: new Date(),
            request: request,
            cb: callback
        };

        if (Service.isConnected) {
            doSendRequest(callbacks[request.callbackId]);
        }
    }

    Service.subscribeMetric = function($scope, query, callback) {
        var request = {
            type: 'metric',
            recurring: true,
            query: query
        };

        return sendRequest($scope, request, callback);
    };

    // Start Service!
    resetSocket();

    return Service;
}]);